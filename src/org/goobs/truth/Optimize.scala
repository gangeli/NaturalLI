package org.goobs.truth

import scala.collection.GenSeq
import java.io.{PrintWriter, File}

trait OnlineRegularizer {
  def nu(iteration:Int, sumSquaredGradient:Array[Double], keys:Iterable[Int]):Map[Int,Double]
}

object OnlineRegularizer {
  def l2_fixed(nuValue:Double) = new OnlineRegularizer {
    override def nu(iteration: Int, sumSquaredGradient: Array[Double], keys: Iterable[Int]): Map[Int, Double] = keys.map( (xi:Int) => (xi, nuValue) ).toMap
  }
  def l2_inverseDecay(nuValue:Double) = new OnlineRegularizer {
    override def nu(iteration: Int, sumSquaredGradient: Array[Double], keys: Iterable[Int]): Map[Int, Double] = keys.map( (xi:Int) => (xi, nuValue / (1 + iteration).toDouble) ).toMap
  }
  def l2_inverseSqrtDecay(nuValue:Double) = new OnlineRegularizer {
    override def nu(iteration: Int, sumSquaredGradient: Array[Double], keys: Iterable[Int]): Map[Int, Double] = keys.map( (xi:Int) => (xi, nuValue / math.sqrt((1 + iteration).toDouble)) ).toMap
  }
  def sgd(nuValue:Double) = l2_inverseSqrtDecay(nuValue)
}

trait LossFunction extends ((Array[Double]) => Double) {
  def gradient(x:Array[Double]):Map[Int,Double]
}

trait LogisticLoss extends LossFunction {
  override def apply(wVector: Array[Double]): Double = math.log(1.0 + math.exp(-prediction(wVector) * gold))

  override def gradient(wVector: Array[Double]): Map[Int, Double] = {
    val constant = - gold * 1.0 / (1.0 + math.exp(prediction(wVector) * gold))
    (for (i <- 0 until wVector.length) yield {
      (i, constant * feature(i))
    }).toMap
  }

  def probability(wVector:Array[Double]):Double = 1.0 / (1.0 + math.exp(-prediction(wVector)))

  def prediction(wVector:Array[Double]):Double
  def gold:Double
  def feature(i:Int):Double
}

trait SquaredLoss extends LossFunction {
  override def apply(wVector: Array[Double]): Double = {
    val diff = gold  - guess(wVector)
    0.5 * diff * diff
  }

  override def gradient(wVector: Array[Double]): Map[Int, Double] = {
    val diff = gold  - guess(wVector)
    val dw = guessGradient(wVector)
    (for (i <- 0 until wVector.length) yield {
      (i, diff * -1 * dw(i))
    }).toMap
  }

  def gold:Double
  def guess(wVector:Array[Double]):Double
  def guessGradient(wVector:Array[Double]):Array[Double]
}

class AlwaysWrongLoss extends LossFunction {
  override def gradient(x: Array[Double]): Map[Int, Double] = throw new RuntimeException("Cannot take the derivative of the AlwaysWrongLoss")
  override def apply(v1: Array[Double]): Double = 1.0
}

trait ZeroOneLoss extends LossFunction {
  override def gradient(x: Array[Double]): Map[Int, Double] = throw new RuntimeException("Cannot take the derivative of the AlwaysWrongLoss")
  override def apply(v1: Array[Double]): Double = if (isCorrect) 0.0 else 1.0
  def isCorrect:Boolean
}

object OnlineOptimizer {
  def deserialize(f:File, regularizer:OnlineRegularizer,
                  project:(Int,Double)=>Double = (i:Int, w:Double) => w):OnlineOptimizer = {
    import Learn.flattenWeights
    // Parse file
    val (state, weightLines) = io.Source.fromFile(f).getLines().toList.splitAt(4)
    // Parse parameters
    val weights:Array[Double] = Learn.deserialize(weightLines)
    val iteration = state(0).toInt
    val sumRegret = state(1).toDouble
    val sumSquaredGradient = state(2).split("\\s+").map( _.toDouble )
    val performanceRegret = state(3).toDouble
    // Create optimizer
    new OnlineOptimizer(
      weights=weights,
      regularizer=regularizer,
      iteration=iteration,
      sumRegret=sumRegret,
      sumSquaredGradient=sumSquaredGradient,
      performanceRegret=performanceRegret,
      project=project)
  }

  def apply(weights:Array[Double], regularizer:OnlineRegularizer)(
      // For resuming from a saved state
      iteration:Int = 0, sumRegret:Double = 0.0,
      sumSquaredGradient:Array[Double] = weights.map( _ => 1.0 ),
      performanceRegret:Double = 0.0,
      // Tweaks
      project:(Int,Double)=>Double = (i:Int, w:Double) => w):OnlineOptimizer = {
    new OnlineOptimizer(weights, regularizer,
      iteration=iteration,
      sumRegret=sumRegret,
      sumSquaredGradient=sumSquaredGradient,
      performanceRegret=performanceRegret,
      project=project)
  }
}

/**
 * A general online optimization framework.
 *
 * @author gabor
 */
class OnlineOptimizer(
                      // Required variables
                      val weights:Array[Double], regularizer:OnlineRegularizer,
                      // For resuming from a saved state
                      private var iteration:Int, private var sumRegret:Double,
                      sumSquaredGradient:Array[Double],
                      private var performanceRegret:Double,
                      // Tweaks
                      project:(Int,Double)=>Double) {


  /**
   * Do a stochastic gradient update.
   * @param lossFn The loss function to update on.
   * @return The loss incured from this update (i.e., the loss function on the current weights)
   */
  def update(lossFn:LossFunction, performance:LossFunction = new AlwaysWrongLoss):Double = synchronized {
    // Evaluate the loss
    val loss:Double = lossFn(weights)
    // Compute update
    if (loss != 0.0) {
      val gradients: Map[Int, Double] = lossFn.gradient(weights)
      val nu: Map[Int, Double] = regularizer.nu(iteration, sumSquaredGradient, gradients.keys)
      for ( (xi, grad) <- gradients ) {
        assert (!grad.isNaN && !grad.isInfinite)
        assert (!weights(xi).isNaN && !weights(xi).isInfinite)
        assert (!nu(xi).isNaN && !nu(xi).isInfinite)
        val update = project(xi, weights(xi) - nu(xi) * grad)
        assert (!update.isNaN && !update.isInfinite)
        weights(xi) = update
        assert (!weights(xi).isNaN && !weights(xi).isInfinite)
        sumSquaredGradient(xi) += (grad * grad)
      }
    }
    // Update metadata
    sumRegret += loss
    performanceRegret += performance(weights)
    iteration += 1
    loss
  }

  def averageRegret:Double = if (iteration == 0) 0.0 else sumRegret / iteration.toDouble
  def averagePerformance:Double = if (iteration == 0) 0.0 else performanceRegret / iteration.toDouble

  def error(losses:GenSeq[_ <: LossFunction]):Double = {
    val (loss, count) = losses.foldLeft((0.0, 0)){case ((soFar:Double, count:Int), loss:LossFunction) => (soFar + loss(weights), count + 1) }
    loss / count.toDouble
  }

  def serializePartial(f:File):File = {
    import Learn.inflateWeights
    Utils.printToFile(f){ (p:PrintWriter) =>
      p.println(iteration.toString)
      p.println(sumRegret.toString)
      p.println(sumSquaredGradient.mkString("\t"))
      p.println(performanceRegret.toString)
      Learn.serialize(weights, p)
    }
    f
  }
}
