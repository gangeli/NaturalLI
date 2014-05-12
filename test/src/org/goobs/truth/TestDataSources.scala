package org.goobs.truth

import org.goobs.truth.TruthValue.TruthValue

import scala.collection.JavaConversions._
import org.goobs.truth.Messages.Query

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
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
        queries.size should be (1)
        for (query <- queries) {
          // Ensure all the facts have been indexed successfully
          query.getKnownFactCount should be > 0
          query.getKnownFactList.foreach { (antecedent: Messages.Fact) =>
            antecedent.getWordCount should be > 0
          }
          query.getQueryFact.getWordCount should be > 0
        }
      }
    }

    it ("should have the correct total label distribution") {
      var numYes = 0
      var numNo  = 0
      var numUnk = 0
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
        queries.size should be (1)
        for (query <- queries) {
          truth match {
            case TruthValue.TRUE    => numYes += 1
            case TruthValue.FALSE   => numNo  += 1
            case TruthValue.UNKNOWN => numUnk += 1
          }
        }
      }
      numYes should be (203)
      numNo  should be (33)
      numUnk should be (98)

    }

    it ("should have the correct single-antecedent dataset") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isSingleAntecedent )
      data.size should be (183)
      data.forall( _._1.head.getKnownFactCount == 1) should be (right = true)
    }

    it ("should have correct single-antecedent label distribution") {
      var numYes = 0
      var numNo  = 0
      var numUnk = 0
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isSingleAntecedent ).foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
        queries.size should be (1)
        for (query <- queries) {
          truth match {
            case TruthValue.TRUE => numYes += 1
            case TruthValue.FALSE => numNo += 1
            case TruthValue.UNKNOWN => numUnk += 1
          }
        }
      }
      numYes should be (102)
      numNo  should be (21)
      numUnk should be (60)
    }

    it ("should have the correct 'applicable' dataset (according to McCartney)") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isApplicable )
      data.size should be (75)
      data.forall( _._1.head.getKnownFactCount == 1) should be (right = true)
    }

    it ("should have correct 'applicable' label distribution") {
      var numYes = 0
      var numNo  = 0
      var numUnk = 0
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isApplicable ).foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
        queries.size should be (1)
        for (query <- queries) {
          truth match {
            case TruthValue.TRUE => numYes += 1
            case TruthValue.FALSE => numNo += 1
            case TruthValue.UNKNOWN => numUnk += 1
          }
        }
      }
      numYes should be (35)
      numNo  should be (8)
      numUnk should be (32)
    }
  }

  /**
   * Tests on the held-out dataset
   */
  describe("Held-Out") {

    it ("should be readable") {
      HoldOneOut.read("").take(10).length should be (10)
    }

    it ("should have an equal number of positive and negative examples") {
      HoldOneOut.read("").take(10).filter{case (x:Iterable[Query.Builder], t:TruthValue) => t == TruthValue.TRUE}.length should be (5)
      HoldOneOut.read("").take(10).filter{case (x:Iterable[Query.Builder], t:TruthValue) => t == TruthValue.FALSE}.length should be (5)
    }

  }

  /**
   * Test the AVE
   */
  describe("AVE Examples") {

    it ("should load from files") {
      AVE.read(Props.DATA_AVE_PATH.get("2006").getPath).size should be (1111)
      AVE.read(Props.DATA_AVE_PATH.get("2007").getPath).size should be (202)
      AVE.read(Props.DATA_AVE_PATH.get("2008").getPath).size should be (1055)
    }

    it ("should have valid facts") {
      for (path <- Props.DATA_AVE_PATH.values()) {
        AVE.read(path.getPath).foreach { case (queries: Iterable[Messages.QueryOrBuilder], truth: TruthValue) =>
          queries.size should be > 0
          queries.size should be <= 2
          for (query <- queries) {
            // Ensure all the facts have been indexed successfully
            query.getKnownFactCount should be (0)
            query.getKnownFactList.foreach { (antecedent: Messages.Fact) =>
              antecedent.getWordCount should be > 0
            }
            query.getQueryFact.getWordCount should be > 0
          }
        }
      }
    }

    it ("should have the right number of facts filtered") {
      AVE.read(Props.DATA_AVE_PATH.get("2006").getPath).filter( _._1.head.getForceFalse ).size should be > 0
      AVE.read(Props.DATA_AVE_PATH.get("2007").getPath).filter( _._1.head.getForceFalse ).size should be > 0
      AVE.read(Props.DATA_AVE_PATH.get("2008").getPath).filter( _._1.head.getForceFalse ).size should be > 0
    }
  }

  describe("MTurk Examples") {

    it ("should load from files") {
      MTurk.read(Props.DATA_MTURK_TRAIN.getPath).size should be (1796)
      MTurk.read(Props.DATA_MTURK_TEST.getPath).size should be (1975)
    }

    it ("should have valid facts") {
      for (path <- List(Props.DATA_MTURK_TRAIN, Props.DATA_MTURK_TEST)) {
        MTurk.read(path.getPath).foreach { case (queries: Iterable[Messages.QueryOrBuilder], truth: TruthValue) =>
          queries.size should be > 0
          queries.size should be <= 2
          for (query <- queries) {
            // Ensure all the facts have been indexed successfully
            query.getKnownFactCount should be (0)
            query.getKnownFactList.foreach { (antecedent: Messages.Fact) =>
              antecedent.getWordCount should be > 0
            }
            query.getQueryFact.getWordCount should be > 0
          }
        }
      }
    }

    it ("should have the right number of facts filtered") {
      MTurk.read(Props.DATA_MTURK_TRAIN.getPath).filter( _._2 == TruthValue.TRUE ).size should be (1256)
      MTurk.read(Props.DATA_MTURK_TRAIN.getPath).filter( _._2 == TruthValue.FALSE ).size should be (540)
      MTurk.read(Props.DATA_MTURK_TEST.getPath).filter( _._2 == TruthValue.TRUE ).size should be (1286)
      MTurk.read(Props.DATA_MTURK_TEST.getPath).filter( _._2 == TruthValue.FALSE ).size should be (689)
    }

  }

}
