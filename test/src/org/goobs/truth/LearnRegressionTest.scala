package org.goobs.truth

import org.goobs.truth.Messages.Inference

import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.TruthValue.TruthValue
import edu.stanford.nlp.stats.{Counters, ClassicCounter}

/**
 * A test to make sure that we can learn a good weight assignment over the regression tests
 *
 * @author gabor
 */
object LearnRegressionTest extends Client {

  /** Run the regression test */
  def runClient():Int = {
    import Implicits.{flattenWeights, inflateWeights}
    startTrack("Regression Learning Test")

    // Get Data
    val data = RegressionDataSource.read("org/goobs/truth/regressions_natlog.dat")
    var weights:Array[Double] = new ClassicCounter[String]
    weights = weights.map( x => -1.0 )

    // Learn
    for (pass <- 0 until 3) {
      startTrack("Pass " + pass)
      val predictions: Iterable[(Iterable[Inference], TruthValue)] =
        evaluate(data, weights, print = x => LearnOnline.synchronized { log(BOLD,YELLOW, s"[pass $pass] " + x) }, quiet = true)
      weights = LearnOffline.batchUpdateWeights(predictions, weights)
      endTrack("Pass " + pass)
    }
    endTrack("Regression Learning Test")

    // Evaluate
    val exitCode = RegressionTest.runClient(weights)
    Counters.printCounterSortedByKeys(weights)
    exitCode
  }

  def main(args:Array[String]):Unit = {
    var exitStatus:Int = 0
    Props.SERVER_MAIN_HOST = "localhost"
    Props.SERVER_MAIN_PORT = 41337
    Props.SERVER_BACKUP_HOST = "localhost"
    Props.SERVER_BACKUP_PORT = 41337
    startMockServer(() => exitStatus = runClient(), printOut = false)
    if (exitStatus == 0) {
      log(GREEN, "TESTS PASS")
    } else {
      log(RED, "FAILED " + exitStatus + " TESTS")
    }
    System.exit(exitStatus)
  }
}
