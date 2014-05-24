package org.goobs.truth

import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.Messages.Inference
import org.goobs.truth.TruthValue.TruthValue
import edu.stanford.nlp.classify.{LinearClassifier, LinearClassifierFactory, RVFDataset}
import edu.stanford.nlp.ling.{Datum, RVFDatum}
import java.io.File

/**
 * Learn in batch mode; this makes use of a Logistic Regression model to find a good weight assignment.
 *
 * @author Gabor Angeli
 */

object LearnOffline extends Client {

  def batchUpdateWeights(predictions:Iterable[(Iterable[Inference], TruthValue)], weights:Array[Double]):Array[Double] = {
    import Implicits.flattenWeights
    // (1) Create Dataset
    val dataset = new RVFDataset[Boolean, String]()
    for ( (paths, gold) <- predictions;
          path <- bestInference(paths, weights) ) {
      // Featurize
      val features = featurize(path)
      // Create Datum
      val datum = new RVFDatum[Boolean, String](features, gold match {
        case TruthValue.TRUE => true
        case TruthValue.FALSE => false
        case TruthValue.UNKNOWN => false
        case _ => throw new IllegalStateException("Invalid gold truth: " + gold)
      })
      // Add to Dataset
      dataset.add(datum)
    }

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
    val maxFeatureWeight = rawWeights.drop(2).max + 1e-4
    val minFeatureWeight = rawWeights.drop(2).min
    //        rawWeights.map( x => x - maxFeatureWeight )
    rawWeights.map( x => 2.0 * (x - maxFeatureWeight) / (maxFeatureWeight - minFeatureWeight) )
    //        rawWeights.map( x => math.min(x, -1e-4) )

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
