package org.goobs.truth

import scala.concurrent._

import org.goobs.truth.Messages._
import edu.stanford.nlp.util.logging.Redwood.Util._
import java.io.File
import org.goobs.truth.DataSource.DataStream
import org.goobs.truth.EdgeType.EdgeType

import edu.stanford.nlp.stats.ClassicCounter
import scala.concurrent.duration.Duration
import scala.collection.JavaConversions._
import org.goobs.truth.Learn.WeightVector
import edu.stanford.nlp.util.Sets
import org.goobs.truth.TruthValue.TruthValue

/**
 * A utility for evaluating a system.
 *
 * @author gabor
 */
trait Evaluator {
  import Evaluate._
  import Implicits.{flattenWeights, inflateWeights}

  /** Offer a prediction for a given query, parameterized by the given weights. */
  def guess(query:Query.Builder, weights:Array[Double]):Future[Iterable[Inference]]

  /**
   * Featurize a given inference chain.
   * This is a mirror of the implicit featurization done on the server side to
   * compute costs.
   * @param path The inference chain to featurize.
   * @return The feature vector for this path.
   */
  def featurize(path:Inference):WeightVector = {
    // Recursive call
    val features
    = if (path.hasImpliedFrom) featurize(path.getImpliedFrom)
    else new ClassicCounter[String]
    if (path.hasIncomingEdgeType && path.getIncomingEdgeType != 63) {
      // Variables for score
      val edge: EdgeType.Value = EdgeType(path.getIncomingEdgeType)
      val state: CollapsedInferenceState
        = if (path.hasImpliedFrom && path.getImpliedFrom.hasState) path.getImpliedFrom.getState
          else CollapsedInferenceState.TRUE
      val monotonicity: Monotonicity = if (path.hasMonotoneContext) path.getMonotoneContext else Messages.Monotonicity.UP
      // Compute score
      val useTrueWeights:Boolean = state == CollapsedInferenceState.TRUE
      val edgeCost:Double = if (path.hasIncomingEdgeCost) path.getIncomingEdgeCost else 1.0
      val featureKey:String = feature(edge, useTrueWeights, monotonicity)
      assert(!edgeCost.isNaN)
      assert(!edgeCost.isInfinite)
      features.incrementCount(featureKey, edgeCost)
    }
    // Return
    features
  }

  /**
   * Featurize a set of paths. By and large, this will featurize the first path.
   * @param inferences The paths to featurize.
   * @return A feature vector for this set of paths.
   */
  def features(inferences:Iterable[Inference]):WeightVector = {
    inferences.headOption match {
      case Some(inference) => featurize(inference)
      case None => new ClassicCounter[String]
    }
  }

  /** A little helper to compute a dot product */
  private def dotProduct(a:WeightVector, b:WeightVector):Double = {
    var product = 0.0
    for (key <- Sets.union(a.keySet, b.keySet())) {
      val aCount = a.getCount(key)
      val bCount = b.getCount(key)
      assert(!aCount.isNaN)
      assert(!bCount.isNaN)
      if (aCount != 0 && bCount != 0) {
        product += a.getCount(key) * b.getCount(key)
        assert(!product.isNaN, "Product became NaN on key '" + key + "': " + aCount + " * " + bCount)
      }
    }
    assert (!product.isNaN)
    product
  }

  /**
   * Return the prediction for a given set of inferences.
   * This reduces entirely to the dot product between the feature vector of the inferences
   * and the passed weight vector.
   * @param inference The "guess" -- the inference paths returned by the search.
   * @param wVector The weight vector at the current state of inference.
   * @return f(x)*w, the dot product between the feature and weight vector.
   */
  private def prediction(inference:Iterable[Inference], wVector:Array[Double]):Double = {
    val feats:Array[Double] = features(inference)
    assert (wVector.drop(2).forall( _ <= 0.0 ))
    assert (feats.forall( _ >= 0.0 ))
    assert (feats.forall( !_.isNaN ))
    val p = dotProduct(wVector, feats)
    if (p > 0.0) 0.0 else p
  }

