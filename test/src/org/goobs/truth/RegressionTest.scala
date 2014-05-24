package org.goobs.truth

import org.goobs.truth.Messages.{Inference, Fact, Query}

import edu.stanford.nlp.io.IOUtils
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.TruthValue.TruthValue
import org.goobs.truth.Learn.WeightVector

/**
 * A regression test for the inference engine
 *
 * @author gabor
 */
object RegressionTest extends Client {

  /** Evaluate, optionally backing off to a larger timeout (if not solution was found */
  private def evaluateQuery(facts:Seq[Fact], truth:TruthValue, timeout:Int, weights:WeightVector):Int = {
    import Implicits.flattenWeights
    val paths: Iterable[Inference] = issueQuery(
      facts.slice(0, facts.length - 1).foldLeft(Query.newBuilder()){ case (builder, fact:Fact) =>
        builder.addKnownFact(fact)
      }.setQueryFact(facts.last)
        .setUseRealWorld(false)
        .setTimeout(if (truth == TruthValue.UNKNOWN) Props.SEARCH_TIMEOUT else timeout)
        .setCosts(Learn.weightsToCosts(weights))
        .setSearchType("ucs")
        .setCacheType("bloom")
        .build())
    val score:Double = probability(paths, weights)

    // Check Validity
    truth match {
      case TruthValue.TRUE =>
        if (score < 0.6) {
          if (timeout < 1000000) { return evaluateQuery(facts, truth, timeout * 10, weights)}
          err(RED,"SHOULD BE VALID (score=" + score + ") ")
          return 1
        }
      case TruthValue.UNKNOWN =>
        if (score >= 0.6 || score <= 0.4) {
          err(RED,"SHOULD BE UNKNOWN (score=" + score + ") ")
          return 1
        }
      case TruthValue.FALSE =>
        if (score > 0.4) {
          if (timeout < 1000000) { return evaluateQuery(facts, truth, timeout * 10, weights)}
          err(RED,"SHOULD BE FALSE (score=" + score + ") ")
          return 1
        }
    }
    0
  }

  /** Run the regression test */
  def runClient(weights:WeightVector):Int = {
    startTrack("Regression Test")
    val INPUT = """\s*\[([^\]]+)\]\s*\(([^,]+),\s*([^\)]+)\)\s*""".r
    var exitStatus = 0

    val reader = IOUtils.getBufferedReaderFromClasspathOrFileSystem("org/goobs/truth/regressions_natlog.dat")
    var line:String = reader.readLine()
    while ( line != null ) {
      line = line.replaceAll("\\s*#.*", "")
      if (!line.trim.equals("")) {
        // Print example
        startTrack(BOLD, BLUE, "Running " + line)

        // Parse Line
        // (truth value)
        val truth:TruthValue = line.charAt(0) match {
          case '✔' => TruthValue.TRUE
          case '✘' => TruthValue.FALSE
          case '?' => TruthValue.UNKNOWN
        }
        // (unk mapping)
        val unkProvider = Utils.newUnkProvider
        // (parse fact
        val facts:Seq[Fact] = line.substring(1).split("\t").map {
          case INPUT(rel, subj, obj) => NatLog.annotate(subj, rel, obj, unkProvider)
          case _ => throw new IllegalArgumentException("Could not parse fact: " + line)
        }

        // Issue Query
        for ( fact <- facts.slice(0, facts.length - 1) ) {
          explain(fact, "antecedent")
        }
        explain(facts.last, "consequent")
        exitStatus += evaluateQuery(facts, truth, 10000, weights)
        endTrack("Running " + line)
      }

      line = reader.readLine()
    }
    endTrack("Regression Test")

    exitStatus
  }

  def main(args:Array[String]) {
    var exitStatus:Int = 0
    Props.SERVER_MAIN_HOST = "localhost"
    Props.SERVER_MAIN_PORT = 41337
    Props.SERVER_BACKUP_HOST = "localhost"
    Props.SERVER_BACKUP_PORT = 41337
    startMockServer(() => exitStatus = runClient(NatLog.hardNatlogWeights), printOut = true)
    if (exitStatus == 0) {
      log(GREEN, "TESTS PASS")
    } else {
      log(RED, "FAILED " + exitStatus + " TESTS")
    }
    System.exit(exitStatus)
  }

}
