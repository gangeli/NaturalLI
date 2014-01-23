package org.goobs.truth

import scala.collection.JavaConversions._

/**
 * Tests for various NatLog functionalities
 *
 * @author gabor
 */
class NatLogTest extends Test {

  describe("Natural Logic Weights") {
    describe("when a hard assignment") {
      it ("should accept Wordnet monotone jumps") {
        NatLog.hardNatlogWeights.getCount(Learn.unigramUp(EdgeType.WORDNET_UP)) should be >= 0.0
        NatLog.hardNatlogWeights.getCount(Learn.unigramDown(EdgeType.WORDNET_DOWN)) should be >= 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramUp(EdgeType.WORDNET_UP, EdgeType.WORDNET_UP)) should be >= 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramDown(EdgeType.WORDNET_DOWN, EdgeType.WORDNET_DOWN)) should be >= 0.0
      }
      it ("should accept Freebase monotone jumps") {
        NatLog.hardNatlogWeights.getCount(Learn.unigramUp(EdgeType.FREEBASE_UP)) should be >= 0.0
        NatLog.hardNatlogWeights.getCount(Learn.unigramDown(EdgeType.FREEBASE_DOWN)) should be >= 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramUp(EdgeType.FREEBASE_UP, EdgeType.FREEBASE_UP)) should be >= 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramDown(EdgeType.FREEBASE_DOWN, EdgeType.FREEBASE_DOWN)) should be >= 0.0
      }
      it ("should accept hybrid monotone jumps") {
        NatLog.hardNatlogWeights.getCount(Learn.bigramUp(EdgeType.WORDNET_UP, EdgeType.FREEBASE_UP)) should be >= 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramDown(EdgeType.FREEBASE_DOWN, EdgeType.WORDNET_DOWN)) should be >= 0.0
      }
      it ("should NOT accept mixed jumps") {
        NatLog.hardNatlogWeights.getCount(Learn.bigramUp(EdgeType.FREEBASE_UP, EdgeType.FREEBASE_DOWN)) should be < 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramDown(EdgeType.FREEBASE_DOWN, EdgeType.FREEBASE_UP)) should be < 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramUp(EdgeType.WORDNET_UP, EdgeType.FREEBASE_DOWN)) should be < 0.0
        NatLog.hardNatlogWeights.getCount(Learn.bigramDown(EdgeType.FREEBASE_DOWN, EdgeType.WORDNET_UP)) should be < 0.0
      }
      it ("should respect passed weights") {
        NatLog.natlogWeights(1.0, -1.0, -0.25).getCount(Learn.bigramUp(EdgeType.FREEBASE_UP, EdgeType.FREEBASE_UP)) should be (1.0)
        NatLog.natlogWeights(1.0, -1.0, -0.25).getCount(Learn.bigramUp(EdgeType.FREEBASE_UP, EdgeType.FREEBASE_DOWN)) should be (-1.0)
        NatLog.natlogWeights(1.0, -1.0, -0.25).getCount(Learn.unigramUp(EdgeType.ANGLE_NEAREST_NEIGHBORS)) should be (-0.25)
        NatLog.natlogWeights(1.0, -1.0, -0.25).getCount(Learn.unigramDown(EdgeType.ANGLE_NEAREST_NEIGHBORS)) should be (-0.25)
        NatLog.natlogWeights(1.0, -1.0, -0.25).getCount(Learn.unigramFlat(EdgeType.ANGLE_NEAREST_NEIGHBORS)) should be (-0.25)

      }
    }
  }


  describe("Monotonicity Markings") {
    it ("should mark 'all'") {
      NatLog.annotate("all cats", "have", "tails").getWordList.map( _.getMonotonicity ) should be (List(-1, -1, 1, 1))
      NatLog.annotate("every cat", "has", "a tail").getWordList.map( _.getMonotonicity ) should be (List(-1, -1, 1, 1, 1))
    }
    it ("should mark 'some'") {
      NatLog.annotate("some cats", "have", "tails").getWordList.map( _.getMonotonicity ) should be (List(1, 1, 1, 1))
      NatLog.annotate("there are cats", "which have", "tails").getWordList.map( _.getMonotonicity ) should be (List(1, 1, 1, 1, 1, 1))
      NatLog.annotate("there exist cats", "which have", "tails").getWordList.map( _.getMonotonicity ) should be (List(1, 1, 1, 1, 1, 1))
    }
    it ("should mark 'most'") {
      NatLog.annotate("few cat", "have", "tails").getWordList.map( _.getMonotonicity ) should be (List(0, 0, 1, 1))
      NatLog.annotate("most cats", "have", "tails").getWordList.map( _.getMonotonicity ) should be (List(0, 0, 1, 1))
    }
    it ("should mark 'no'") {
      NatLog.annotate("no cats", "have", "tails").getWordList.map( _.getMonotonicity ) should be (List(-1, -1, -1, -1))
      NatLog.annotate("cat", "dont have", "tails").getWordList.map( _.getMonotonicity ) should be (List(-1, -1, -1, -1))
    }
  }
}