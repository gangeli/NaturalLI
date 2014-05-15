package org.goobs.truth

import scala.collection.JavaConversions._

import org.goobs.truth.Messages._
import Monotonicity._
import java.io.{DataOutputStream, DataInputStream}
import java.net.Socket

import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.scripts.ShutdownServer
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

  /**
   * Issue a query to the search server, and block until it returns
   * @param query The query, with monotonicity markings set.
   * @param quiet Suppress logging messages
   * @return The query fact, with a truth value and confidence
   */
  def issueQuery(query:Query, quiet:Boolean=false):Iterable[Inference] = {
    def doQuery(query:Query, host:String, port:Int):Iterable[Inference] = {
      // Set up connection
      val sock = new Socket(host, port)
      val toServer = new DataOutputStream(sock.getOutputStream)
      val fromServer = new DataInputStream(sock.getInputStream)
      if (!quiet) log("connection established with server.")

      // Write query
      query.writeTo(toServer)
      if (!quiet) log(s"wrote query to server (${query.getSerializedSize} bytes); awaiting response...")
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
    val main = if(Props.SERVER_MAIN_HOST.equalsIgnoreCase("null")) Nil else doQuery(query, Props.SERVER_MAIN_HOST, Props.SERVER_MAIN_PORT)
    if (main.isEmpty && (!Props.SERVER_BACKUP_HOST.equals(Props.SERVER_MAIN_HOST) || Props.SERVER_BACKUP_PORT != Props.SERVER_BACKUP_PORT)) {
      doQuery(Utils.simplifyQuery(query, (x:String) =>NatLog.annotate(x).head), Props.SERVER_BACKUP_HOST, Props.SERVER_BACKUP_PORT)
    } else {
      main
    }
  }

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
    startTrack("Making server")
    val couldMake = List[String]("""make""", "-j" + Execution.threads) ! ProcessLogger { line => log(line) }
    endTrack("Making server")
    if (couldMake != 0) { return -1 }

    // Run the server
    var running = false
    startTrack("Starting Server")
    List[String]("catchsegv", "src/naturalli_server", "" + Props.SERVER_MAIN_PORT) ! ProcessLogger{line =>
      if (!running || printOut) { log(line) }
      if (line.startsWith("Listening on port") && !running) {
        // Server is initialized -- we can start doing client-side stuff
        running = true
        endTrack("Starting Server")
        new Thread(new Runnable {
          override def run(): Unit = {
            // Finish loading the annotators
            cache.join()

            // Call the callback
            // vvv
            callback()
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
