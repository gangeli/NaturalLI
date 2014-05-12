package org.goobs.truth

import org.goobs.truth.TruthValue.TruthValue

import scala.collection.JavaConversions._
import org.goobs.truth.Messages.Query
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test various sources of training / test data.
 *
 * @author gabor
 */
class DataSourcesTest extends Test {

  /**
   * Statistics for this test are taken from the statistics in
   * Section 4 of http://nlp.stanford.edu/~wcmac/papers/natlog-wtep07.pdf
   */
  describe("FraCaS") {

    Props.NATLOG_INDEXER_LAZY = false

    it ("should load from file") {
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).par.size should be (334)
    }

    it ("should have valid facts") {
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).par.foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
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
      val numYes = new AtomicInteger(0)
      val numNo = new AtomicInteger(0)
      val numUnk = new AtomicInteger(0)
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
        queries.size should be (1)
        for (query <- queries) {
          truth match {
            case TruthValue.TRUE    => numYes.incrementAndGet()
            case TruthValue.FALSE   => numNo.incrementAndGet()
            case TruthValue.UNKNOWN => numUnk.incrementAndGet()
          }
        }
      }
      numYes.get should be (203)
      numNo.get  should be (33)
      numUnk.get should be (98)

    }

    it ("should have the correct single-antecedent dataset") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).par.filter( FraCaS.isSingleAntecedent )
      data.size should be (183)
      data.forall( _._1.head.getKnownFactCount == 1) should be (right = true)
    }

    it ("should have correct single-antecedent label distribution") {
      val numYes = new AtomicInteger(0)
      val numNo = new AtomicInteger(0)
      val numUnk = new AtomicInteger(0)
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isSingleAntecedent ).foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
        queries.size should be (1)
        for (query <- queries) {
          truth match {
            case TruthValue.TRUE => numYes.incrementAndGet()
            case TruthValue.FALSE => numNo.incrementAndGet()
            case TruthValue.UNKNOWN => numUnk.incrementAndGet()
          }
        }
      }
      numYes.get should be (102)
      numNo.get  should be (21)
      numUnk.get should be (60)
    }

    it ("should have the correct 'applicable' dataset (according to MacCartney)") {
      val data = FraCaS.read(Props.DATA_FRACAS_PATH.getPath).par.filter( FraCaS.isApplicable )
      data.size should be (75)
      data.forall( _._1.head.getKnownFactCount == 1) should be (right = true)
    }

    it ("should have correct 'applicable' label distribution") {
      val numYes = new AtomicInteger(0)
      val numNo = new AtomicInteger(0)
      val numUnk = new AtomicInteger(0)
      FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter( FraCaS.isApplicable ).foreach{ case (queries:Iterable[Messages.QueryOrBuilder], truth:TruthValue) =>
        queries.size should be (1)
        for (query <- queries) {
          truth match {
            case TruthValue.TRUE => numYes.incrementAndGet()
            case TruthValue.FALSE => numNo.incrementAndGet()
            case TruthValue.UNKNOWN => numUnk.incrementAndGet()
          }
        }
      }
      numYes.get should be (35)
      numNo.get  should be (8)
      numUnk.get should be (32)
    }
  }

  /**
   * Tests on the held-out dataset of Ollie facts.
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
   * Test the AVE question answering validation corpus.
   * The statistics for these are taken from Angeli and Manning (2013): http://stanford.edu/~angeli/papers/2013-conll-truth.pdf.
   * The data is in etc/ave.
   */
  describe("AVE Examples") {
    val ave2006 = AVE.read(Props.DATA_AVE_PATH.get("2006").getPath).par
    val ave2007 = AVE.read(Props.DATA_AVE_PATH.get("2007").getPath).par
    val ave2008 = AVE.read(Props.DATA_AVE_PATH.get("2008").getPath).par

    it ("should load from files") {
      ave2006.size should be (1111)
      ave2007.size should be (202)
      ave2008.size should be (1055)
    }

    it ("should have valid facts") {
      for (stream <- List(ave2006, ave2007, ave2008)) {
        stream.foreach { case (queries: Iterable[Messages.QueryOrBuilder], truth: TruthValue) =>
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
      ave2006.filter( _._1.head.getForceFalse ).size should be > 0
      ave2007.filter( _._1.head.getForceFalse ).size should be > 0
      ave2008.filter( _._1.head.getForceFalse ).size should be > 0
    }
  }

  /**
   * Test the MTurk annotated ReVerb examples.
   * The statistics for these are taken from Angeli and Manning (2013): http://stanford.edu/~angeli/papers/2013-conll-truth.pdf.
   * The data is in etc/mturk.
   */
  describe("MTurk Examples") {

    val mturkTrain = MTurk.read(Props.DATA_MTURK_TRAIN.getPath).par
    val mturkTest = MTurk.read(Props.DATA_MTURK_TEST.getPath).par

    it ("should load from files") {
      mturkTrain.size should be (1796)
      mturkTest.size should be (1975)
    }

    it ("should have valid facts") {
      for (stream <- List(mturkTrain, mturkTest)) {
        stream.foreach { case (queries: Iterable[Messages.QueryOrBuilder], truth: TruthValue) =>
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
      mturkTrain.filter( _._2 == TruthValue.TRUE ).size should be (1256)
      mturkTrain.filter( _._2 == TruthValue.FALSE ).size should be (540)
      mturkTest.filter( _._2 == TruthValue.TRUE ).size should be (1286)
      mturkTest.filter( _._2 == TruthValue.FALSE ).size should be (689)
    }

  }

}
