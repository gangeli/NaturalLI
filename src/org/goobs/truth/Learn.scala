package org.goobs.truth

import edu.stanford.nlp.stats.Counter
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.EdgeType.EdgeType
import org.goobs.truth.Messages.{UnlexicalizedWeights, Weights, Inference}


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

  /**
   * Serialize a weight vector into a protobuf to send to the server.
   * @param weights The weight vector to serialize
   * @return A protobuf message encoding the <b>cost</b> of each weight
   */
  def weightsToCosts(weights:WeightVector):Weights = {
    // Function to set the unlexicalized weights
    def unlexicalizedWeights(unigram:EdgeType=>String, bigram:(EdgeType,EdgeType)=>String):UnlexicalizedWeights = {
      val builder = UnlexicalizedWeights.newBuilder()
      for (from <- EdgeType.values) {
        builder.addEdgeWeight(-weights.getCount(unigram(from)).toFloat)
        for (to <- EdgeType.values) {
          builder.addEdgeWeight(-weights.getCount(bigram(from, to)).toFloat)
        }
      }
      builder.build()
    }

    Weights.newBuilder()
      .setUnlexicalizedMonotoneUp(unlexicalizedWeights(unigramUp, bigramUp))
      .setUnlexicalizedMonotoneDown(unlexicalizedWeights(unigramUp, bigramUp))
      .setUnlexicalizedMonotoneFlat(unlexicalizedWeights(unigramFlat, bigramFlat))
      .setUnlexicalizedMonotoneAny(unlexicalizedWeights(unigramAny, bigramAny))
      .build()
  }

  def evaluate(paths:Iterable[Inference], weights:WeightVector):Double = {
    def recursivePrint(node:Inference):String = {
      if (node.hasImpliedFrom) {
        s"${node.getFact.getGloss} <- ${recursivePrint(node.getImpliedFrom)}"
      } else {
        s"<${node.getFact.getGloss}>"
      }
    }
    for (inference <- paths) {
      log(recursivePrint(inference))
    }

    // TODO(gabor) an actual evaluation function
    if (paths.size > 0) 1.0 else 0.0
  }
}



