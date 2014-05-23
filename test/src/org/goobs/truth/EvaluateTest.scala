package org.goobs.truth

/**
 * Test various helper functions for evaluating performance.
 *
 * @author gabor
 */
class EvaluateTest extends Test {

  describe("Precision") {
    it ("should pass a sanity check") {
      val guessAndGold = List( (true, true), (true, false), (false, true), (false, false) )
      Evaluate.p(guessAndGold) should be (0.5 +- 1e-10)
    }
    it ("should lower bound at 0") {
      val guessAndGold = List( (false, true), (true, false), (false, true), (false, false) )
      Evaluate.p(guessAndGold) should be (0.0 +- 1e-10)
    }
    it ("should upper bound at 1") {
      val guessAndGold = List( (true, true), (false, true), (true, true), (false, false) )
      Evaluate.p(guessAndGold) should be (1.0 +- 1e-10)
    }
    it ("should degenerate nicely") {
      Evaluate.p(Nil) should be (1.0 +- 1e-10)
      val guessAndGold = List( (false, false) )
      Evaluate.p(guessAndGold) should be (1.0 +- 1e-10)
    }
  }

  describe("Recall") {
    it ("should pass a sanity check") {
      val guessAndGold = List( (true, true), (true, false), (false, true), (false, false) )
      Evaluate.r(guessAndGold) should be (0.5 +- 1e-10)
    }
    it ("should lower bound at 0") {
      val guessAndGold = List( (false, true), (true, false), (false, true), (false, false) )
      Evaluate.r(guessAndGold) should be (0.0 +- 1e-10)
    }
    it ("should upper bound at 1") {
      val guessAndGold = List( (true, true), (true, false), (true, true), (false, false) )
      Evaluate.r(guessAndGold) should be (1.0 +- 1e-10)
    }
    it ("should degenerate nicely") {
      Evaluate.r(Nil) should be (0.0 +- 1e-10)
      val guessAndGold = List( (false, false) )
      Evaluate.r(guessAndGold) should be (0.0 +- 1e-10)
    }
  }

  describe("F1") {
    it ("should pass a sanity check") {
      val guessAndGold = List( (true, true), (true, false), (false, true), (false, false) )
      Evaluate.f1(guessAndGold) should be (0.5 +- 1e-10)
    }
    it ("should match my math") {
      val guessAndGold = List( (true, false), (true, false), (false, true), (true, true), (true, false) )
      Evaluate.p(guessAndGold) should be (1.0/4.0 +- 1e-10)
      Evaluate.r(guessAndGold) should be (1.0/2.0 +- 1e-10)
      Evaluate.f1(guessAndGold) should be (1.0 / 3.0 +- 1e-10)
    }
    it ("should lower bound at 0") {
      val guessAndGold = List( (false, true), (true, false), (false, true), (false, false) )
      Evaluate.f1(guessAndGold) should be (0.0 +- 1e-10)
    }
    it ("should upper bound at 1") {
      val guessAndGold = List( (true, true), (true, true), (true, true), (false, false) )
      Evaluate.f1(guessAndGold) should be (1.0 +- 1e-10)
    }
    it ("should degenerate nicely") {
      Evaluate.f1(Nil) should be (0.0 +- 1e-10)
      val guessAndGold = List( (false, false) )
      Evaluate.f1(guessAndGold) should be (0.0 +- 1e-10)
    }
  }

  describe("AUC") {
    it ("should pass a sanity check") {
      val guessAndGold = List( (false, true), (true, false), (true, true), (false, false) )
      Evaluate.auc(guessAndGold) should be (2.0 / 3.0 +- 1e-10)
    }
    it ("should expand to the right") {
      val guessAndGold = List( (true, false), (true, false), (true, true), (false, false) )
      Evaluate.auc(guessAndGold) should be (2.0 / 3.0 +- 1e-10)
    }
    it ("should be 0 if we never guess") {
      val guessAndGold = List( (false, true), (false, false), (false, true), (false, false) )
      Evaluate.auc(guessAndGold) should be (0.0 +- 1e-10)
    }
    it ("should be 1 if accuracy is 100%") {
      val guessAndGold = List( (true, true), (false, false), (true, true), (false, false) )
      Evaluate.auc(guessAndGold) should be (1.0 +- 1e-10)
    }
    it ("should pass a more interesting distribution") {
      val guessAndGold = List( (false, true), (true, false), (true, true), (true, true), (false, false), (false, true) )
      Evaluate.auc(guessAndGold) should be (47.0 / 120.0 +- 1e-10)
    }

  }
}
