package org.goobs.truth

import edu.stanford.nlp.stats.Counter
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.EdgeType.EdgeType
import org.goobs.truth.Messages.Inference


object Learn {
  type WeightVector = Counter[String]

  def unigramUp(edge:EdgeType):String   = "^" + "::" + edge
  def unigramDown(edge:EdgeType):String = "v" + "::" + edge
  def unigramFlat(edge:EdgeType):String = "-" + "::" + edge
  def unigramAny(edge:EdgeType):String  = "*" + "::" + edge
  def bigramUp(e1:EdgeType, e2:EdgeType):String    = "^" + "::" + e1 + "->" + e2
  def bigramDown(e1:EdgeType, e2:EdgeType):String  = "v" + "::" + e1 + "->" + e2
  def bigramFlat(e1:EdgeType, e2:EdgeType):String  = "-" + "::" + e1 + "->" + e2
  def bigramAny(e1:EdgeType, e2:EdgeType):String   = "*" + "::" + e1 + "->" + e2

  def evaluate(paths:Iterable[Inference], weights:WeightVector):Double = {
    for (inference <- paths) {
      log(inference.getFact.getGloss + " <- " + inference.getImpliedFrom.getFact.getGloss)
    }

    // TODO(gabor) an actual evaluation function
    if (paths.size > 0) 1.0 else 0.0
  }
}



