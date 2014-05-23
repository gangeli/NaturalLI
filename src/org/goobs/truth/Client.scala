package org.goobs.truth

import scala.collection.JavaConversions._
import scala.concurrent._

import org.goobs.truth.Messages._
import org.goobs.truth.scripts.ShutdownServer
import Monotonicity._


import java.io.{DataOutputStream, DataInputStream}
import java.net.Socket
import java.util.concurrent.{ThreadFactory, Executors}

import edu.stanford.nlp.util.logging.Redwood.Util._
import edu.stanford.nlp.util.Execution
import org.goobs.truth.DataSource.DataStream

/**
 * @author Gabor Angeli
 */
trait Client extends Evaluator {

  import Implicits.inflateWeights

  /** Offer a prediction for a given query, parameterized by the given weights. */
  override def guess(query:Query.Builder, weights:Array[Double]):Future[Iterable[Inference]] = {
    asyncQuery(Messages.Query.newBuilder(query.build())
      .setTimeout(Props.SEARCH_TIMEOUT)
      .setCosts(Learn.weightsToCosts(weights))
      .setSearchType("ucs")
      .setCacheType("bloom").build(), quiet = true)
  }

  def explain(fact:Fact, tag:String="fact", verbose:Boolean=true):Unit = {
    log(tag + ": " + fact.getWordList.map( w =>
      s"[${w.getMonotonicity match { case UP => "^" case DOWN => "v" case FLAT => "-"}}]${w.getGloss}_${w.getSense}"
    ).mkString(" "))
    if (verbose) {
      log(fact.getWordList.map{ w =>
        val synsets = NatLog.wordnet.getSynsets(w.getGloss)
        if (synsets == null || synsets.size == 0 || w.getSense == 0) {
          s"  ${w.getGloss} (${w.getWord}_${w.getSense}}): <unknown sense>"
        } else {
          s"  ${w.getGloss} (${w.getWord}_${w.getSense}}): ${Utils.senseGloss( (w.getWord, w.getSense) )}"
        }
      }.mkString("\n"))
    }
  }

  /**
   * toString() an inference path, primarily for debugging.
   * @param node The path to print.
   * @return The string format of the path.
   */
  def recursivePrint(node:Inference):String = {
    if (node.hasImpliedFrom) {
      val incomingType = EdgeType.values.find( _.id == node.getIncomingEdgeType).getOrElse(node.getIncomingEdgeType)
      if (node.hasIncomingEdgeType && incomingType != 63) {
        s"${node.getFact.getGloss} <-[$incomingType]- ${recursivePrint(node.getImpliedFrom)}"
      } else {
        recursivePrint(node.getImpliedFrom)
      }
    } else {
      s"${node.getFact.getGloss}"
    }
  }

  private val mainExecContext = new ExecutionContext {
    lazy val threadPool = Executors.newFixedThreadPool(Props.SERVER_MAIN_THREADS, new ThreadFactory {
      override def newThread(r: Runnable): Thread = { val t = new Thread(r); t.setDaemon(true); t }
    })
    def execute(runnable: Runnable) { threadPool.submit(runnable) }
    def reportFailure(t: Throwable) { t.printStackTrace() }
  }

  private val backupExecContext = new ExecutionContext {
    lazy val threadPool = Executors.newFixedThreadPool(Props.SERVER_BACKUP_THREADS, new ThreadFactory {
      override def newThread(r: Runnable): Thread = { val t = new Thread(r); t.setDaemon(true); t }
    })
    def execute(runnable: Runnable) { threadPool.submit(runnable) }
    def reportFailure(t: Throwable) { t.printStackTrace() }
  }

