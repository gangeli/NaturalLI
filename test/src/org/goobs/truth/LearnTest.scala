package org.goobs.truth

import java.io.File
import edu.stanford.nlp.stats.Counters
import org.goobs.truth.Learn.WeightVector

/**
 * Unit test various learning components.
 *
 * @author gabor
 */
class LearnTest extends Test {
  /**
   * Check the weight vector class
   */
  describe ("A weight vector") {
    def counterString(w:WeightVector) = Counters.toSortedByKeysString(w, "%2$f %1$s", "; ", "{%s}" )

    /** Make sure we can read and write it to a file */
    it ("should be serializable") {
      val tmpFile = File.createTempFile("model", ".tab")
      tmpFile.deleteOnExit()
      counterString(Learn.deserialize(Learn.serialize(NatLog.softNatlogWeights, tmpFile))) should be (counterString(NatLog.softNatlogWeights))
      counterString(Learn.deserialize(Learn.serialize(NatLog.hardNatlogWeights, tmpFile))) should be (counterString(NatLog.hardNatlogWeights))
    }
    it ("should have an Array mapping") {
      counterString(Learn.inflateWeights(Learn.flattenWeights(NatLog.hardNatlogWeights), NatLog.hardNatlogWeights.defaultReturnValue())) should be (counterString(NatLog.hardNatlogWeights))
      counterString(Learn.inflateWeights(Learn.flattenWeights(NatLog.softNatlogWeights), NatLog.softNatlogWeights.defaultReturnValue())) should be (counterString(NatLog.softNatlogWeights))
    }
    it ("should have an Array mapping with bias") {
      val w = NatLog.softNatlogWeights
      w.setCount("bias", 0.5)
      counterString(Learn.inflateWeights(Learn.flattenWeights(w), w.defaultReturnValue())) should be (counterString(w))
    }
  }

  /**
   * Sanity check the loss functions
   */
  describe ("The loss function") {
    /**
     * The actual gradient checker
     * @param loss A function to generate a loss function. The parameters are:
     *             <ol>
     *               <li> The gold truth value
     *               <li> The first element of the feature vector
     *               <li> The second element of the feature vector
     *             </ol>
     */
    def gradientCheck(loss:(Boolean,Double, Double) => LossFunction) {
      val delta = 0.001
      for (x0 <- -1.0 until 1.0 by 0.1;
           x1 <- -1.0 until 1.0 by 0.1) {
        val w = Array(x0, x1)
        val value = loss(true, 2.0, -5.0)(w)
        val grad: Map[Int, Double] = loss(true, 2.0, -5.0).gradient(w)
        for ((i, dw) <- grad) {
          val nearW = w.map( x => x )
          nearW(i) += delta
          val nearValue = loss(true, 2.0, -5.0)(nearW)
          val tolerance:Double = (delta * math.abs(dw)) / 10.0 + 1e-10
          (value + dw * delta) should be (nearValue +- tolerance)
        }
      }
    }

    /**
     * The Logistic Loss
     */
    describe ("Logistic loss") {
      def loss(goldValue:Boolean, x0:Double, x1:Double) = new LogisticLoss {
        val features = Array(x0, x1)
        override def feature(i: Int): Double = features(i)
        override def prediction(wVector: Array[Double]): Double = wVector(0) * features(0) + wVector(1) * features(1)
        override def gold: Double = if (goldValue) 1.0 else -1.0
      }
      it ("should match my math") {
        val w = Array(3.0, 4.0)
        loss(goldValue = true, 2.0, -5.0)(w) should be (14.0 +- 1e-5)
        loss(goldValue = true, 2.0, -1.0)(w) should be (0.12692801104 +- 1e-5)
      }
      it ("should pass gradient check") {
        gradientCheck(loss)
      }
    }
  }
}
