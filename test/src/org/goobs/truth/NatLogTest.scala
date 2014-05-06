package org.goobs.truth

import scala.collection.JavaConversions._
import edu.smu.tspell.wordnet.WordNetDatabase
import edu.stanford.nlp.natlog.Monotonicity
import edu.stanford.nlp.Sentence

/**
 * Tests for various NatLog functionalities
 *
 * @author gabor
 */
class NatLogTest extends Test {

  val DEFAULT = Monotonicity.DEFAULT match {
    case Monotonicity.UP => Messages.Monotonicity.UP
    case Monotonicity.DOWN => Messages.Monotonicity.DOWN
    case Monotonicity.NON => Messages.Monotonicity.FLAT
  }

  describe("Natural Logic Weights") {
    Props.NATLOG_INDEXER_LAZY = false
    describe("when a hard assignment") {
      it ("should accept Wordnet monotone jumps") {
        NatLog.hardNatlogWeights.getCount(Learn.monoUp_stateTrue(EdgeType.WORDNET_UP)) should be >= -1.0
        NatLog.hardNatlogWeights.getCount(Learn.monoDown_stateTrue(EdgeType.WORDNET_DOWN)) should be >= -1.0
      }
      it ("should accept Freebase monotone jumps") {
        NatLog.hardNatlogWeights.getCount(Learn.monoUp_stateTrue(EdgeType.FREEBASE_UP)) should be >= -1.0
        NatLog.hardNatlogWeights.getCount(Learn.monoDown_stateTrue(EdgeType.FREEBASE_DOWN)) should be >= -1.0
      }
    }
  }


  describe("Monotonicity Markings") {
    Props.NATLOG_INDEXER_LAZY = false
    import Messages.Monotonicity._
    it ("should mark 'all'") {
      NatLog.annotate("all cats", "have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(UP, DOWN, UP, UP))
      NatLog.annotate("every cat", "has", "a tail").getWordList.map( _.getMonotonicity ).toList should be (List(UP, DOWN, UP, UP, UP))
    }
    it ("should mark 'some'") {
      NatLog.annotate("some cats", "have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(UP, UP, UP, UP))
      NatLog.annotate("there are cats", "which have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(UP, UP, UP, UP, UP))
      NatLog.annotate("there exist cats", "which have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(UP, UP, UP, UP, UP))
    }
    it ("should mark 'few'") {
      NatLog.annotate("few cat", "have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(UP, DOWN, UP, UP))
    }
    it ("should mark 'most'/'many'") {
      NatLog.annotate("most cats", "have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(UP, FLAT, UP, UP))
      NatLog.annotate("many cats", "have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(UP, FLAT, UP, UP))
    }
    it ("should mark 'no'") {
      NatLog.annotate("no cats", "have", "tails").getWordList.map(_.getMonotonicity).toList should be(List(UP, DOWN, DOWN, DOWN))
    }
    it ("should mark 'not'") {
      NatLog.annotate("cat", "do not have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(DEFAULT, UP, UP, DOWN, DOWN))
      NatLog.annotate("cat", "don't have", "tails").getWordList.map( _.getMonotonicity ).toList should be (List(DEFAULT, UP, UP, DOWN, DOWN))
    }
    it ("should work on 'Every job that involves a giant squid is dangerous'") {
      NatLog.annotate("every job that involves a giant squid is dangerous").head.getWordList.map( _.getMonotonicity ).toList should be (
        List(UP, DOWN, DOWN, DOWN, DOWN, DOWN, UP, UP))
    }
    it ("should work on 'Not every job that involves a giant squid is safe'") {
      new Sentence("not every job that involves a giant squid is safe").words.length should be (10)
      NatLog.annotate("not every job that involves a giant squid is safe").head.getWordList.map( _.getMonotonicity ).toList should be (
        List(UP, DOWN, UP, UP, UP, UP, UP, DOWN, DOWN))
    }
  }

  describe("Monotone boundaries") {
    it("should find the first verb") {
      NatLog.annotate("some cat has a tail").head.getMonotoneBoundary should be (2)
      NatLog.annotate("all cats have a tail").head.getMonotoneBoundary should be (2)
      NatLog.annotate("all cats are animals").head.getMonotoneBoundary should be (2)

    }
  }

  describe("Lesk") {
    Props.NATLOG_INDEXER_LAZY = false
    it ("should be perfect for exact string matches") {
      NatLog.lesk(WordNetDatabase.getFileInstance.getSynsets("cat")(0), "feline mammal usually having thick soft fur and no ability to roar: domestic cats; wildcats".split("""\s+""")) should be (225.0)
    }
    it ("should be reasonable for multi-word overlaps") {
      NatLog.lesk(WordNetDatabase.getFileInstance.getSynsets("cat")(5), "cat be tracked vehicle".split("""\s+""")) should be (4.0)
      NatLog.lesk(WordNetDatabase.getFileInstance.getSynsets("cat")(5), "cat be large tracked vehicle".split("""\s+""")) should be (9.0)
    }
  }

  describe("Word Senses") {
    Props.NATLOG_INDEXER_LAZY = false
    it ("should get default sense of 'cat'") {
      NatLog.annotate("the cat", "have", "tail").getWordList.map( _.getPos ).toList should be (List("e", "n", "v", "n"))
      NatLog.annotate("the cat", "have", "tail").getWordList.map( _.getSense ).toList should be (List(0, 1, 2, 1))
    }
    it ("should get vehicle senses of 'CAT' with enough evidence") {
      NatLog.annotate("the cat", "be", "large tracked vehicle").getWordList.map( _.getPos ).toList should be (List("e", "n", "v", "j", "n"))
      NatLog.annotate("the cat", "be", "large tracked vehicle").getWordList.map( _.getSense ).toList should be (List(0, 6, 2, 2, 0))  // TODO(gabor) this should end with 1, not 0
    }
    it ("should get right sense of 'tail'") {
      NatLog.annotate("some cat", "have", "tail").getWordList.map( _.getSense ).toList should be (List(0, 1, 2, 1))
      NatLog.annotate("some animal", "have", "tail").getWordList.map( _.getSense ).toList should be (List(0, 1, 2, 1))
    }
    it ("should not mark final VBP as a OTHER") {
      NatLog.annotate("cats", "have", "more fur than dogs have").getWordList.map( _.getPos ).toList should be (List("n", "v", "j", "n", "?", "n", "?"))
      NatLog.annotate("cats", "have", "a more important role than dogs are").getWordList.map( _.getPos ).toList.last should be ("?")

    }
  }
}