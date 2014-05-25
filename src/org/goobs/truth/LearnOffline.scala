package org.goobs.truth

import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.Messages.Inference
import org.goobs.truth.TruthValue.TruthValue
import edu.stanford.nlp.classify.{LinearClassifier, LinearClassifierFactory, RVFDataset}
import edu.stanford.nlp.ling.{Datum, RVFDatum}
import java.io.File
import org.goobs.truth.Learn.WeightVector
import edu.stanford.nlp.stats.{ClassicCounter, Counter, Counters}
import scala.collection.JavaConversions._

/**
 * Learn in batch mode; this makes use of a Logistic Regression model to find a good weight assignment.
 *
 * @author Gabor Angeli
 */

object LearnOffline extends Client {

  def correctTable(guess:TruthValue, gold:TruthValue):Boolean = {
    guess match {
      case TruthValue.TRUE =>
        gold match {
          case TruthValue.TRUE => true
          case TruthValue.FALSE => false
          case TruthValue.UNKNOWN => false
        }
      case TruthValue.FALSE =>
        gold match {
          case TruthValue.TRUE => false
          case TruthValue.FALSE => true
          case TruthValue.UNKNOWN => false
        }
      case TruthValue.UNKNOWN => throw new IllegalArgumentException("Truth table not defined for guess=UNK")
    }
  }

  def batchUpdateWeights(predictions:Iterable[(Iterable[Inference], TruthValue)], weights:Array[Double]):Array[Double] = {
    import Implicits.{flattenWeights, inflateWeights}
    // For debugging
    val aggregateFeats = new collection.mutable.HashMap[String, Counter[Boolean]]()
    // (1) Create Dataset
    val dataset = new RVFDataset[Boolean, String]()
    for ( (paths, gold) <- predictions;
          path <- bestInference(paths, weights) ) {
      // Get whether we were correct
      val prob = probability(Some(path), weights)
      val guess = if (prob > 0.5) TruthValue.TRUE
                  else TruthValue.FALSE
      val isCorrect = correctTable(guess, gold)
      // Featurize
      val features: WeightVector = featurize(path)
      features.incrementCount("bias")
      // Debug
      startTrack("Analysis")
      for (pair <- Counters.toSortedListWithCounts(features)) {
        log(s"$gold :: ${pair.first}: ${pair.second}}")
        if (!aggregateFeats.contains(pair.first())) { aggregateFeats(pair.first) = new ClassicCounter[Boolean] }
        aggregateFeats(pair.first).incrementCount(isCorrect)
      }
      endTrack("Analysis")
      // Create Datum
      val datum = new RVFDatum[Boolean, String](features, isCorrect)
      // Add to Dataset
      dataset.add(datum)
    }
    // Debug (print weight agreement)
    startTrack("Aggregate Feature Counts")
    val hardWeights = NatLog.hardNatlogWeights
    val longestKey = hardWeights.keySet().maxBy( _.length ).length + 2
    for ( (feature, counts) <- aggregateFeats ) {
      " " * 10
      log(s"$feature${" " * (longestKey - feature.length)} => C:${counts.getCount(true)}   I:${counts.getCount(false)}  {${hardWeights.getCount(feature)}}")
    }
    endTrack("Aggregate Feature Counts")

    // (2) Train Classifier
    // (train)
    val factory: LinearClassifierFactory[Boolean, String] = new LinearClassifierFactory[Boolean, String]()
    factory.setSigma(0.1)
    val classifier: LinearClassifier[Boolean, String] = factory.trainClassifier(dataset)
    // (get accuracy)
    val accuracy = (for (i <- 0 until dataset.size()) yield {
      val datum = dataset.getDatum(i)
      val guess = classifier.classOf(datum.asInstanceOf[Datum[Boolean, String]])
      if (guess == datum.label()) 1 else 0
    }).sum.toDouble / dataset.size().toDouble
    log(YELLOW, BOLD, s"Classifier accuracy: ${Utils.percent.format(accuracy)}")
    // (get majority class)
    val majorityClass = dataset.getLabelsArray.groupBy(x => x).values.maxBy( _.size ).size.toDouble / dataset.size().toDouble
    log(YELLOW, BOLD, s"Majority class: ${Utils.percent.format(majorityClass)}")

    // (3) Update weights
    val rawWeights: Array[Double] = classifier.weightsAsMapOfCounters().get(true)
    val maxFeatureWeight: Double = rawWeights.drop(2).max + 1e-4
    val minFeatureWeight: Double = rawWeights.drop(2).min
    val bias: Double = rawWeights(0)
//    val newWeights = rawWeights.map( x => x - maxFeatureWeight )
//    val newWeights = rawWeights.map( x => 2.0 * (x - maxFeatureWeight) / (maxFeatureWeight - minFeatureWeight) )
//    val newWeights = rawWeights.map( x => math.min(x, -1e-4) )
    val newWeights: Array[Double] = rawWeights.map{ (w:Double) =>  // do a bilinear transform from the feature value range to [-2.0, -0.0]
      if (w < bias) {
        val denom = math.abs(bias - minFeatureWeight)
        val numer = math.abs(bias - w)
        val frac = numer / denom
        assert (-1.0 - frac <= -1.0)
        assert (-1.0 - frac >= -2.0)
        -1.0 - frac
      } else {
        val denom = math.abs(maxFeatureWeight - bias)
        val numer = math.abs(w - bias)
        val frac = numer / denom
        assert (-1.0 - frac >= -1.0)
        assert (-1.0 - frac <= -0.0)
        -1.0 + frac
      }
    }

    // Debug (check changed weights)
    startTrack("Learned Weight Summary")
    for ( (feature, counts) <- aggregateFeats ) {
      " " * 10
      log(s"$feature${" " * (longestKey - feature.length)} => C:${counts.getCount(true)}   I:${counts.getCount(false)}  {hard:${hardWeights.getCount(feature)}  learned:${newWeights.getCount(feature)}}")
    }
    endTrack("Learned Weight Summary")

    newWeights
  }