  /**
   * Get the probability of truth for a given set of inferences.
   * @param inference The "guess" -- the inference paths returned by the search.
   * @param wVector The weight vector at the current state of inference.
   * @return A probability 0 <= p <= 1 for whether this fact is true or not.
   */
  def probability(inference:Iterable[Inference], wVector:Array[Double]):Double = {
    val state:Double = inference.headOption.fold(CollapsedInferenceState.UNKNOWN)(_.getState) match {
      case CollapsedInferenceState.TRUE => 1.0
      case CollapsedInferenceState.FALSE => -1.0
      case CollapsedInferenceState.UNKNOWN => 0.0
    }
    val pred:Double = prediction(inference, wVector)
    val logistic = 1.0 / (1.0 + math.exp(-pred * state))
    val prob = 0.5*state + logistic
    assert (!prob.isNaN)
    assert (prob >= 0.0, "Not a probability: " + prob + " from x=" + (pred * state) + " => sigmoid=" + logistic)
    assert (prob <= 1.0, "Not a probability: " + prob + " from x=" + (pred * state) + " => sigmoid=" + logistic)
    prob
  }

  /**
   * Compute the subgradient of the probability in the probability() function.
   * @param inference The "guess" -- the inference paths returned by the search.
   * @param wVector The weight vector at the current state of inference.
   * @return The subgradient of the probability.
   */
  def probabilitySubgradient(inference:Iterable[Inference], wVector:Array[Double]):Array[Double] = {
    // Compute gradient
    val gradient = inference.headOption match {
      case Some(guessPath) =>
        guessPath.getState match {
          case CollapsedInferenceState.TRUE =>
            val sigmoid = 1.0 / (1.0 + math.exp(-prediction(inference, wVector)))
            flattenWeights(features(inference)).map(x => sigmoid * (1.0 - sigmoid) * x)
          case CollapsedInferenceState.FALSE =>
            val sigmoid = 1.0 / (1.0 + math.exp(prediction(inference, wVector)))
            flattenWeights(features(inference)).map(x => sigmoid * (1.0 - sigmoid) * x)
          case _ => throw new RuntimeException("Unknown inference state")
        }
      case None => wVector.map( _ => 0.0 )
    }
    // Some sanity checks
    // (the gradient is real-valued)
    assert( gradient.forall( x => !x.isNaN && !x.isInfinite ) )
    // (the gradient is pushing weights up if true, and down if false)
    assert ( gradient.drop(2).forall( x => x >= 0.0 ) || gradient.forall( x => x <= 0.0 ), "took a fishy gradient update!" )
    // Return
    gradient
  }

  def evaluate(data:DataStream, weightsInput:Array[Double], print:String=>Unit, prFile:File=new File("/dev/null")):Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val weights = weightsInput.map( x => x )

    // An encapsulation of a result point
    case class ResultPoint(prob:Double, gold:Boolean) {
      def guess = prob > 0.5
      def isCorrect = guess == gold
      def correctlyTrue = guess && gold
      def correctlyFalse = !guess && !gold
    }

    // Print out a result
    def dumpResult(results:Seq[ResultPoint], tag:String = ""):Unit = {
      val guessAndGold = results.map( r => (r.guess, r.gold) )
      val fmt = (x:Double) => Utils.percent.format(x)
      print(s"$tag Accuracy: ${fmt(accuracy(guessAndGold))}")
      print(s"$tag        P: ${fmt(p(guessAndGold))} R: ${fmt(r(guessAndGold))} F1: ${fmt(f1(guessAndGold))}")
    }


    // Run baseline
    def baselineProb(queries:Iterable[Query.Builder]):Future[Double]  = Future.successful(queries.map( (q:Query.Builder) => if (q.getForceFalse) 0.0 else 1.0 )).map( _.foldLeft(1.0){ case (p:Double, guess:Double) => p * guess })
    val baseline: Future[Seq[ResultPoint]] = Future.sequence(data.map{ case (queries:Iterable[Query.Builder], gold:TruthValue) =>
      baselineProb(queries).map( (p:Double) => ResultPoint(p, gold == TruthValue.TRUE))
    })
    dumpResult(Await.result(baseline, Duration.Inf), "(baseline)")

