package org.goobs.truth

import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.Messages.Inference
import org.goobs.truth.TruthValue.TruthValue
import edu.stanford.nlp.classify.RVFDataset
import edu.stanford.nlp.ling.RVFDatum
import java.io.File
import org.goobs.truth.Learn.WeightVector
import edu.stanford.nlp.stats.{ClassicCounter, Counter, Counters}
import scala.collection.JavaConversions._
import scala.collection.mutable
import edu.stanford.nlp.optimization._
import scala.Some

/**
 * Learn in batch mode; this makes use of a Logistic Regression model to find a good weight assignment.
 *
 * @author Gabor Angeli
 */

object LearnOffline extends Client {

  /**
   * When in doubt, try to optimize the function anyways!
   * @param correct The list of whether each example is correct or not.
   * @param features The list of feature vectors for each example.
   */
  class UnregularizedObjectiveFunction(correct:Seq[Boolean], features:Seq[Array[Double]]) extends AbstractCachingDiffFunction {
    override def initial:Array[Double] = Implicits.flattenWeights(NatLog.softNatlogWeights)
    override lazy val domainDimension: Int = initial.length

    private def dot(a:Array[Double], b:Array[Double]):Double = a.zip(b).foldLeft(0.0){ case (s, (ai, bi)) => s + ai*bi }
    private def sigmoid(w:Array[Double], f:Array[Double]):Double = 1.0 / (1.0 + math.exp(-dot(w,f)))
    private def sigmoidInverse(w:Array[Double], f:Array[Double]):Double = 1.0 / (1.0 + math.exp(dot(w,f)))

    override def calculate(x: Array[Double]): Unit = {
      val (likelihood, likelihoodDeriv) = correct.zip(features).foldLeft( (0.0, new Array[Double](domainDimension)) ){
        case ((valueSoFar:Double, derivSoFar:Array[Double]), (correct:Boolean, features:Array[Double])) =>
          assert (features.length == x.length)
          val probAtPoint = if (correct) {
            0.5 + 1.0 / (1.0 + math.exp(-dot(x,features)))
          } else {
            0.5 - 1.0 / (1.0 + math.exp(-dot(x,features)))
          }
          // Compute derivative
          val constant = (if (correct) 1.0 else -1.0) * sigmoid(x, features) * sigmoidInverse(x, features) * (1.0 / probAtPoint)
          val derivAtPoint = (0 until derivSoFar.length).map{ (i:Int) =>
            constant * features(i)
          }.toArray
          // Return
          (valueSoFar + math.log(probAtPoint), derivSoFar.zip(derivAtPoint).map{case (a, b) => a+b})
      }
      this.value = -likelihood
      this.derivative = likelihoodDeriv.map( x => -x )
    }
  }

  /**
   * Same as the unregularized objective, but with some l2 regularization thrown in, and a tiny push to make sure weights stay negative.
   * @param correct The list of whether each example is correct or not.
   * @param features The list of feature vectors for each example.
   * @param sigma The regularization constant.
   * @param negativePush A small value to make sure the weights stay negative. The larger this is, the more negative the weights will become.
   */
  class ObjectiveFunction(correct:Seq[Boolean], features:Seq[Array[Double]], sigma:Double, negativePush:Double) extends UnregularizedObjectiveFunction(correct, features) {
    private def l2norm(x:Array[Double]) = x.map( x => x * x ).sum

    override def calculate(x: Array[Double]): Unit = {
      // Do the hard work
      super.calculate(x)
      //  Error checks
      // Add regularization
      this.value += l2norm(x) / (2.0 * sigma * sigma)
      for (v <- x.drop(Implicits.searchFeatureStart)) {
        this.value -= negativePush * math.log(-v)
      }
      // Add regularization derivative
      for (i <- 0 until x.length) {
        derivative(i) += x(i) / (sigma * sigma)
      }
      for (i <- Implicits.searchFeatureStart until this.derivative.length) {
        derivative(i) -= negativePush / x(i)
      }
    }

  }


