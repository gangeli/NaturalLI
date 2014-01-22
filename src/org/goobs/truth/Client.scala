package org.goobs.truth

import org.goobs.truth.Messages.{Inference, Query}

/**
 * @author Gabor Angeli
 */
object Client {

  /**
   * Issue a query to the search server, and block until it returns
   * @param query The query, with monotonicity markings set.
   * @return The query fact, with a truth value and confidence
   */
  def issueQuery(query:Query):Iterable[Inference] = {
    // TODO(gabor) implement me!
    List(Inference.newBuilder().setFact(query.getQueryFact).setImpliedFrom(Inference.newBuilder().setFact(query.getKnownFact(0)).build()).build())
  }

  def main(args:Array[String]):Unit = {
    val INPUT = """\s*\[([^\]]+)\]\s*\(([^,]+),\s*([^\)]+)\)\s*""".r
    val weights = NatLog.hardNatlogWeights


    while (true) {
      println()
      println("Enter an antecedent and a consequent ase \"[rel](arg1, arg2)\".")
      for (antecedent <- readLine("antecedent> ") match {
            case INPUT(rel, arg1, arg2) => Some(NatLog.annotate(arg1, rel, arg2))
            case _ => println("Could not parse antecedent"); None
          }) {
        for (consequent <- readLine("consequent> ") match {
          case INPUT(rel, arg1, arg2) => Some(NatLog.annotate(arg1, rel, arg2))
          case _ => println("Could not parse consequent"); None
        }) {
          // We have our antecedent and consequent
          val query = Query.newBuilder().setQueryFact(consequent).addKnownFact(antecedent).setUseRealWorld(false).build()
          // Execute Query
          val paths = issueQuery(query)
          // Evaluate Query
          val score = Learn.evaluate(paths, weights)
          // Debug Print
          if (score > 0.5) { println("Valid") } else { println("Invalid") }
        }
      }
    }
  }
}