    // Run evaluation
    def noisyAndProb(queries:Iterable[Query.Builder]):Future[(Double, String)]
      = Future.sequence( queries.map(guess(_, weights)) ).map( _.foldLeft((1.0, "")) {
      case ((p: Double, tag: String), guess: Iterable[Inference]) =>
        (p * probability(guess, weights), guess.headOption.fold(tag)(_.getTag))
    })
    val results: Future[Seq[ResultPoint]] = Future.sequence(data.map{ case (queries:Iterable[Query.Builder], gold:TruthValue) =>
      val futureProb = if (queries.exists( _.getForceFalse )) Future.successful((0.0, "forced")) else noisyAndProb(queries)
      futureProb.map{ case (p:Double, tag:String) =>
        val result = ResultPoint(p, gold == TruthValue.TRUE)
        if (result.gold && result.guess) {
          log(GREEN, s"[guess=true] ${Utils.df.format(result.prob)} $tag: ${queries.head.getQueryFact.getGloss}")
        } else if (result.gold && !result.guess) {
          log(RED, s"[guess=false] ${Utils.df.format(result.prob)} $tag: ${queries.head.getQueryFact.getGloss}")
        } else if (!result.gold && result.guess) {
          log(RED, BOLD, s"[guess=true] ${Utils.df.format(result.prob)} $tag: ${queries.head.getQueryFact.getGloss}")
        }
        result
      }
    })
    val sortedResults = Await.result(results, Duration.Inf).sortBy( x => x.prob )
    dumpResult(sortedResults)

    // Run PR curve
    assert (sortedResults.isEmpty || sortedResults.head.prob <= sortedResults.last.prob)
    val (optimalThreshold, optimalP, optimalR, optimalF1) =(0 until sortedResults.length).map{ (falseUntil:Int) =>
      val (p, r, f1) = PRF1(sortedResults.take(falseUntil).map{ x => (false, x.gold) }.toList :::
        sortedResults.drop(falseUntil).map{ x => (true, x.gold) }.toList)
      (falseUntil, p, r, f1)
    }.filter{ case (t, p, r, f1) => !f1.isNaN && !f1.isInfinite }.maxBy( _._4 )
    val tunedResults: List[(Boolean, Boolean)] = sortedResults.take(optimalThreshold).map{ x => (false, x.gold) }.toList ::: sortedResults.drop(optimalThreshold).map{ x => (true, x.gold) }.toList
    val optimalAccuracy: Double = accuracy(tunedResults)
    val optimalAUC = auc(tunedResults)
    print(s"    Threshold: ${Utils.percent.format(sortedResults(optimalThreshold).prob)} {index=$optimalThreshold}")
    print(s"Opt. Accuracy: ${Utils.percent.format(optimalAccuracy)}")
    print(s"    Optimal P: ${Utils.percent.format(optimalP)} R: ${Utils.percent.format(optimalR)} F1: ${Utils.percent.format(optimalF1)}")
    print(s"  Optimal AUC: ${Utils.percent.format(optimalAUC)}")
  }
}

/**
 * Some static helpers for evaluation
 */
object Evaluate extends Client {
  def p(guessed: Seq[Boolean], golds: Seq[Boolean]):Double = {
    val correctGuess:Int = guessed.zip(golds).count{ case (guess, gold) => guess && gold }
    val guessCount:Int = guessed.count( x => x )
    if (guessCount == 0) 1.0 else correctGuess.toDouble / guessCount.toDouble
  }

  def r(guessed: Seq[Boolean], golds: Seq[Boolean]):Double = {
    val correctGuess:Int = guessed.zip(golds).count{ case (guess, gold) => guess && gold }
    val goldCount:Int = golds.count( x => x )
    if (goldCount == 0) 0.0 else correctGuess.toDouble / goldCount.toDouble
  }

  def f1(guessed: Seq[Boolean], golds: Seq[Boolean]):Double = {
    val correctGuess:Int = guessed.zip(golds).count{ case (guess, gold) => guess && gold }
    val guessCount:Int = guessed.count( x => x )
    val goldCount:Int = golds.count( x => x )
    if (guessCount == 0 || goldCount == 0 || correctGuess == 0) { return 0.0 }
    val p = correctGuess.toDouble / guessCount.toDouble
    val r = correctGuess.toDouble / goldCount.toDouble
    2.0 * p * r / (p + r)
  }

  def accuracy(guessed: Seq[Boolean], golds: Seq[Boolean]):Double = accuracy(guessed.zip(golds))

