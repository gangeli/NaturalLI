package org.goobs.truth

import java.util.Properties

import scala.language.implicitConversions
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import org.goobs.truth.Learn.WeightVector
import edu.stanford.nlp.stats.ClassicCounter
import org.goobs.truth.Messages.Monotonicity

/**
 * Implicit definitions to help with the Java/Scala interface
 *
 * @author Gabor Angeli
 */
object Implicits {

  class Fn1(fn:()=>Unit) extends edu.stanford.nlp.util.Function[Properties, AnyRef] {
    def apply(in: Properties): Object = { fn(); None }
  }
  class Fn2(fn:Properties=>Unit) extends edu.stanford.nlp.util.Function[Properties, AnyRef] {
    def apply(in: Properties): Object = { fn(in); None }
  }

  implicit def fn2execInput1(fn:()=>Unit):edu.stanford.nlp.util.Function[Properties, AnyRef] = new Fn1(fn)
  implicit def fn2execInput2(fn:Properties=>Unit):edu.stanford.nlp.util.Function[Properties, AnyRef] = new Fn2(fn)
  
  implicit class Regex(sc: StringContext) {
    def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }

  /** Implicit to convert from a weight counter to a weight vector */
  implicit def flattenWeights(w:WeightVector):Array[Double] = {
    (w.getCount("bias") :: w.getCount("resultCount") ::
      (for (e <- EdgeType.values.toList;
            truth <- Array[Boolean](true, false);
            mono <- Monotonicity.values().toArray) yield {
        w.getCount(Evaluate.feature(e, truth, mono))
      })).toArray
  }

  /** The first index of features that must be < 0 */
  val searchFeatureStart = 2

  /** Implicit to convert from a weight vector to a weight counter */
  def inflateWeights(w:Array[Double], ignoreValue:Double):WeightVector = {
    val feats = new ClassicCounter[String]
    if (w(0) != ignoreValue) { feats.incrementCount("bias", w(0)) }
    if (w(1) != ignoreValue) { feats.incrementCount("resultCount", w(1)) }
    var i:Int = 2
    for (e <- EdgeType.values.toList;
         truth <- Array[Boolean](true, false);
         mono <- Monotonicity.values().toArray) {
      if (w(i) != ignoreValue) { feats.setCount(Evaluate.feature(e, truth, mono), w(i)) }
      i += 1
    }
    feats
  }
  implicit def inflateWeights(w:Array[Double]):WeightVector = inflateWeights(w, 0.0)
}