  /**
   * A table mapping a guess and gold TruthValue to whether it was correct or not.
   * @param guess The guessed truth value.
   * @param gold The gold truth value.
   * @return True if this example should be marked 'correct'.
   */
  private def correctTable(guess:TruthValue, gold:TruthValue):Boolean = {
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

  /** Project weights in a bit of a hacky way to be all negative */
  private def projectWeights(weightsAsCounter:WeightVector, default:WeightVector):Array[Double] = {
    // Convert weight to vector
    import Implicits.flattenWeights
    // (Copy the counter)
    val weightsWithDefault = new ClassicCounter[String](weightsAsCounter)
    // (Save defaults from last iteration)
    for (key <- default.keySet()) {
      if (!weightsWithDefault.containsKey(key)) {
        weightsWithDefault.setCount(key, default.getCount(key))
      }
    }
    // (Set a default return value)
    weightsWithDefault.setDefaultReturnValue(-2.0)
    val rawWeights:Array[Double] = weightsWithDefault
    // Collect stats
    val maxFeatureWeight: Double = rawWeights.drop(2).max + 1e-4
    val minFeatureWeight: Double = rawWeights.drop(2).min
    val bias: Double = rawWeights(0)
    // Project
    //    val newWeights = rawWeights.map( x => x - maxFeatureWeight )
    //    val newWeights = rawWeights.map( x => 2.0 * (x - maxFeatureWeight) / (maxFeatureWeight - minFeatureWeight) )
    //    val newWeights = rawWeights.map( x => math.min(x, -1e-4) )
    val newWeights: Array[Double] = rawWeights.map{ (w:Double) =>  // do a bilinear transform from the feature value range to [-2.0, -0.0]
      if (w < bias) {
        val denom = math.abs(bias - minFeatureWeight)
        val numer = math.abs(bias - w)
        val frac = numer / denom
        assert (-1.0 - 4.0 * frac <= -1.0)
        assert (-1.0 - 4.0 * frac >= -5.0)
        -1.0 - 4.0 * frac
      } else {
        val denom = math.abs(maxFeatureWeight - bias)
        val numer = math.abs(w - bias)
        val frac = numer / denom
        assert (-1.0 + frac >= -1.0)
        assert (-1.0 + frac <= -0.0)
        -1.0 + frac
      }
    }
    newWeights
  }

  type Memory = mutable.Set[Integer]

  def newMemory:Memory = new mutable.HashSet[Integer]

  /**
   * The main entry point for optimizing weights. Given a set of predictions, create a new optimized set of weights.
   * @param predictions The predictions from this iteration of search.
   * @param weights The last iteration's weights.
   * @return A new weight vector, learned from the search this iteration.
   */
  def batchUpdateWeights(predictions:Iterable[(Iterable[Inference], TruthValue)],
                         weights:Array[Double], memory:Memory):Array[Double] = {
    import Implicits.{inflateWeights, flattenWeights}
    // For debugging
    val aggregateFeats = new collection.mutable.HashMap[String, Counter[Boolean]]()
    // (1) Create Dataset
    val dataset = new RVFDataset[Boolean, String]()
    // Add to dataset
    for ( (paths, gold) <- predictions;
          path <- bestInference(paths, weights) ) {
      // Get whether we were correct
      val prob = probability(Some(path), weights)
      val guess = if (prob > 0.5) TruthValue.TRUE
                  else TruthValue.FALSE
      val isCorrect = correctTable(guess, gold)
      // Featurize
      val features: WeightVector = featurize(path)
      // Debug
      for (pair <- Counters.toSortedListWithCounts(features)) {
        if (!aggregateFeats.contains(pair.first())) { aggregateFeats(pair.first) = new ClassicCounter[Boolean] }
        aggregateFeats(pair.first).incrementCount(isCorrect, pair.second())
      }
      // Add to Dataset
      if (features.size() > 0) {
        val datum = new RVFDatum[Boolean, String](features, isCorrect)
        dataset.add(datum)
      }
    }

    /*
    // (2) Train Classifier
    // (train)
    def train(dataset:RVFDataset[Boolean, String]):LinearClassifier[Boolean,String] = {
      val factory: LinearClassifierFactory[Boolean, String] = new LinearClassifierFactory[Boolean, String]()
      factory.setSigma(Props.LEARN_OFFLINE_SIGMA)
      // Get the QN minimizer to shut up. No one cares about its nonsense.
      factory.setMinimizerCreator(new Factory[Minimizer[DiffFunction]] {
        override def create(): Minimizer[DiffFunction] = {
          val minimizer = new QNMinimizer(15)
          minimizer.shutUp()
          minimizer
        }
      })
      factory.trainClassifier(dataset)
    }
    // (evaluate)
    def evaluate(classifier:LinearClassifier[Boolean,String]):(Double, Iterable[Inference], Iterable[Inference], Iterable[Inference]) = {
      val projectedWeights:Array[Double] = projectWeights(classifier.weightsAsMapOfCounters().get(true), new ClassicCounter[String])  // don't project past weights here
      val (correctCount, mistakes, keepUnknown, correct) = predictions.foldLeft( (0, List[Inference](), List[Inference](), List[Inference]()) ){
          case ((correctSoFar, mistakesList, keepUnkList, correctList), (paths, gold)) =>
        bestInference(paths, projectedWeights) match {
          case Some(bestPath) =>
            val prob = probability(Some(bestPath), projectedWeights)
            val guess = if (prob > 0.5) TruthValue.TRUE
                        else TruthValue.FALSE
            if (math.abs(prob - 0.5) > 0.1 || gold != TruthValue.UNKNOWN) {
              // We're either right or wrong. Register a mistake
              correctTable(guess, gold) match {
                case true =>
                  (correctSoFar + 1, mistakesList, keepUnkList, bestPath :: correctList)
                case false =>
                  (correctSoFar, bestPath :: mistakesList, keepUnkList, correctList)
              }
            } else {
              // A peculiar case: we're not necessarily wrong, but we don't want to register ourselves
              // as a mistake either; nor do we want to register as correct, as that would move the weights
              // in the wrong direction.
              (correctSoFar + 1, mistakesList, bestPath :: keepUnkList, correctList)
            }
          case None =>
            (correctSoFar, mistakesList, keepUnkList, correctList)
        }
      }
      (correctCount.toDouble / predictions.count{ case (x, y) => !x.isEmpty }.toDouble, mistakes, keepUnknown, correct)
    }

    // Hill-climb on accuracy
    val timesToAdd = new ClassicCounter[Inference]()
    timesToAdd.setDefaultReturnValue(1.0)
    var bestClassifier = train(dataset)
    var (bestAccuracy, mistakes, keepUnk, correct) = evaluate(bestClassifier)
    var newDataset = dataset
    var iter = 0
    startTrack("Hill-climbing accuracy")
    while (bestAccuracy < 1.0 && newDataset.size() < 2 * dataset.size()) {
      newDataset = new RVFDataset[Boolean,String]()
      iter += 1
      startTrack(s"iter $iter")
      log(BOLD, s"accuracy: ${Utils.percent.format(bestAccuracy)}")
      log(s"${mistakes.size} incorrect paths")
      log(s"${correct.size} correct [T/F] paths")
      log(s"${keepUnk.size} correct [UNK] paths")
      // Upweight mistakes more than the last time around
      startTrack("Mistakes")
      for (path <- mistakes) {
        timesToAdd.incrementCount(path)
        log(s"mistake [${timesToAdd.getCount(path)} copies]: ${recursivePrint(path)}")
        log(s"   ${featurize(path)}")
        for (i <- 0 until timesToAdd.getCount(path).toInt) {
          newDataset.add(new RVFDatum[Boolean,String](featurize(path), false))
        }
      }
      endTrack("Mistakes")
      // Add correct paths, with last weighting
      for (path <- correct) {
        for (i <- 0 until timesToAdd.getCount(path).toInt) {
          newDataset.add(new RVFDatum[Boolean, String](featurize(path), true))
        }
      }
      // Add paths we'd like to keep towards UNK, but didn't get wrong
      for (path <- keepUnk) {
        for (i <- 0 until timesToAdd.getCount(path).toInt) {
          newDataset.add(new RVFDatum[Boolean, String](featurize(path), false))
        }
      }
      // Retrain
      bestClassifier = train(newDataset)
      val (newAccuracy, newMistakes, newKeepUnk, newCorrect) = evaluate(bestClassifier)
      bestAccuracy = newAccuracy; mistakes = newMistakes; keepUnk = newKeepUnk; correct = newCorrect
      endTrack(s"iter $iter")
    }
    endTrack("Hill-climbing accuracy")

    log(YELLOW, BOLD, s"Classifier accuracy: ${Utils.percent.format(bestAccuracy)}")
    val rawWeights: WeightVector = bestClassifier.weightsAsMapOfCounters().get(true)
    val newWeights: Array[Double] = projectWeights(rawWeights, weights)  // do project past weights here
    */

    /*
    // (2) Compute Weights
    val newWeights: WeightVector = {
      val counts = new ClassicCounter[String]
      counts.setDefaultReturnValue(-1.0)
      val delta = predictions.size.toDouble / 500.0
      for ( (feature, accuracy) <- aggregateFeats) {
        counts.setCount(feature, (-1.0 - 1e-4) + (accuracy.getCount(true) + delta) / (accuracy.getCount(true) + accuracy.getCount(false) + 2.0 * delta) )
      }
      Counters.multiplyInPlace(counts, 3.0)
      for (key <- weights.keySet()) {
        if (!counts.containsKey(key)) { counts.setCount(key, weights.getCount(key)) }
      }
      counts
    }
    */

    // (2) Compute Weights
    startTrack("Minimizing")
    // (create dataset)
    val (correct, features) = (for (i <- 0 until dataset.size()) yield {
      val featVector:Array[Double] = dataset.getDatum(i).asFeaturesCounter()
      val correct:Boolean = dataset.labelIndex().get(dataset.getLabelsArray.apply(i))
      (correct, featVector)
    }).unzip
    // (minimize)
    val objective = new ObjectiveFunction(correct, features, Props.LEARN_OFFLINE_SIGMA, 1e-5)
    val optimizer = new CGMinimizer()
    val newWeights: Array[Double] = optimizer.minimize(objective, 1e-2, weights)
    // (tweak)
    val seenMask:WeightVector = new ClassicCounter[String]
    for (feat <- aggregateFeats.keys) { seenMask.incrementCount(feat) }
    val seenArray:Array[Double] = seenMask
    for (i <- Implicits.searchFeatureStart until seenArray.length) {
      if (seenArray(i) != 0.0) { memory.add(i) }
      if (!memory(i)) { newWeights(i) = newWeights(i) / 2.0 }
    }
    endTrack("Minimizing")


    // (3) Update weights
    // Debug (check changed weights)
    startTrack("Learned Weight Summary")
    val hardWeights = NatLog.hardNatlogWeights
    val longestKey = hardWeights.keySet().maxBy( _.length ).length + 2
    for ( (feature, counts) <- aggregateFeats ) {
      log(s"$feature${" " * (longestKey - feature.length)} => C:${Utils.short.format(counts.getCount(true))}   I:${Utils.short.format(counts.getCount(false))}  {hard:${Utils.short.format(hardWeights.getCount(feature))}  learned:${Utils.short.format(newWeights.getCount(feature))}}")
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
      var weights:Array[Double] = Learn.initialization
      val memory = newMemory
      for (pass <- 0 until Props.LEARN_OFFLINE_PASSES) {
        startTrack("Pass " + pass)
        val predictions: Iterable[(Iterable[Inference], TruthValue)] =
          evaluate(trainData, weights, print = x => LearnOnline.synchronized { log(BOLD,YELLOW, s"[pass $pass] " + x) }, quiet = true)
        weights = batchUpdateWeights(predictions, weights, newMemory)
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
      evaluate(testData, weights,
        print = x => LearnOnline.synchronized { log(BOLD,YELLOW, x) },
        prFile = new File("logs/lastPR.dat"))
      endTrack("Evaluating")
    }), args)
  }

}
