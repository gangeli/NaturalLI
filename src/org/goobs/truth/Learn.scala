package org.goobs.truth

import edu.stanford.nlp.stats.Counter
import org.goobs.truth.EdgeType.EdgeType


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
}



