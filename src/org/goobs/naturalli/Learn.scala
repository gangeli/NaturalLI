package org.goobs.naturalli

import java.io.{PrintWriter, File}
import edu.stanford.nlp.stats.{Counter, ClassicCounter, Counters}
import org.goobs.naturalli.Messages.{UnlexicalizedCosts, Costs, Inference}
import org.goobs.naturalli.EdgeType.EdgeType
import scala.collection.JavaConversions._

object Learn extends Client {
  type WeightVector = Counter[String]

  /** Write a weight vector to a print writer (usually, a file). */
  def serialize(w:WeightVector, p:PrintWriter):Unit = {
    for ( (key, value) <- Counters.asMap(w) ) {
      p.println(key + "\t" + value)
    }
  }

  /** Write a weight vector to a file. Returns the same file that we wrote to */
  def serialize(w:WeightVector, f:File):File = { Utils.printToFile(f)( Learn.serialize(w, _) ); f }

  /** Read a weight vector from a list of entries (usually, lines of a file) */
  def deserialize(lines:Seq[String]):WeightVector = {
    lines.foldLeft(new ClassicCounter[String]){ case (w:ClassicCounter[String], line:String) =>
      val fields = line.split("\t")
      w.setCount(fields(0), fields(1).toDouble)
      w
    }
  }

  /** Read a weight vector from a file */
  def deserialize(f:File):WeightVector = deserialize(io.Source.fromFile(f).getLines().toArray)

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

    import Evaluate._
    Costs.newBuilder()
      .setUnlexicalizedMonotoneUp(unlexicalizedWeights(monoUp_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneDown(unlexicalizedWeights(monoDown_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneFlat(unlexicalizedWeights(monoFlat_stateTrue, monoFlat_stateFalse))
      .setUnlexicalizedMonotoneAny(unlexicalizedWeights(monoAny_stateTrue, monoAny_stateFalse))
      .setBias(weights.getCount("bias").toFloat)
      .build()
  }

  /** @see Learn#recursivePrint(Inference) */
  def recursivePrint(node:Option[Inference]):String = node match {
    case Some(n:Inference) => recursivePrint(n)
    case None => "<no paths>"
  }

  /** Parse the initialization proprty from Props */
  def initialization:WeightVector = {
    Props.LEARN_INITIALIZATION match {
      case Props.Initialization.HARD_WEIGHTS => NatLog.hardNatlogWeights
      case Props.Initialization.SOFT_WEIGHTS => NatLog.softNatlogWeights
      case Props.Initialization.UNIFORM => {
        val weights = new ClassicCounter[String]
        weights.setDefaultReturnValue(-1.0)
        weights
      }
    }

  }

}
