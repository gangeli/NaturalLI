package org.goobs.truth.scripts

import org.goobs.truth.Fact
import org.goobs.truth.Implicits._
import org.goobs.truth._

/**
 *
 * The entry point for constructing a graph between fact arguments.
 *
 * @author Gabor Angeli
 */

object CreateGraph {

  def facts:Iterator[Fact] = {
    null
  }

  def main(args:Array[String]) = {
    Props.exec(() => {
      println("Hello world!")
    }, args)

  }


}
