package edu.stanford.nlp.naturalli;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * A test for {@link edu.stanford.nlp.naturalli.WSD}.
 *
 * @author Gabor Angeli
 */
public class WSDTest {

  @Test
  public void leskTrivial() {
    assertEquals(1, WSD.lesk("dog cat snail", "dog fish camel"));
  }

  @Test
  public void leskMultipleExclusiveMatches() {
    assertEquals(1, WSD.lesk("dog cat snail", "dog dog dog"));
    assertEquals(1, WSD.lesk("dog dog dog", "dog cat snail"));
  }

  @Test
  public void leskMultipleInclusiveMatches() {
    assertEquals(2, WSD.lesk("dog cat snail", "snail dog dog"));
    assertEquals(2, WSD.lesk("dog dog dog", "dog snail dog"));
  }

  @Test
  public void leskDifferentLengths() {
    assertEquals(1, WSD.lesk("dog cat snail sheep cow", "dog fish camel"));
    assertEquals(1, WSD.lesk("dog cat snail", "dog dog dog dog dog dog"));
    assertEquals(1, WSD.lesk("dog dog dog dog dog dog", "dog cat snail"));
  }

  @Test
  public void leskLongerMatches() {
    assertEquals(1, WSD.lesk("dog cat", "dog snail"));
    assertEquals(4, WSD.lesk("dog cat", "dog cat snail"));
    assertEquals(9, WSD.lesk("fuzzy dog cat", "fuzzy dog cat snail"));
    assertEquals(5, WSD.lesk("dog cat fuzzy", "dog cat snail fuzzy"));
  }


  @Test
  public void leskStopWords() {
    assertEquals(4, WSD.lesk("Bill the dog cat", "Bill dog fish"));
    assertEquals(4, WSD.lesk("Bill the dog cat", "Bill the dog fish"));
  }
}