  def p(guessAndGold: Seq[(Boolean,Boolean)]):Double = {
    val (guessed, gold) = guessAndGold.unzip
    p(guessed, gold)
  }

  def r(guessAndGold: Seq[(Boolean,Boolean)]):Double = {
    val (guessed, gold) = guessAndGold.unzip
    r(guessed, gold)
  }

  def f1(guessAndGold: Seq[(Boolean,Boolean)]):Double = {
    val (guessed, gold) = guessAndGold.unzip
    f1(guessed, gold)
  }

  def PRF1(guessAndGold: Seq[(Boolean,Boolean)]):(Double,Double,Double) = {
    val (guessed, gold) = guessAndGold.unzip
    (p(guessed, gold), r(guessed, gold), f1(guessed, gold))
  }

  def accuracy(guessAndGold: Seq[(Boolean,Boolean)]):Double = {
    guessAndGold.count{case (x, y) => x == y}.toDouble / guessAndGold.size.toDouble
  }

  def auc(guessAndGold: Seq[(Boolean,Boolean)]):Double = {
    val mostToLeastConfident: Seq[(Boolean, Boolean)] = guessAndGold.reverse
    val datasetTrueSize = guessAndGold.count(_._2).toDouble
    var rollingAccuracyNumer = 0
    var rollingAccuracyDenom = 0
    var sumAUC = 0.0
    for ( (guess, gold) <- mostToLeastConfident ) {
      if (guess || gold) {
        rollingAccuracyDenom += 1
        if (guess == gold) { assert (guess && gold); rollingAccuracyNumer += 1 }
        if (gold) {
          sumAUC += (rollingAccuracyNumer.toDouble / rollingAccuracyDenom.toDouble)
        }
      }
    }
    sumAUC / datasetTrueSize
  }


  def monoUp_stateTrue(edge:EdgeType):String   = "^,T" + "::" + edge
  def monoDown_stateTrue(edge:EdgeType):String = "v,T" + "::" + edge
  def monoFlat_stateTrue(edge:EdgeType):String = "-,T" + "::" + edge
  def monoAny_stateTrue(edge:EdgeType):String  = "*,T" + "::" + edge

  def monoUp_stateFalse(edge:EdgeType):String   = "^,F" + "::" + edge
  def monoDown_stateFalse(edge:EdgeType):String = "v,F" + "::" + edge
  def monoFlat_stateFalse(edge:EdgeType):String = "-,F" + "::" + edge
  def monoAny_stateFalse(edge:EdgeType):String  = "*,F" + "::" + edge

  /**
   * Compute the String representation of a feature with the given properties.
   * @param edge The edge type we are taking.
   * @param truth The truth state we are in (determines polarity).
   * @param mono The monotonicity we are under (up / down / flat / any).
   * @return A string feature key.
   */
  def feature(edge:EdgeType, truth:Boolean, mono:Monotonicity):String = {
    truth match {
      case true => mono match {
        case Monotonicity.UP             => monoUp_stateTrue(edge)
        case Monotonicity.DOWN           => monoDown_stateTrue(edge)
        case Monotonicity.FLAT           => monoFlat_stateTrue(edge)
        case Monotonicity.ANY_OR_INVALID => monoAny_stateTrue(edge)
      }
      case false => mono match {
        case Monotonicity.UP             => monoUp_stateFalse(edge)
        case Monotonicity.DOWN           => monoDown_stateFalse(edge)
        case Monotonicity.FLAT           => monoFlat_stateFalse(edge)
        case Monotonicity.ANY_OR_INVALID => monoAny_stateFalse(edge)
      }
    }
  }

  /**
   * Just evaluate the system, without running learning.
   */
  def main(args:Array[String]) {
    Props.exec(Implicits.fn2execInput1(() => {
      val testData:DataStream = Props.LEARN_TEST.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar:DataStream, elem:DataStream) => soFar ++ elem }
      import Implicits.flattenWeights
      forceTrack("Evaluating")
      evaluate(testData, NatLog.softNatlogWeights, x => Learn.synchronized { log(BOLD,YELLOW, "[Evaluate] " + x) })
      endTrack("Evaluating")
    }), args)
  }

}
