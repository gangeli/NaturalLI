package org.goobs.truth.scripts

import org.goobs.truth._
import org.goobs.truth.Messages._

import edu.stanford.nlp.util.logging.Redwood.Util._

/**
 * A command line script to enter an antecedent and a consequent and see if they're entailed by the
 * NaturalLI entailment system.
 * This does not require an independent server running.
 *
 * @author gabor
 */
object Entailment extends Client {

  def main(args:Array[String]):Unit = {
    Props.NATLOG_INDEXER_LAZY = false
    Props.SERVER_MAIN_PORT = 4002
    val weights = NatLog.hardNatlogWeights

    startMockServer( () =>
      do {
        println("")
        println("Enter an antecedent and a consequent")
        val antecedent:Fact = NatLog.annotate(io.StdIn.readLine("antecedent> "), x => Utils.WORD_UNK).head
        explain(antecedent, "antecedent")
        val consequent:Fact = NatLog.annotate(io.StdIn.readLine("consequent> "), x => Utils.WORD_UNK).head
        explain(consequent, "consequent")
        // We have our antecedent and consequent
        if (antecedent.getWordCount > 0 && consequent.getWordCount > 0) {
          val query = Query.newBuilder()
            .setQueryFact(consequent)
            .addKnownFact(antecedent)
            .setUseRealWorld(false)
            .setTimeout(1000000)
            .setCosts(Learn.weightsToCosts(weights))
            .setSearchType("ucs")
            .setCacheType("bloom")
            .build()
          // Execute Query
          val paths:Iterable[Inference] = issueQuery(query)
          for (path <- paths) {
            log(recursivePrint(path))
          }
          // Evaluate Query
          import Implicits.flattenWeights
          val prob = probability(paths, weights)
          // Debug Print
          if (prob > 0.5) { println(Console.GREEN + "VALID" + Console.RESET + " (p=" + prob + ")") }
          else { println(Console.RED + "INVALID" + Console.RESET + " (p=" + prob + ")") }
        } else {
          err("No antecedent or consequent provided!")
        }
      } while (true),
      printOut = false)
  }

}
