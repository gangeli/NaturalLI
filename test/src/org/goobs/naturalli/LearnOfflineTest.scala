package org.goobs.naturalli

import org.goobs.naturalli.LearnOffline.{ObjectiveFunction, UnregularizedObjectiveFunction}
import org.goobs.naturalli.Learn.WeightVector
import edu.stanford.nlp.stats.ClassicCounter
import java.util.Random
import edu.stanford.nlp.optimization.CGMinimizer

/**
 * Tests for offline learning (e.g., gradient check)
 *
 * @author gabor
 */
class LearnOfflineTest extends Test with Client {
  import Implicits.flattenWeights
  def sPlus(x:Double) = 1.0 / (1.0 + math.exp(x))
  def sMinus(x:Double) = 1.0 / (1.0 + math.exp(-x))

  def weights:WeightVector = {
    val v = new ClassicCounter[String]()
    v.incrementCount(Evaluate.monoUp_stateTrue(EdgeType.WORDNET_UP), -1.0)
    v.incrementCount(Evaluate.monoUp_stateTrue(EdgeType.WORDNET_DOWN), -5.0)
    v.setDefaultReturnValue(-0.1)
    v
  }

  def catToFeline:WeightVector = {
    val v = new ClassicCounter[String]()
    v.incrementCount(Evaluate.monoUp_stateTrue(EdgeType.WORDNET_UP))
    v
  }

  def felineToCat:WeightVector = {
    val v = new ClassicCounter[String]()
    v.incrementCount(Evaluate.monoUp_stateTrue(EdgeType.WORDNET_DOWN))
    v
  }

  describe ("The Unregularized Objective Function") {

    it ("should have the right value") {
      val fn = new UnregularizedObjectiveFunction(List(true, false), List(catToFeline, felineToCat))
      val value = fn.valueAt(weights)
      value should be ( -(math.log(0.5 + sPlus(1)) + math.log(0.5 - sPlus(5))) +- 1e-10 )
    }

    it ("should pass gradient check") {
      val fn = new UnregularizedObjectiveFunction(List(true, false), List(catToFeline, felineToCat))
      fn.gradientCheck() should be (right = true)
    }

    it ("should pass a random gradient check") {
      val rand = new Random
      val truthValues: Seq[Boolean] = (0 until 1000).map{ x => rand.nextBoolean() }
      val features: Seq[Array[Double]] = (0 until 1000).map{ x =>
        val init:Array[Double] = new Array[Double](Implicits.flattenWeights(NatLog.softNatlogWeights).length)
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init
      }
      val fn = new UnregularizedObjectiveFunction(truthValues, features)
      fn.gradientCheck() should be (right = true)
    }

  }

  describe ("The Regularized Objective Function") {
    it ("should pass gradient check") {
      val fn = new ObjectiveFunction(List(true, false), List(catToFeline, felineToCat), Props.LEARN_OFFLINE_SIGMA, 1e-5)
      fn.gradientCheck() should be (right = true)
    }

    it ("should pass a random gradient check") {
      val rand = new Random
      val truthValues: Seq[Boolean] = (0 until 1000).map{ x => rand.nextBoolean() }
      val features: Seq[Array[Double]] = (0 until 1000).map{ x =>
        val init:Array[Double] = new Array[Double](Implicits.flattenWeights(NatLog.softNatlogWeights).length)
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init
      }

      val fn = new ObjectiveFunction(truthValues, features, 1.0, 1e-3)
//      fn.gradientCheck() should be (right = true)
      fn.gradientCheck(100, 50, NatLog.softNatlogWeights) should be (right = true)
    }

    it ("should minimize with CG Minimizer") {
      val rand = new Random
      val truthValues: Seq[Boolean] = (0 until 1000).map{ x => rand.nextBoolean() }
      val features: Seq[Array[Double]] = (0 until 1000).map{ x =>
        val init:Array[Double] = new Array[Double](Implicits.flattenWeights(NatLog.softNatlogWeights).length)
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init(rand.nextInt(init.length)) = 1.0
        init
      }

      val fn = new ObjectiveFunction(truthValues, features, 1.0, 1e-3)
      val initialValue = fn.valueAt(NatLog.softNatlogWeights)
      val optimizer = new CGMinimizer()
      val w: Array[Double] = optimizer.minimize(fn, 1e-2, NatLog.softNatlogWeights)
      for (i <- Implicits.searchFeatureStart until fn.domainDimension) {
        w(i) should be < 0.0
      }
      fn.valueAt(w) should be < initialValue
    }
  }

}