  /**
   * Issue a query to the search server, and block until it returns
   * @param query The query, with monotonicity markings set.
   * @param quiet Suppress logging messages
   * @return The query fact, with a truth value and confidence
   */
  def asyncQuery(query:Query, quiet:Boolean=false, singleQuery:Boolean=false):Future[Iterable[Inference]] = {
    def doQuery(query:Query, host:String, port:Int):Iterable[Inference] = {
      // Set up connection
      val sock = new Socket(host, port)
      try {
        val toServer = new DataOutputStream(sock.getOutputStream)
        val fromServer = new DataInputStream(sock.getInputStream)
        if (!quiet) log("connection established with server.")

        // Write query
        query.writeTo(toServer)
        if (!quiet) log(s"wrote query to server (${query.getSerializedSize} bytes): ${query.getQueryFact.getGloss}")
        sock.shutdownOutput()  // signal end of transmission

        // Read response
        val response:Response = Response.parseFrom(fromServer)
        if (response.getError) {
          throw new RuntimeException(s"Error on inference server: ${if (response.hasErrorMessage) response.getErrorMessage else ""}")
        }
        if (!quiet) {
          log(s"server returned ${response.getInferenceCount} paths after ${if (response.hasTotalTicks) response.getTotalTicks else "?"} ticks.")
          for (inference <- response.getInferenceList) { log(recursivePrint(inference)) }
        }
        response.getInferenceList
      } finally {
        sock.close()
      }
    }

    def tag(paths:Iterable[Inference], tag:String):Iterable[Inference] = { paths.map{ i => Inference.newBuilder(i).setTag(tag).build } }

    // Query with backoff
    // (step 0: hard weights)
    val hard: Future[Iterable[Inference]] = if (singleQuery) Future.successful(Nil) else Future {
      if (Props.SERVER_MAIN_HOST.equalsIgnoreCase("null")) Nil
      else doQuery(Query.newBuilder(query).setCosts(Learn.weightsToCosts(NatLog.hardNatlogWeights)).build(), Props.SERVER_MAIN_HOST, Props.SERVER_MAIN_PORT)
    } (mainExecContext)
    hard.flatMap{ (hard: Iterable[Inference]) =>
      if (hard.isEmpty) {
        // (step 1: main search)
        val main: Future[Iterable[Inference]] = Future {
          if (Props.SERVER_MAIN_HOST.equalsIgnoreCase("null")) Nil else doQuery(query, Props.SERVER_MAIN_HOST, Props.SERVER_MAIN_PORT)
        } (mainExecContext)
        // (step 2: map to backoff)
        if (singleQuery) main else main.flatMap( (main: Iterable[Inference]) =>
          if (main.isEmpty && (!Props.SERVER_BACKUP_HOST.equals(Props.SERVER_MAIN_HOST) || Props.SERVER_BACKUP_PORT != Props.SERVER_MAIN_PORT)) {
            val simpleQuery = Utils.simplifyQuery(query, (x:String) => NatLog.annotate(x).head)
            // (step 3: execute backoff)
            val backoff = Future {
              doQuery(simpleQuery, Props.SERVER_BACKUP_HOST, Props.SERVER_BACKUP_PORT)
            } (backupExecContext)
            // (step 4: map to backoff)
            backoff.flatMap{ (backoff: Iterable[Inference]) =>
              if (backoff.isEmpty) {
                // (step 5: execute failsafe)
                //            warn("no results for query (backing off to expensive BFS): " + simpleQuery.getQueryFact.getGloss)
                val failsafe: Future[Iterable[Inference]] = Future {
                  doQuery(Messages.Query.newBuilder(simpleQuery).setSearchType("bfs").setTimeout(Props.SEARCH_TIMEOUT*2).build(), Props.SERVER_BACKUP_HOST, Props.SERVER_BACKUP_PORT)
                } (backupExecContext)
                failsafe.map{ failsafe =>
                  if (failsafe.isEmpty) {
                    debug(YELLOW, "no results for query: " + simpleQuery.getQueryFact.getGloss)
                  }
                  // return case: had to go all the way to the failsafe
                  tag(failsafe, "backoff BFS")
                }(ExecutionContext.Implicits.global)
              } else {
                // return case: backoff found paths
                Future.successful(tag(backoff, "backoff UCS"))
              }
            }(ExecutionContext.Implicits.global)
          } else {
            // return case: main query found paths
            Future.successful(tag(main, "soft weights"))
          }
        )(ExecutionContext.Implicits.global)
      } else {
        Future.successful(tag(hard, "hard weights"))
      }
    }(ExecutionContext.Implicits.global)
  }

  def issueQuery(query:Query, quiet:Boolean=false, singleQuery:Boolean=false):Iterable[Inference] = Await.result(asyncQuery(query, quiet, singleQuery), scala.concurrent.duration.Duration.Inf)

  /**
   * Start a mock server that the client can connect to.
   * @param callback The callback to call once the server is started.
   * @param printOut If true, print the server output (otherwise, squash it).
   * @return The exit code of the server process.
   */
  def startMockServer(callback:()=>Any, printOut:Boolean = false):Int = {
    ShutdownServer.shutdown()

    // Pre-fetch the annotators while the server spins up
    val cache = new Thread() {
      override def run() { NatLog.annotate("all models like to be cached", x => Utils.WORD_UNK); }
    }
    cache.setDaemon(true)
    cache.start()

    // Make the server
    import scala.sys.process._
    log("Making server")
    val couldMake = List[String]("""make""", "-j" + Execution.threads) ! ProcessLogger { line => log(line) }
    if (couldMake != 0) { return -1 }

    // Run the server
    var running = false
    log("Starting Server")
    List[String]("catchsegv", "src/naturalli_server", "" + Props.SERVER_MAIN_PORT) ! ProcessLogger{line =>
      if (!running || printOut) { log(line) }
      if (line.startsWith("Listening on port") && !running) {
        // Server is initialized -- we can start doing client-side stuff
        running = true
        new Thread(new Runnable {
          override def run(): Unit = {
            // Finish loading the annotators
            cache.join()

            // Call the callback
            // vvv
            log("--Start Callback--")
            callback()
            log("--End Callback--")
            // ^^^

            // Shutdown the server
            log("shutting down server...")
            ShutdownServer.shutdown()
          }
        }).start()
      }
    }

  }

  /**
   * Create a dataset from a Corpus enum (as set in the properties).
   * @param corpus The specification of the corpus to load.
   * @return A DataStream created from reading that corpus.
   */
  def mkDataset(corpus:Props.Corpus):DataStream = {
    corpus match {
      case Props.Corpus.HELD_OUT => HoldOneOut.read("")
      case Props.Corpus.FRACAS => FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter(FraCaS.isSingleAntecedent)
      case Props.Corpus.FRACAS_NATLOG => FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter(FraCaS.isApplicable)
      case Props.Corpus.AVE_2006 => AVE.read(Props.DATA_AVE_PATH("2006").getPath)
      case Props.Corpus.AVE_2007 => AVE.read(Props.DATA_AVE_PATH("2007").getPath)
      case Props.Corpus.AVE_2008 => AVE.read(Props.DATA_AVE_PATH("2008").getPath)
      case Props.Corpus.MTURK_TRAIN => MTurk.read(Props.DATA_MTURK_TRAIN.getPath)
      case Props.Corpus.MTURK_TEST => MTurk.read(Props.DATA_MTURK_TEST.getPath)
      case _ => throw new IllegalArgumentException("Unknown dataset: " + corpus)
    }
  }
}
