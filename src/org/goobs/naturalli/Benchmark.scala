package org.goobs.naturalli

import DataSource._
import org.goobs.naturalli.Learn.WeightVector

import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.naturalli.scripts.ShutdownServer
import org.goobs.naturalli.TruthValue.TruthValue

/**
 * TODO(gabor) JavaDoc
 *
 * @author gabor
 */
object Benchmark extends Client {

  case class Accuracy(name:String, shouldTake:Datum=>Boolean, var correct:Int = 0, var total:Int = 0,
                      var guessedAndTrue:Int = 0, var guessed:Int = 0, var gold:Int = 0) {
    def accuracy:Double = if (total == 0) 0.0 else correct.toDouble / total.toDouble
    def precision:Double = if (guessed == 0) 1.0 else guessedAndTrue.toDouble / guessed.toDouble
    def recall:Double = if (gold == 0) 0.0 else guessedAndTrue.toDouble / gold.toDouble
    def f1:Double = if (total == 0 || precision + recall == 0.0) 0.0 else 2.0 * precision * recall / (precision + recall)
    override def toString:String
      = s"Accuracy[$name]: ${Utils.percent.format(accuracy)} $correct / $total {P:${Utils.percent.format(precision)} R:${Utils.percent.format(recall)} F1:${Utils.percent.format(f1)}}"
  }


  def evaluateBenchmark(data:DataStream, weights:WeightVector,
                subResults:Seq[(String, Datum=>Boolean)] = Nil,
                remember:Datum=>Boolean = x => false) {
    startTrack("Evaluating")
    // Run queries
    val accuracies = Accuracy("all", (x:Datum) => true, 0, 0) :: subResults.map{ case (name:String, fn:(Datum=>Boolean)) => Accuracy(name, fn, 0, 0) }.toList
    for ( (query, gold: TruthValue) <- data) {
      val datum:Datum = (query, gold)
      // Run Query
      explain(query.head.getKnownFact(0), "antecedent", verbose = false)
      explain(query.head.getQueryFact, "consequent", verbose = false)
      import Implicits.flattenWeights
      val prob:Double = probability(issueQuery(query.head
        .setTimeout(Props.SEARCH_TIMEOUT)
        .setCosts(Learn.weightsToCosts(weights))
        .setSearchType("ucs")
        .setCacheType("bloom")
        .build(), quiet=false, singleQuery=true), weights)

      // Check if correct
      val guessed = prob >= 0.51
      val correct:Boolean = gold match {
        case TruthValue.TRUE => guessed
        case TruthValue.FALSE => prob <= 0.49
        case TruthValue.UNKNOWN => prob < 0.51 && prob > 0.49
        case _ => throw new IllegalArgumentException("Gold annotation has invalid truth value: " + gold)
      }
      if (correct) {
        if (remember.apply( datum )) {
          log(BOLD, GREEN, "correct (gold=" + gold + "; guess=" + prob + ")")
        } else {
          log(GREEN, "correct (gold=" + gold + "; guess=" + prob + ")")
        }
      } else {
        if (remember.apply( datum )) {
          log(RED, BOLD, "--------------------")
          log(RED, BOLD, ">>> incorrect (gold=" + gold + "; guess=" + prob + ")")
          log(RED, BOLD, "--------------------")
        } else {
          log(RED, "incorrect (gold=" + gold + "; guess=" + prob + ")")
        }
      }

      // Update accuracies
      accuracies.foreach{ (view:Accuracy) =>
        if (view.shouldTake.apply(datum)) {
          view.total += 1
          if (correct) { view.correct += 1 }
          if (guessed && gold == TruthValue.TRUE) { view.guessedAndTrue += 1; }
          if (guessed) { view.guessed += 1; }
          if (gold == TruthValue.TRUE) { view.gold += 1; }
        }
      }
    }

    // Print
    startTrack("Scores")
    for (view <- accuracies) {
      log(BLUE, BOLD, view)
    }
    endTrack("Scores")
    endTrack("Evaluating")

    forceTrack("Shutting down server")
    ShutdownServer.shutdown()
    endTrack("Shutting down server")
  }
}

