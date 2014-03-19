package org.goobs.truth

import org.goobs.truth.Messages.Fact
import org.goobs.truth.Messages.Query
import edu.stanford.nlp.io.IOUtils

/**
 * A regression test for the inference engine
 *
 * @author gabor
 */
object RegressionTest {

  def main(args:Array[String]) {
    Props.NATLOG_INDEXER_LAZY = true
    val INPUT = """\s*\[([^\]]+)\]\s*\(([^,]+),\s*([^\)]+)\)\s*""".r
    var exitStatus = 0

    val reader = IOUtils.getBufferedReaderFromClasspathOrFileSystem("org/goobs/truth/regressions_natlog")
    var line:String = reader.readLine()
    while ( line != null ) {
      if (!line.trim.equals("") && !line.trim.startsWith("#")) {
        // Parse Line
        val facts = line.split("\t").map {
          case INPUT(rel, subj, obj) => NatLog.annotate(subj, rel, obj)
          case _ => throw new IllegalArgumentException("Could not parse fact: " + line)
        }

        // Issue Query
        val score:Double = Learn.evaluate(Client.issueQuery(
          facts.slice(0, facts.length - 1).foldLeft(Query.newBuilder()){ case (builder, fact:Fact) =>
            builder.addKnownFact(fact)
          }.setQueryFact(facts.last)
            .setUseRealWorld(false)
            .setTimeout(Props.SEARCH_TIMEOUT)
            .setWeights(Learn.weightsToCosts(NatLog.hardNatlogWeights))
            .setSearchType("ucs")
            .setCacheType("bloom")
            .build()), NatLog.hardNatlogWeights)

        // Check Validity
        if (score < 0.5) {
          System.err.println("SHOULD BE VALID (score=" + score + ": " + line)
          exitStatus += 1
        }
      }

      line = reader.readLine()
    }

    System.exit(exitStatus)
  }

}
