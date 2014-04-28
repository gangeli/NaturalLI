package org.goobs.truth

import edu.stanford.nlp.stats.Counter
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.EdgeType.EdgeType
import org.goobs.truth.Messages._


object Learn {
  type WeightVector = Counter[String]

  def monoUp_stateTrue(edge:EdgeType):String   = "^,T" + "::" + edge
  def monoDown_stateTrue(edge:EdgeType):String = "v,T" + "::" + edge
  def monoFlat_stateTrue(edge:EdgeType):String = "-,T" + "::" + edge
  def monoAny_stateTrue(edge:EdgeType):String  = "*,T" + "::" + edge

  def monoUp_stateFalse(edge:EdgeType):String   = "^,F" + "::" + edge
  def monoDown_stateFalse(edge:EdgeType):String = "v,F" + "::" + edge
  def monoFlat_stateFalse(edge:EdgeType):String = "-,F" + "::" + edge
  def monoAny_stateFalse(edge:EdgeType):String  = "*,F" + "::" + edge

  /**
   * Serialize a weight vector into a protobuf to send to the server.
   * Note that the semantics of the serialized weights is the negative of the weights
   * used on the client.
   *
   * @param weights The weight vector to serialize
   * @return A protobuf message encoding the <b>cost</b> of each weight
   */
  def weightsToCosts(weights:WeightVector):Costs = {
    // Function to set the unlexicalized weights
    def unlexicalizedWeights(stateTrue:EdgeType=>String, stateFalse:EdgeType=>String):UnlexicalizedCosts = {
      val builder = UnlexicalizedCosts.newBuilder()
      for (from <- EdgeType.values.toArray.sortWith({ (a:EdgeType.Value, b:EdgeType.Value) => a.id < b.id })) {
        val trueWeight = weights.getCount(stateTrue(from)).toFloat
        val falseWeight = weights.getCount(stateFalse(from)).toFloat
        assert(trueWeight <= 0, "trueWeight=" + trueWeight)
        assert(falseWeight <= 0, "falseWeight=" + falseWeight)
        builder.addTrueCost(-trueWeight)
        builder.addFalseCost(-falseWeight)
      }
      builder.build()
    }

    Costs.newBuilder()
      .setUnlexicalizedMonotoneUp(unlexicalizedWeights(monoUp_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneDown(unlexicalizedWeights(monoDown_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneFlat(unlexicalizedWeights(monoFlat_stateTrue, monoFlat_stateFalse))
      .setUnlexicalizedMonotoneAny(unlexicalizedWeights(monoAny_stateTrue, monoAny_stateFalse))
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
      // TODO(gabor) compute a more real score (e.g., post-featurizers on the path)
      node.getState match {
        case Messages.CollapsedInferenceState.TRUE => node.getScore
        case Messages.CollapsedInferenceState.FALSE => 1.0 - node.getScore
        case Messages.CollapsedInferenceState.UNKNOWN => 0.5
      }
    }
    // Special case for no paths
    if (paths.isEmpty) { return 0.5 }
    // Compute noisy or of probabilities
    1.0 - {for (inference <- paths) yield {
      val prob = computeProb(inference)
      log({
        if (prob >= 0.5) "p(true)=" + prob + ": "
        else "p(true)=" + prob + ": "
        } + recursivePrint(inference))
      prob
    }}.foldLeft(1.0){ case (orInverse:Double, prob:Double) => orInverse * (1.0 - prob) }
  }
}



