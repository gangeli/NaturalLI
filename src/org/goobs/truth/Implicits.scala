package org.goobs.truth

import java.util.Properties

import scala.language.implicitConversions
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

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
}
