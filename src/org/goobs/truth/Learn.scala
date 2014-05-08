package org.goobs.truth

import edu.stanford.nlp.stats.{Counters, ClassicCounter, Counter}
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.EdgeType.EdgeType
import org.goobs.truth.Messages._
import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.util.{Sets, Execution}
import java.io.{PrintWriter, File}
import java.util.Properties
import com.typesafe.config.{Config, ConfigFactory}
import scala.collection.JavaConversions._
import org.goobs.truth.TruthValue.TruthValue

import scala.language.implicitConversions



object Learn extends Client {
  type WeightVector = Counter[String]

  def monoUp_stateTrue(edge:EdgeType):String   = "^,T" + "::" + edge
  def monoDown_stateTrue(edge:EdgeType):String = "v,T" + "::" + edge
  def monoFlat_stateTrue(edge:EdgeType):String = "-,T" + "::" + edge
  def monoAny_stateTrue(edge:EdgeType):String  = "*,T" + "::" + edge

  def monoUp_stateFalse(edge:EdgeType):String   = "^,F" + "::" + edge
  def monoDown_stateFalse(edge:EdgeType):String = "v,F" + "::" + edge
  def monoFlat_stateFalse(edge:EdgeType):String = "-,F" + "::" + edge
  def monoAny_stateFalse(edge:EdgeType):String  = "*,F" + "::" + edge

  def feature(edge:EdgeType, truth:Boolean, mono:Monotonicity):String = {
    truth match {
      case true => mono match {
        case Monotonicity.UP      => monoUp_stateTrue(edge)
        case Monotonicity.DOWN    => monoDown_stateTrue(edge)
        case Monotonicity.FLAT    => monoFlat_stateTrue(edge)
        case Monotonicity.ANY_OR_INVALID => monoAny_stateTrue(edge)
      }
      case false => mono match {
        case Monotonicity.UP      => monoUp_stateFalse(edge)
        case Monotonicity.DOWN    => monoDown_stateFalse(edge)
        case Monotonicity.FLAT    => monoFlat_stateFalse(edge)
        case Monotonicity.ANY_OR_INVALID => monoAny_stateFalse(edge)
      }
    }
  }

  /** Implicit to convert from a weight counter to a weight vector */
  implicit def flattenWeights(w:WeightVector):Array[Double] = {
    (w.getCount("bias") ::
      (for (e <- EdgeType.values.toList;
         truth <- Array[Boolean](true, false);
         mono <- Monotonicity.values().toArray) yield {
      w.getCount(feature(e, truth, mono))
      })).toArray
  }

  /** Implicit to convert from a weight vector to a weight counter */
  def inflateWeights(w:Array[Double], ignoreValue:Double):WeightVector = {
    val feats = new ClassicCounter[String]
    if (w(0) != ignoreValue) { feats.incrementCount("bias", w(0)) }
    var i:Int = 1
    for (e <- EdgeType.values.toList;
          truth <- Array[Boolean](true, false);
          mono <- Monotonicity.values().toArray) {
      if (w(i) != ignoreValue) { feats.setCount(feature(e, truth, mono), w(i)) }
      i += 1
    }
    feats
  }
  implicit def inflateWeights(w:Array[Double]):WeightVector = inflateWeights(w, 0.0)

  /** Write a weight vector to a file. Returns the same file that we wrote to */
  def serialize(w:WeightVector, f:File):File = {
    Utils.printToFile(f){ (p:PrintWriter) =>
      for ( (key, value) <- Counters.asMap(w) ) {
        p.println(key + "\t" + value)
      }
    }
    f
  }

  /** Read a weight vector from a file */
  def deserialize(f:File):WeightVector = {
    io.Source.fromFile(f).getLines().toArray
      .foldLeft(new ClassicCounter[String]){ case (w:ClassicCounter[String], line:String) =>
        val fields = line.split("\t")
        w.setCount(fields(0), fields(1).toDouble)
        w
      }
  }

