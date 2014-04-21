package org.goobs.truth

import scala.collection.JavaConversions._

import org.goobs.truth.Messages._
import Monotonicity._
import java.io.{DataOutputStream, DataInputStream}
import java.net.Socket

import edu.stanford.nlp.util.logging.Redwood.Util._
import scala.Some
import org.goobs.truth.scripts.ShutdownServer
import edu.stanford.nlp.util.Execution

/**
 * @author Gabor Angeli
 */
object Client {

  def explain(fact:Fact, tag:String="fact"):Unit = {
    log(tag + ": " + fact.getWordList.map( w =>
      s"[${w.getMonotonicity match { case UP => "^" case DOWN => "v" case FLAT => "-"}}]${w.getGloss}:${w.getPos.toUpperCase}"
    ).mkString(" "))
    log(fact.getWordList.map{ w =>
      val synsets = NatLog.wordnet.getSynsets(w.getGloss)
      if (synsets == null || synsets.size == 0 || w.getSense == 0) {
        s"  ${w.getGloss} (${w.getWord}_${w.getSense}}): <unknown sense>"
      } else {
        s"  ${w.getGloss} (${w.getWord}_${w.getSense}}): ${synsets(w.getSense - 1).getDefinition}"
      }
    }.mkString("\n"))
  }

  /**
   * Issue a query to the search server, and block until it returns
   * @param query The query, with monotonicity markings set.
   * @return The query fact, with a truth value and confidence
   */
  def issueQuery(query:Query):Iterable[Inference] = {
    // Set up connection
    val sock = new Socket(Props.SERVER_HOST, Props.SERVER_PORT)
    val toServer = new DataOutputStream(sock.getOutputStream)
    val fromServer = new DataInputStream(sock.getInputStream)
    log("connection established with server.")

    // Write query
    query.writeTo(toServer)
    log(s"wrote query to server (${query.getSerializedSize} bytes); awaiting response...")
    sock.shutdownOutput()  // signal end of transmission

    // Read response
    val response:Response = Response.parseFrom(fromServer)
    if (response.getError) {
      throw new RuntimeException(s"Error on inference server: ${if (response.hasErrorMessage) response.getErrorMessage else ""}")
    }
    log(s"server returned ${response.getInferenceCount} paths after ${if (response.hasTotalTicks) response.getTotalTicks else "?"} ticks.")
    response.getInferenceList
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
    List[String]("src/server", "" + Props.SERVER_PORT) ! ProcessLogger{line =>
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
            forceTrack("Shutting down server")
            ShutdownServer.shutdown()
            endTrack("Shutting down server")
          }
        }).start()
      }
    }

  }

  def main(args:Array[String]):Unit = {
    Props.NATLOG_INDEXER_LAZY = true
    val INPUT = """\s*\[([^\]]+)\]\s*\(([^,]+),\s*([^\)]+)\)\s*""".r
    val weights = NatLog.softNatlogWeights

    do {
      println("")
      println("Enter an antecedent and a consequent as \"[rel](arg1, arg2)\".")
      for (antecedent:Fact <- /*"[have](all animal, tail)"*/ readLine("antecedent> ") match {
            case INPUT(rel, arg1, arg2) => Some(NatLog.annotate(arg1, rel, arg2, x => Utils.WORD_UNK))
            case _ => println("Could not parse antecedent"); None
          }) {
        explain(antecedent, "antecedent")
        for (consequent:Fact <- /*"[have](all cat, tail)"*/ readLine("consequent> ") match {
          case INPUT(rel, arg1, arg2) => Some(NatLog.annotate(arg1, rel, arg2, x => Utils.WORD_UNK))
          case _ => println("Could not parse consequent"); None
        }) {
          explain(consequent, "consequent")
          // We have our antecedent and consequent
          val query = Query.newBuilder()
            .setQueryFact(consequent)
            .addKnownFact(antecedent)
            .setUseRealWorld(true)
            .setTimeout(1000000)
            .setWeights(Learn.weightsToCosts(weights))
            .setSearchType("ucs")
            .setCacheType("bloom")
            .build()
          // Execute Query
          val paths:Iterable[Inference] = issueQuery(query)
          // Evaluate Query
          val prob = Learn.evaluate(paths, weights)
          // Debug Print
          if (prob > 0.5) { println("\033[32mVALID\033[0m (p=" + prob + ")") } else { println("\033[31mINVALID\033[0m (p=" + prob + ")") }
        }
      }
    } while (true)
  }
}
