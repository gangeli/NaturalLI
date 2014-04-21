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
   * Note that the semantics of the serialized weights is different in at least
   * two ways:
   *
   * <ol>
   *   <li> The weights are now costs -- they are the negative of the passed in weights</li>
   *   <li> The up and down edges are reversed, as the search is performing reverse inference.
   * <ol>
   *
   * @param weights The weight vector to serialize
   * @return A protobuf message encoding the <b>cost</b> of each weight
   */
  def weightsToCosts(weights:WeightVector):Weights = {
    // Function to set the unlexicalized weights
    def unlexicalizedWeights(unigram:EdgeType=>String, bigram:(EdgeType,EdgeType)=>String):UnlexicalizedWeights = {
      val builder = UnlexicalizedWeights.newBuilder()
      for (from <- EdgeType.values.toArray.sortWith({ (a:EdgeType.Value, b:EdgeType.Value) => a.id < b.id })) {
        builder.addEdgeWeight(-weights.getCount(unigram(from)).toFloat)
        for (to <- EdgeType.values.toArray.sortWith({ (a:EdgeType.Value, b:EdgeType.Value) => a.id < b.id })) {
          builder.addEdgePairWeight(-weights.getCount(bigram(from, to)).toFloat)
        }
      }
      builder.build()
    }

    // Note: flip up and down! The inference inversion happens here.
    Weights.newBuilder()
      .setUnlexicalizedMonotoneDown(unlexicalizedWeights(unigramUp, bigramUp))
      .setUnlexicalizedMonotoneUp(unlexicalizedWeights(unigramDown, bigramDown))
      .setUnlexicalizedMonotoneFlat(unlexicalizedWeights(unigramFlat, bigramFlat))
      .setUnlexicalizedMonotoneAny(unlexicalizedWeights(unigramAny, bigramAny))
      .build()
  }

  def evaluate(paths:Iterable[Inference], weights:WeightVector):Double = {
    def recursivePrint(node:Inference):String = {
      if (node.hasImpliedFrom) {
        s"${node.getFact.getGloss} <-[${EdgeType.values.find( _.id == node.getIncomingEdgeType).getOrElse(node.getIncomingEdgeType)}]- ${recursivePrint(node.getImpliedFrom)}"
      } else {
        s"<${node.getFact.getGloss}>"
      }
    }
    def computeProb(node:Inference):Double = {
      // TODO(gabor) compute a more real score
      node.getScore
    }
    // Special case for no paths
    if (paths.isEmpty) { return 0.5 }
    // Compute noisy or of probabilities
    1.0 - {for (inference <- paths) yield {
      val prob = computeProb(inference);
      log({
        if (prob >= 0.5) "\033[32mp(true)=" + prob + "\033[0m : " 
        else "\033[33mp(true)=" + prob + "\033[0m : " 
        } + recursivePrint(inference))
      prob
    }}.foldLeft(1.0){ case (orInverse:Double, prob:Double) => orInverse * (1.0 - prob) }
  }
}



