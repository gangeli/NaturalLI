package org.goobs.truth

/**
 * A test to make sure we are indexing words in a reasonable way.
 *
 * @author gabor
 */
class NLPProcessingTest extends Test {
  import Utils._

  describe ("An indexer") {
    it ("should index 'at'") {
      val (indexed, _) = index("at")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("at")
    }

    it ("should index 'stock'") {
      val (indexed, _) = index("stock")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("stock")
    }

    it ("should index 'market'") {
      val (indexed, _) = index("market")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("market")
    }

    it ("should index 'stock market' in middle of sentence") {
      val (indexed, _) = index("the stock market crash")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(1)) should be ("stock market")
      wordGloss(indexed(2)) should be ("crash")
    }

    it ("should index 'stock market' at end of sentence") {
      val (indexed, _) = index("the crash of the stock market")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(4)) should be ("stock market")
      indexed.length should be (5)

      val (indexed2, _) = index("the crash of the stock market.")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed2(4)) should be ("stock market")
      indexed.length should be (5)
    }

    it ("should index 'nobel'") {
      val (indexed, _) = index("nobel")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("nobel")
    }

    it ("should index 'prize'") {
      val (indexed, _) = index("prize")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("prize")
    }

    it ("should index 'nobel prize' in middle of sentence") {
      val (indexed, _) = index("the nobel prize was awarded")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(1)) should be ("nobel prize")
      wordGloss(indexed(2)) should be ("was")
    }

    it ("should index 'nobel prize' at end of sentence") {
      val (indexed, _) = index("he won a nobel prize")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(3)) should be ("nobel prize")
    }

    it ("should index proper names") {
      val (indexed, _) = index("John saw Mary")(Postgres.indexerContains, Postgres.indexerGet, x => Utils.WORD_UNK)
      wordGloss(indexed(0)) should be ("john")
      wordGloss(indexed(2)) should be ("mary")

    }
  }

  describe ("A word sense assignment") {
    it ("should mark no sense for 'Bill'") {
      val fact = NatLog.annotate("Bill met Mary yesterday")
      fact.head.getWord(0).getSense should be (0)
      fact.head.getWord(2).getSense should be (0)
    }

    it ("should mark no sense for 'at' / 'the' / etc.") {
      val fact = NatLog.annotate("John met Mary and Jane at the store")
      fact.head.getWord(3).getSense should be (0)
      fact.head.getWord(5).getSense should be (0)
      fact.head.getWord(6).getSense should be (0)

    }
  }
}
