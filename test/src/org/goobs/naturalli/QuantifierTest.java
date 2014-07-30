package org.goobs.naturalli;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A simple test to make sure the quantifiers are behaving as they should.
 *
 * @author Gabor Angeli
 */
public class QuantifierTest {

  @Test
  public void testQuantifierComparable() {
    assertTrue(Quantifier.LogicalQuantifier.FORALL.isComparableTo(Quantifier.LogicalQuantifier.FORALL));
    assertTrue(Quantifier.LogicalQuantifier.MOST.isComparableTo(Quantifier.LogicalQuantifier.MOST));
    assertTrue(Quantifier.LogicalQuantifier.EXISTS.isComparableTo(Quantifier.LogicalQuantifier.EXISTS));
    assertTrue(Quantifier.LogicalQuantifier.NONE.isComparableTo(Quantifier.LogicalQuantifier.NONE));

    assertFalse(Quantifier.LogicalQuantifier.NONE.isComparableTo(Quantifier.LogicalQuantifier.FORALL));
    assertFalse(Quantifier.LogicalQuantifier.NONE.isComparableTo(Quantifier.LogicalQuantifier.MOST));
    assertFalse(Quantifier.LogicalQuantifier.NONE.isComparableTo(Quantifier.LogicalQuantifier.EXISTS));
    assertFalse(Quantifier.LogicalQuantifier.FORALL.isComparableTo(Quantifier.LogicalQuantifier.NONE));
    assertFalse(Quantifier.LogicalQuantifier.MOST.isComparableTo(Quantifier.LogicalQuantifier.NONE));
    assertFalse(Quantifier.LogicalQuantifier.EXISTS.isComparableTo(Quantifier.LogicalQuantifier.NONE));
  }

  @Test
  public void testQuantifierDenotationOrdering() {
    assertFalse(Quantifier.LogicalQuantifier.FORALL.denotationLessThan(Quantifier.LogicalQuantifier.FORALL));
    assertTrue(Quantifier.LogicalQuantifier.FORALL.denotationLessThan(Quantifier.LogicalQuantifier.MOST));
    assertTrue(Quantifier.LogicalQuantifier.FORALL.denotationLessThan(Quantifier.LogicalQuantifier.EXISTS));

    assertFalse(Quantifier.LogicalQuantifier.MOST.denotationLessThan(Quantifier.LogicalQuantifier.FORALL));
    assertFalse(Quantifier.LogicalQuantifier.MOST.denotationLessThan(Quantifier.LogicalQuantifier.MOST));
    assertTrue(Quantifier.LogicalQuantifier.MOST.denotationLessThan(Quantifier.LogicalQuantifier.EXISTS));

    assertFalse(Quantifier.LogicalQuantifier.EXISTS.denotationLessThan(Quantifier.LogicalQuantifier.FORALL));
    assertFalse(Quantifier.LogicalQuantifier.EXISTS.denotationLessThan(Quantifier.LogicalQuantifier.MOST));
    assertFalse(Quantifier.LogicalQuantifier.EXISTS.denotationLessThan(Quantifier.LogicalQuantifier.EXISTS));
  }

  @Test
  public void testQuantifierNegation() {
    assertTrue(Quantifier.LogicalQuantifier.FORALL.isNegationOf(Quantifier.LogicalQuantifier.NONE));
    assertTrue(Quantifier.LogicalQuantifier.MOST.isNegationOf(Quantifier.LogicalQuantifier.NONE));
    assertTrue(Quantifier.LogicalQuantifier.EXISTS.isNegationOf(Quantifier.LogicalQuantifier.NONE));
    assertTrue(Quantifier.LogicalQuantifier.NONE.isNegationOf(Quantifier.LogicalQuantifier.FORALL));
    assertTrue(Quantifier.LogicalQuantifier.NONE.isNegationOf(Quantifier.LogicalQuantifier.MOST));
    assertTrue(Quantifier.LogicalQuantifier.NONE.isNegationOf(Quantifier.LogicalQuantifier.EXISTS));

    assertFalse(Quantifier.LogicalQuantifier.FORALL.isNegationOf(Quantifier.LogicalQuantifier.EXISTS));
    assertFalse(Quantifier.LogicalQuantifier.FORALL.isNegationOf(Quantifier.LogicalQuantifier.MOST));
    assertFalse(Quantifier.LogicalQuantifier.EXISTS.isNegationOf(Quantifier.LogicalQuantifier.MOST));
    assertFalse(Quantifier.LogicalQuantifier.NONE.isNegationOf(Quantifier.LogicalQuantifier.NONE));
  }
}
