package org.goobs.truth

import org.goobs.truth.TruthValue.TruthValue

import scala.collection.JavaConversions._

/**
 * Test various sources of training / test data.
 *
 * @author gabor
 */
class TestDataSources extends Test {

  describe("FraCaS") {
    Props.NATLOG_INDEXER_LAZY = false
    it ("should load from file") {
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).size should be (334)
    }
    it ("should have valid facts") {
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).foreach{ case (query:Messages.QueryOrBuilder, truth:TruthValue) =>
        // Ensure all the facts have been indexed successfully
        query.getKnownFactCount should be > 0
        query.getKnownFactList.foreach{ (antecedent:Messages.Fact) =>
          antecedent.getWordCount should be > 0
        }
        query.getQueryFact.getWordCount should be > 0
      }
    }

    it ("should have the correct single-antecedent dataset") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isSingleAntecedent )
      data.size should be (183)
      data.forall( _._1.getKnownFactCount == 1) should be (right = true)
    }
    it ("should have the correct 'applicable' dataset (according to McCartney)") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isApplicable )
      data.size should be (75)
      data.forall( _._1.getKnownFactCount == 1) should be (right = true)
    }

    it ("should match expected monotonicity markings on a subset of queries") {
      import Messages.Monotonicity._
      // There was an Italian who became the world 's greatest tenor
      // TODO(gabor) I actually contest the FLAT mark here?
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).head._1.getQueryFact.getWordList.map( _.getMonotonicity ).toList should be (
        List(UP, UP, UP, UP, UP, UP, FLAT, FLAT, FLAT, FLAT, FLAT))
    }
  }

}
