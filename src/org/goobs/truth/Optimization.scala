package org.goobs.truth

trait OnlineRegularizer {
  def nu(iteration:Int, sumSquaredGradient:Array[Double], keys:Iterable[Int]):Map[Int,Double]
}

object OnlineRegularizer {
  def l2_fixed(nuValue:Double) = new OnlineRegularizer {
    override def nu(iteration: Int, sumSquaredGradient: Array[Double], keys: Iterable[Int]): Map[Int, Double] = keys.map( (xi:Int) => (xi, nuValue) ).toMap
  }
  def sgd(nuValue:Double) = l2_fixed(nuValue)
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

  def prediction(wVector:Array[Double]):Double
  def gold:Double
  def feature(i:Int):Double
}

trait LogisticConfidenceLoss extends LossFunction {
  override def apply(wVector: Array[Double]): Double = {
    val m = prediction(wVector) * gold
    math.log( 2.0 * (1 + math.exp(-m)) / (predictionPolarity + (1 + math.exp(-m))) )
  }

  override def gradient(wVector: Array[Double]): Map[Int, Double] = {
    val m = prediction(wVector) * gold
    val constant = (-2 * predictionPolarity * math.exp(-m)) / ((1 + predictionPolarity + math.exp(-m)) * (1 + predictionPolarity + math.exp(-m)))
    (for (i <- 0 until wVector.length) yield {
      (i, constant * feature(i))
    }).toMap
  }

  def prediction(wVector:Array[Double]):Double
  def predictionPolarity:Double
  def gold:Double
  def feature(i:Int):Double
}

/**
 * A general online optimization framework
 *
 * @author gabor
 */
class OnlineOptimization(var weights:Array[Double], val regularizer:OnlineRegularizer) {

  // State
  private val sumSquaredGradient: Array[Double] = weights.map(_ => 1.0)
  private var iteration:Int = 0
  private var sumRegret:Double = 0.0

  def update(lossFn:LossFunction):Unit = synchronized {
    // Evaluate the loss
    val loss:Double = lossFn(weights)
    // Compute update
    if (loss != 0.0) {
      val gradients: Map[Int, Double] = lossFn.gradient(weights)
      val nu: Map[Int, Double] = regularizer.nu(iteration, sumSquaredGradient, gradients.keys)
      for ( (xi, grad) <- gradients ) {
        assert (!grad.isNaN && !grad.isInfinite)
        weights(xi) -= nu(xi) * grad
        sumSquaredGradient(xi) += (grad * grad)
      }
    }
    // Update metadata
    sumRegret += loss
    iteration += 1
  }

  def averageRegret:Double = sumRegret / iteration.toDouble

}
