package org.goobs.truth

import collection.mutable.Stack

/**
 * TODO(gabor) JavaDoc
 *
 * @author gabor
 */
class NatLogSpec extends Test {

  describe("Natural Logic Weights") {
    describe("when a hard assignment") {
      it("should accept Wordnet jumps") {
        NatLog.hardNatlogWeights.getCount(Learn.unigramUp(EdgeType.WORDNET_UP)) should be >= 0.0
      }
    }

  }
}