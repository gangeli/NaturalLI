package org.goobs.truth

import org.goobs.truth.Messages.Fact
import org.goobs.truth.Messages.Query

import edu.stanford.nlp.util.Execution
import edu.stanford.nlp.io.IOUtils
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.scripts.ShutdownServer

/**
 * A regression test for the inference engine
 *
 * @author gabor
 */
object RegressionTest {

  Props.NATLOG_INDEXER_LAZY = true
  Props.SERVER_HOST = "localhost"
  Props.SERVER_PORT = 41337

  def runClient():Unit = {
    val INPUT = """\s*\[([^\]]+)\]\s*\(([^,]+),\s*([^\)]+)\)\s*""".r
    var exitStatus = 0

    val reader = IOUtils.getBufferedReaderFromClasspathOrFileSystem("org/goobs/truth/regressions_natlog.dat")
    var line:String = reader.readLine()
    while ( line != null ) {
      if (!line.trim.equals("") && !line.trim.startsWith("#")) {
        // Print example
        System.err.flush()
        log("")
        log(BOLD, ">>> RUNNING " + line)

        // Parse Line
        val facts = line.split("\t").map {
          case INPUT(rel, subj, obj) => NatLog.annotate(subj, rel, obj)
          case _ => throw new IllegalArgumentException("Could not parse fact: " + line)
        }

        // Issue Query
        Client.explain(facts.last, "consequent")
        val score:Double = Learn.evaluate(Client.issueQuery(
          facts.slice(0, facts.length - 1).foldLeft(Query.newBuilder()){ case (builder, fact:Fact) =>
            Client.explain(fact, "antecedent")
            builder.addKnownFact(fact)
          }.setQueryFact(facts.last)
            .setUseRealWorld(false)
            .setTimeout(Props.SEARCH_TIMEOUT)
            .setWeights(Learn.weightsToCosts(NatLog.hardNatlogWeights))
            .setSearchType("ucs")
            .setCacheType("bloom")
            .build()), NatLog.hardNatlogWeights)

        // Check Validity
        if (score < 0.5) {
          System.err.println("SHOULD BE VALID (score=" + score + ": " + line)
          exitStatus += 1
        }
      }

      line = reader.readLine()
    }

    ShutdownServer.shutdown()

    System.exit(exitStatus)

  }

  def main(args:Array[String]) {
    ShutdownServer.shutdown()

    import scala.sys.process._
    List[String]("""make""", "-j" + Execution.threads,  """dist/server""").!
    var running = false
    val retval:Int = List[String]("dist/server", "" + Props.SERVER_PORT) ! ProcessLogger{line =>
      println(line)
      if (line.startsWith("Listening on port") && !running) {
        running = true
        new Thread(new Runnable {
          override def run(): Unit = runClient()
        }).start()
      }
    }

    System.exit(retval)

  }

}
