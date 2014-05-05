package org.goobs.truth.scripts

import org.goobs.truth._
import org.goobs.truth.Messages._

import edu.stanford.nlp.util.logging.Redwood.Util._
import edu.stanford.nlp.util.Execution

/**
 * A command-line utility to check if a fact is true according to NaturalLI and the fact
 * database running on the server.
 * This requires a separate server running.
 *
 * @author gabor
 */
object Truth extends Client {
  def main(args:Array[String]):Unit = {
    Execution.fillOptions(classOf[Props], args)
    val weights = NatLog.hardNatlogWeights

    do {
      println("")
      val consequent:Fact = NatLog.annotate(readLine("query> "), x => Utils.WORD_UNK).head
      explain(consequent, "consequent")
      // We have our antecedent and consequent
      if (consequent.getWordCount > 0) {
        val query = Query.newBuilder()
          .setQueryFact(consequent)
          .setUseRealWorld(true)
          .setTimeout(1000000)
          .setCosts(Learn.weightsToCosts(weights))
          .setSearchType("ucs")
          .setCacheType("bloom")
          .build()
        // Execute Query
        val paths:Iterable[Inference] = issueQuery(query)
        // Evaluate Query
        val prob = Learn.evaluate(paths, weights)
        // Debug Print
        if (prob > 0.5) { println("\033[32mVALID\033[0m (p=" + prob + ")") } else { println("\033[31mINVALID\033[0m (p=" + prob + ")") }
      } else {
        err("No antecedent or consequent provided!")
      }
    } while (true)
  }

}
