package org.goobs.truth

import org.goobs.truth.TruthValue.TruthValue

import scala.collection.JavaConversions._

/**
 * Test various sources of training / test data.
 *
 * @author gabor
 */
class TestDataSources extends Test {

  /**
   * Statistics for this test are taken from the statistics in
   * Section 4 of http://nlp.stanford.edu/~wcmac/papers/natlog-wtep07.pdf
   */
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

    it ("should have the correct total label distribution") {
      var numYes = 0
      var numNo  = 0
      var numUnk = 0
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).foreach{ case (query:Messages.QueryOrBuilder, truth:TruthValue) =>
        truth match {
          case TruthValue.TRUE    => numYes += 1
          case TruthValue.FALSE   => numNo  += 1
          case TruthValue.UNKNOWN => numUnk += 1
        }
      }
      numYes should be (203)
      numNo  should be (33)
      numUnk should be (98)

    }

    it ("should have the correct single-antecedent dataset") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isSingleAntecedent )
      data.size should be (183)
      data.forall( _._1.getKnownFactCount == 1) should be (right = true)
    }

    it ("should have correct single-antecedent label distribution") {
      var numYes = 0
      var numNo  = 0
      var numUnk = 0
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isSingleAntecedent ).foreach{ case (query:Messages.QueryOrBuilder, truth:TruthValue) =>
        truth match {
          case TruthValue.TRUE    => numYes += 1
          case TruthValue.FALSE   => numNo  += 1
          case TruthValue.UNKNOWN => numUnk += 1
        }
      }
      numYes should be (102)
      numNo  should be (21)
      numUnk should be (60)
    }

    it ("should have the correct 'applicable' dataset (according to McCartney)") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isApplicable )
      data.size should be (75)
      data.forall( _._1.getKnownFactCount == 1) should be (right = true)
    }

    it ("should have correct 'applicable' label distribution") {
      var numYes = 0
      var numNo  = 0
      var numUnk = 0
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isApplicable ).foreach{ case (query:Messages.QueryOrBuilder, truth:TruthValue) =>
        truth match {
          case TruthValue.TRUE    => numYes += 1
          case TruthValue.FALSE   => numNo  += 1
          case TruthValue.UNKNOWN => numUnk += 1
        }
      }
      numYes should be (35)
      numNo  should be (8)
      numUnk should be (32)
    }
  }

}