  /**
   * Serialize a weight vector into a protobuf to send to the server.
   * Note that the semantics of the serialized weights is the negative of the weights
   * used on the client.
   *
   * @param weights The weight vector to serialize
   * @return A protobuf message encoding the <b>cost</b> of each weight
   */
  def weightsToCosts(weights:WeightVector):Costs = {
    // Function to set the unlexicalized weights
    def unlexicalizedWeights(stateTrue:EdgeType=>String, stateFalse:EdgeType=>String):UnlexicalizedCosts = {
      val builder = UnlexicalizedCosts.newBuilder()
      for (from <- EdgeType.values.toArray.sortWith({ (a:EdgeType.Value, b:EdgeType.Value) => a.id < b.id })) {
        val trueWeight = weights.getCount(stateTrue(from)).toFloat
        val falseWeight = weights.getCount(stateFalse(from)).toFloat
        assert(trueWeight <= 0, "trueWeight=" + trueWeight)
        assert(falseWeight <= 0, "falseWeight=" + falseWeight)
        builder.addTrueCost(-trueWeight)
        builder.addFalseCost(-falseWeight)
      }
      builder.build()
    }

    Costs.newBuilder()
      .setUnlexicalizedMonotoneUp(unlexicalizedWeights(monoUp_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneDown(unlexicalizedWeights(monoDown_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneFlat(unlexicalizedWeights(monoFlat_stateTrue, monoFlat_stateFalse))
      .setUnlexicalizedMonotoneAny(unlexicalizedWeights(monoAny_stateTrue, monoAny_stateFalse))
      .setBias(weights.getCount("bias").toFloat)
      .build()
  }

  def featurize(path:Inference):WeightVector = {
    // Recursive call
    val features
      = if (path.hasImpliedFrom) featurize(path.getImpliedFrom)
        else new ClassicCounter[String]
    if (path.hasIncomingEdgeType && path.getIncomingEdgeType != 63) {
      // Variables for score
      val edge: EdgeType.Value = EdgeType(path.getIncomingEdgeType)
      val state: CollapsedInferenceState = if (path.hasState) path.getState else CollapsedInferenceState.TRUE
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
   * An abstract class for computing a given loss over just the single most peaked prediction.
   * @param guesses The inference paths we are guessing
   * @param goldTruth The ground truth for the query associated with this loss.
   */
  abstract class MaxTypeLoss(guesses:Iterable[Inference], goldTruth:TruthValue) {
    val guess:Option[Inference] = if (guesses.isEmpty) None else Some(guesses.maxBy( x => math.abs(0.5 - x.getScore) ))
    val features:Array[Double] = if (guess.isDefined) featurize(guess.get) else new ClassicCounter[String]
    val gold = goldTruth match {
      case TruthValue.TRUE => 1.0
      case TruthValue.FALSE => -1.0
      case TruthValue.UNKNOWN => 0.0
      case TruthValue.INVALID => throw new IllegalArgumentException("Invalid truth value")
    }

  }

  /**
   * A loss function over the logistic loss on the most peaked inference. That is, the inference with the furthest
   * search score from 0.5.
   * @param guesses The inference paths we are guessing
   * @param goldTruth The ground truth for the query associated with this loss.
   */
  class LogisticMaxLoss(guesses:Iterable[Inference], goldTruth:TruthValue) extends MaxTypeLoss(guesses,goldTruth)  with LogisticLoss {
    override def prediction(wVector:Array[Double]):Double = dotProduct(wVector, features)
    override def feature(i: Int): Double = features(i)
  }

  /**
   * A little helper for when we just want p(TRUE) rather than any structured loss
   * @param guesses The inference paths we are guessing
   */
  class ProbabilityOfTruth(guesses:Iterable[Inference]) extends LogisticMaxLoss(guesses, TruthValue.TRUE) {
    override def apply(wVector:Array[Double]):Double = {
      // Debugging output
      def recursivePrint(node:Inference):String = {
        if (node.hasImpliedFrom) {
          val incomingType = EdgeType.values.find( _.id == node.getIncomingEdgeType).getOrElse(node.getIncomingEdgeType)
          if (incomingType != 63) {
            s"${node.getFact.getGloss} -[$incomingType]-> ${recursivePrint(node.getImpliedFrom)}"
          } else {
            recursivePrint(node.getImpliedFrom)
          }
        } else {
          s"${node.getFact.getGloss}"
        }
      }
      for (inference <- guesses) {
        val prob = new LogisticMaxLoss(List(inference), TruthValue.TRUE).apply(wVector)
        assert(!prob.isNaN)
        log({
          if (prob >= 0.5) "p(true)=" + prob + ": "
          else "p(true)=" + prob + ": "
        } + recursivePrint(inference))
      }
      // Compute score
      val prob = super.apply(wVector)
      assert(!prob.isNaN)
      if (guess.isDefined && math.abs(prob - guess.get.getScore) > 1e-5) {
        log(YELLOW, "Client score disagreed with server; client=" + prob + "; server=" + guess.get.getScore)
      }
      prob
    }
  }

  /**
   * Learn a model.
   */
  def main(args:Array[String]):Unit = {
    // Initialize Options
    if (args.length == 1) {
      val props:Properties = new Properties
      val config:Config = ConfigFactory.parseFile(new File(args(0))).resolve()
      for ( entry <- config.entrySet() ) {
        props.setProperty(entry.getKey, entry.getValue.unwrapped.toString)
      }
      Execution.fillOptions(classOf[Props], props)
    } else {
      Execution.fillOptions(classOf[Props], args)
    }

    // Initialize Data
    val data:DataStream = FraCaS.read(Props.DATA_FRACAS_PATH.getPath)

    // Initialize Optimization
    val initialWeights:Array[Double] = NatLog.softNatlogWeights
    val optimizer:OnlineOptimization = new OnlineOptimization(initialWeights, OnlineRegularizer.sgd(Props.LEARN_SGD_NU))

    // Learn
    var iter = data.iterator
    for (iteration <- 1 to Props.LEARN_ITERATIONS) {
      if (!iter.hasNext) { iter = data.iterator }
      val (query, gold) = iter.next()

      val guess = issueQuery(query
        .setUseRealWorld(false)
        .setTimeout(Props.SEARCH_TIMEOUT)
        .setCosts(Learn.weightsToCosts(optimizer.weights))
        .setSearchType("ucs")
        .setCacheType("bloom").build())

      optimizer.update(new LogisticMaxLoss(guess, gold))
    }

    // Save Model
    serialize(optimizer.weights, Props.LEARN_MODEL)
  }
}



