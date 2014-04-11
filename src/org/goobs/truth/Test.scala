package org.goobs.truth

import DataSource._
import org.goobs.truth.Learn.WeightVector

import edu.stanford.nlp.util.logging.Redwood.Util._
import java.text.DecimalFormat

/**
 * TODO(gabor) JavaDoc
 *
 * @author gabor
 */
object Test {

  case class Accuracy(name:String, shouldTake:Datum=>Boolean, var correct:Int = 0, var total:Int = 0) {
    override def toString:String = "Accuracy[" + name + "]: " + new DecimalFormat("0.00%").format(correct.toDouble / total.toDouble)
  }


  def evaluate(data:DataStream, weights:WeightVector,
                subResults:Seq[(String, Datum=>Boolean)] = Nil) {
    startTrack("Evaluating")
    // Run queries
    val accuracies = Accuracy("all", (x:Datum) => true, 0, 0) :: subResults.map{ case (name:String, fn:(Datum=>Boolean)) => Accuracy(name, fn, 0, 0) }.toList
    for ( (query, gold) <- data) {
      // Run Query
      Client.explain(query.getKnownFact(0), "antecedent")
      Client.explain(query.getQueryFact, "consequent")
      val prob:Double = Learn.evaluate(Client.issueQuery(query
        .setUseRealWorld(false)
        .setTimeout(Props.SEARCH_TIMEOUT)
        .setWeights(Learn.weightsToCosts(weights))
        .setSearchType("ucs")
        .setCacheType("bloom")
        .build()), weights)

      // Check if correct
      val correct:Boolean = gold match {
        case TruthValue.TRUE => prob >= 0.51
        case TruthValue.FALSE => prob <= 0.49
        case TruthValue.UNKNOWN => prob < 0.51 && prob > 0.49
        case _ => throw new IllegalArgumentException("Gold annotation has invalid truth value: " + gold)
      }
      if (correct) {
        log(GREEN, "correct (gold=" + gold + "; guess=" + prob + ")")
      } else {
        log(RED, "incorrect (gold=" + gold + "; guess=" + prob + ")")
      }

      // Update accuracies
      accuracies.foreach{ (view:Accuracy) =>
        val datum:Datum = (query, gold)
        if (view.shouldTake.apply(datum)) {
          view.total += 1
          if (correct) { view.correct += 1 }
        }
      }
    }

    // Print
    startTrack("Scores")
    for (view <- accuracies) {
      log(BLUE, view)
    }
    endTrack("Scores")
    endTrack("Evaluating")
  }
}

