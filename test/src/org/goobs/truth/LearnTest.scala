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
    it ("should have an Array mapping with resultCount") {
      val w = NatLog.softNatlogWeights
      w.setCount("resultCount", 10.5)
      counterString(Learn.inflateWeights(Learn.flattenWeights(w), w.defaultReturnValue())) should be (counterString(w))
    }
  }

  /**
   * Sanity check the loss functions
   */
  describe ("Among the loss functions") {
    /**
     * The actual gradient checker
     * @param loss A function to generate a loss function. The parameters are:
     *             <ol>
     *               <li> The gold truth value
     *               <li> The first element of the feature vector
     *               <li> The second element of the feature vector
     *             </ol>
     */
    def gradientCheck(loss:(Double, Double) => LossFunction) {
      val delta = 0.001
      for (x0 <- -1.0 until 1.0 by 0.1;
           x1 <- -1.0 until 1.0 by 0.1) {
        val w = Array(x0, x1)
        val value = loss(2.0, -5.0)(w)
        val grad: Map[Int, Double] = loss(2.0, -5.0).gradient(w)
        for ((i, dw) <- grad) {
          val nearW = w.map( x => x )
          nearW(i) += delta
          val nearValue = loss(2.0, -5.0)(nearW)
          val tolerance:Double = (delta * math.abs(dw)) / 10.0 + 1e-10
          (value + dw * delta) should be (nearValue +- tolerance)
        }
      }
    }

    /**
     * The Logistic Loss
     */
    describe ("the Logistic loss") {
      def loss(goldValue:Boolean, x0:Double, x1:Double) = new LogisticLoss {
        val features = Array(x0, x1)
        override def feature(i: Int): Double = features(i)
        override def prediction(wVector: Array[Double]): Double = wVector(0) * features(0) + wVector(1) * features(1)
        override def gold: Double = if (goldValue) 1.0 else -1.0
      }
      it ("should be the log inverse probability") {
        val w = Array(3.0, 4.0)
        val prob = 1.0 / (1.0 + math.exp(-(w(0) * 2.0 + w(1) * -5.0)))
        loss(goldValue = true, 2.0, -5.0)(w) should be (math.log(1.0 / prob) +- 1e-5)
      }
      it ("should match my math") {
        val w = Array(3.0, 4.0)
        loss(goldValue = true, 2.0, -5.0)(w) should be (14.0 +- 1e-5)
        loss(goldValue = true, 2.0, -1.0)(w) should be (0.12692801104 +- 1e-5)
      }
      it ("should be monotone") {
        val losses:Seq[Double]
        = for (w0 <- -100.0 to 10.0 by 0.01)
        yield loss(goldValue = true, 2.0, -5.0)(Array(w0, 1.0))
        losses.length should be > 1000
        losses.last should be (0.0 +- 0.01)
        for ((last, current) <- losses.zip(losses.drop(1))) {
          last should be >= 0.0
          current should be >= 0.0
          current should be <= last
        }
      }
      it ("should pass gradient check") {
        gradientCheck(loss(true,_,_))
        gradientCheck(loss(false,_,_))
      }
    }

    /**
     * The Squared Loss
     */
    describe ("the Squared loss") {
      def loss(goldValue:Double, x0:Double, x1:Double) = new SquaredLoss {
        val features = Array(x0, x1)
        override def gold: Double = goldValue
        override def guess(wVector: Array[Double]): Double = {
          1.0 / (1.0 + math.exp(-(features(0) * wVector(0) + features(1) * wVector(1))))
        }
        override def guessGradient(wVector: Array[Double]): Array[Double] = {
          val sigmoid = guess(wVector)
          val constant = sigmoid * (1.0 - sigmoid)
          features.map( x => constant * x )
        }
      }
      it ("should match my math") {
        val w = Array(3.0, 4.0)
        loss(0.0, 2.0, -2.0)(w) should be (0.0071046683 +- 1e-5)
        loss(1.0, 2.0, -2.0)(w) should be (0.38790174628 +- 1e-5)
        loss(0.0, 2.0, -1.0)(w) should be (0.38790174628 +- 1e-5)
        loss(1.0, 2.0, -1.0)(w) should be (0.0071046683 +- 1e-5)
      }
      it ("should be monotone") {
        val losses:Seq[Double]
        = for (w0 <- -100.0 to 10.0 by 0.01)
        yield loss(1.0, 2.0, -5.0)(Array(w0, 1.0))
        losses.length should be > 1000
        losses.last should be (0.0 +- 0.01)
        for ((last, current) <- losses.zip(losses.drop(1))) {
          last should be >= 0.0
          current should be >= 0.0
          current should be <= last
        }
      }
      it ("should pass gradient check") {
        gradientCheck(loss(0.0,_,_))
        gradientCheck(loss(1.0,_,_))
      }
      it ("should be usable for training") {
        val loss = new SquaredLoss {
          override def gold: Double = 2.5
          override def guess(wVector: Array[Double]): Double = wVector(0) * 2.5
          override def guessGradient(wVector: Array[Double]): Array[Double] = Array[Double]( 2.5 )
        }
        val optimizer:OnlineOptimizer = OnlineOptimizer(Array[Double](100.0), OnlineRegularizer.sgd(1.0))()
        for (i <- 0 until 100) {
          optimizer.update(loss)
        }
        optimizer.weights(0) should be (1.0 +- 1e-5)
      }
    }
  }

  describe("An online optimizer") {
    it ("should be serializable/deserializable") {
      val loss = new SquaredLoss {
        override def gold: Double = 2.5
        override def guess(wVector: Array[Double]): Double = wVector(0) * 2.5
        override def guessGradient(wVector: Array[Double]): Array[Double] = Array[Double]( 2.5 )
      }
      import Learn.flattenWeights
      val optimizer:OnlineOptimizer = OnlineOptimizer(NatLog.softNatlogWeights, OnlineRegularizer.sgd(1.0))()
      val tmpFile = File.createTempFile("optimizer", ".tab")
      val reread:OnlineOptimizer = OnlineOptimizer.deserialize(optimizer.serializePartial(tmpFile), OnlineRegularizer.sgd(1.0))
      optimizer.weights should be (reread.weights)
      optimizer.averageRegret should be (reread.averageRegret)
      optimizer.averagePerformance should be (reread.averagePerformance)

    }
  }
}