  /**
   * Learn a model.
   */
  def main(args:Array[String]):Unit = {
    import Implicits.{flattenWeights, inflateWeights}
    Props.exec(Implicits.fn2execInput1(() => {
      val trainData: DataStream = Props.LEARN_TRAIN.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar: DataStream, elem: DataStream) => soFar ++ elem}
      val testData: DataStream = Props.LEARN_TEST.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar: DataStream, elem: DataStream) => soFar ++ elem}

      forceTrack("Evaluating (pre-learning)")
        evaluate(testData, NatLog.softNatlogWeights, x => LearnOnline.synchronized { log(BOLD,YELLOW, "[Pre-learning] " + x) })
      endTrack("Evaluating (pre-learning)")

      forceTrack("Learning")
      var weights:Array[Double] = NatLog.softNatlogWeights
      for (pass <- 0 until Props.LEARN_OFFLINE_PASSES) {
        startTrack("Pass " + pass)
        val predictions: Iterable[(Iterable[Inference], TruthValue)] =
          evaluate(trainData, weights, print = x => LearnOnline.synchronized { log(BOLD,YELLOW, s"[pass $pass] " + x) }, quiet = true)
        weights = batchUpdateWeights(predictions, weights)
        if (Props.LEARN_MODEL_DIR.exists()) {
          Learn.serialize(weights, new File(Props.LEARN_MODEL_DIR + File.separator + "offline." + pass + ".tab"))
        }
        endTrack("Pass " + pass)
      }
      if (Props.LEARN_MODEL_DIR.exists()) {
        Learn.serialize(weights, new File(Props.LEARN_MODEL_DIR + File.separator + "offline.tab"))
      }
      endTrack("Learning")

      forceTrack("Evaluating")
      evaluate(testData, weights, x => LearnOnline.synchronized { log(BOLD,YELLOW, x) })
      endTrack("Evaluating")
    }), args)
  }

}
