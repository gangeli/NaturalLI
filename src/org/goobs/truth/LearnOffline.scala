package org.goobs.truth

import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.Messages.Inference
import org.goobs.truth.TruthValue.TruthValue
import edu.stanford.nlp.classify.{LinearClassifierFactory, RVFDataset}
import edu.stanford.nlp.ling.RVFDatum
import edu.stanford.nlp.stats.ClassicCounter

/**
 * Learn in batch mode;
 *
 * @author gabor
 */

object LearnOffline extends Client {
  /**
   * Learn a model.
   */
  def main(args:Array[String]):Unit = {
    import Implicits.flattenWeights
    Props.exec(Implicits.fn2execInput1(() => {
      val trainData: DataStream = Props.LEARN_TRAIN.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar: DataStream, elem: DataStream) => soFar ++ elem}
      val testData: DataStream = Props.LEARN_TEST.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar: DataStream, elem: DataStream) => soFar ++ elem}

//      forceTrack("Evaluating (pre-learning)")
//        evaluate(testData, NatLog.softNatlogWeights, x => LearnOnline.synchronized { log(BOLD,YELLOW, "[Pre-learning] " + x) })
//      endTrack("Evaluating (pre-learning)")

      forceTrack("Learning")
      var weights:Array[Double] = NatLog.softNatlogWeights
      for (pass <- 0 until Props.LEARN_OFFLINE_PASSES) {
        startTrack("Pass " + pass)

        // (1) Get predictions
        val predictions: Iterable[(Iterable[Inference], TruthValue)] =
          evaluate(trainData, weights, print = x => LearnOnline.synchronized { log(BOLD,YELLOW, s"[pass $pass] " + x) }, quiet = true)

        // (2) Create Dataset
        val dataset = new RVFDataset[Boolean, String]()
        for ( (paths, gold) <- predictions;
              path <- paths ) {
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

        // (3) Train Classifier
        val factory = new LinearClassifierFactory[Boolean, String]()
        val classifier = factory.trainClassifier(dataset)

        // (4) Update weights
        weights = classifier.weightsAsMapOfCounters().get(true)
        endTrack("Pass " + pass)
      }
      endTrack("Learning")

      forceTrack("Evaluating")
      evaluate(testData, weights, x => LearnOnline.synchronized { log(BOLD,YELLOW, x) })
      endTrack("Evaluating")
    }), args)
  }

}
