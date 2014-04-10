package org.goobs.truth

import org.goobs.truth.TruthValue.TruthValue

import scala.collection.JavaConversions._

/**
 * Test various sources of training / test data.
 *
 * @author gabor
 */
class TestDataSources extends Test {
  Props.NATLOG_INDEXER_LAZY = false

  describe("FraCaS") {
    it ("should load from file") {
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).size should be (346)
    }
    it ("should have valid facts") {
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).foreach{ case (query:Messages.QueryOrBuilder, truth:TruthValue) =>
        // Ensure all the facts have been indexed successfully
        query.getKnownFactList.foreach{ (antecedent:Messages.Fact) =>
          antecedent.getWordCount should be > 0
        }
        query.getQueryFact.getWordCount should be > 0
      }
    }

    ignore ("should match expected monotonicity markings") {
      // TODO(gabor)
    }
  }

}
