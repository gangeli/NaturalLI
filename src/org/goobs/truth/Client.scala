package org.goobs.truth

import scala.collection.JavaConversions._
import scala.concurrent._

import org.goobs.truth.Messages._
import org.goobs.truth.scripts.ShutdownServer
import Monotonicity._

import java.io.{DataOutputStream, DataInputStream}
import java.net.Socket
import java.util.concurrent.Executors

import edu.stanford.nlp.util.logging.Redwood.Util._
import edu.stanford.nlp.util.Execution

/**
 * @author Gabor Angeli
 */
trait Client {

  def explain(fact:Fact, tag:String="fact", verbose:Boolean=true):Unit = {
    log(tag + ": " + fact.getWordList.map( w =>
      s"[${w.getMonotonicity match { case UP => "^" case DOWN => "v" case FLAT => "-"}}]${w.getGloss}:${w.getPos.toUpperCase}"
    ).mkString(" "))
    if (verbose) {
      log(fact.getWordList.map{ w =>
        val synsets = NatLog.wordnet.getSynsets(w.getGloss)
        if (synsets == null || synsets.size == 0 || w.getSense == 0) {
          s"  ${w.getGloss} (${w.getWord}_${w.getSense}}): <unknown sense>"
        } else {
          s"  ${w.getGloss} (${w.getWord}_${w.getSense}}): ${synsets(w.getSense - 1).getDefinition}"
        }
      }.mkString("\n"))
    }
  }

  private val mainExecContext = new ExecutionContext {
    lazy val threadPool = Executors.newFixedThreadPool(Props.SERVER_MAIN_THREADS)
    def execute(runnable: Runnable) { threadPool.submit(runnable) }
    def reportFailure(t: Throwable) { t.printStackTrace() }
  }

  private val backupExecContext = new ExecutionContext {
    lazy val threadPool = Executors.newFixedThreadPool(Props.SERVER_BACKUP_THREADS)
    def execute(runnable: Runnable) { threadPool.submit(runnable) }
    def reportFailure(t: Throwable) { t.printStackTrace() }
  }

  implicit val serverBoundContext = new ExecutionContext {
    lazy val threadPool = Executors.newFixedThreadPool(math.max(Props.SERVER_BACKUP_THREADS, Props.SERVER_MAIN_THREADS))
    def execute(runnable: Runnable) { threadPool.submit(runnable) }
    def reportFailure(t: Throwable) { t.printStackTrace() }
  }

  /**
   * Issue a query to the search server, and block until it returns
   * @param query The query, with monotonicity markings set.
   * @param quiet Suppress logging messages
   * @return The query fact, with a truth value and confidence
   */
  def asyncQuery(query:Query, quiet:Boolean=false):Future[Iterable[Inference]] = {
    def doQuery(query:Query, host:String, port:Int):Iterable[Inference] = {
      // Set up connection
      val sock = new Socket(host, port)
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
      if (!quiet) log(s"server returned ${response.getInferenceCount} paths after ${if (response.hasTotalTicks) response.getTotalTicks else "?"} ticks.")
      response.getInferenceList
    }

    // Query with backoff
    // (step 1: main search)
    val main: Future[Iterable[Inference]] = Future {
      if (Props.SERVER_MAIN_HOST.equalsIgnoreCase("null")) Nil else doQuery(query, Props.SERVER_MAIN_HOST, Props.SERVER_MAIN_PORT)
    } (mainExecContext)
    // (step 2: map to backoff)
    main.flatMap( (main: Iterable[Inference]) =>
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
              failsafe
            }
          } else {
            // return case: backoff found paths
            Future.successful(backoff)
          }
        }
      } else {
        // return case: main query found paths
        Future.successful(main)
      }
    )
  }

  def issueQuery(query:Query, quiet:Boolean=false):Iterable[Inference] = Await.result(asyncQuery(query, quiet), scala.concurrent.duration.Duration.Inf)

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
}
