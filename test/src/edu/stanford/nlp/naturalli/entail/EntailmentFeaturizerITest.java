package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.util.Trilean;
import org.junit.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * A test of various functions in the entailement trainer.
 *
 * @author Gabor Angeli
 */
public class EntailmentFeaturizerITest {

  @Test
  public void testAlignSimple() {
    assertEquals(
        new HashSet<String>() {{
          add("< Cats; Cats >");
          add("< play; play >");
          add("< checkers; checkers >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "Cats play checkers", "Cats play checkers", Optional.empty(), Optional.empty()),
            Optional.empty(), false).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
    assertEquals(
        new HashSet<String>() {{
          add("< Cats; Cats >");
          add("< eat; --- >");
          add("< candy; --- >");
          add("< --- ; play >");
          add("< --- ; checkers >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "Cats eat candy", "Cats play checkers", Optional.empty(), Optional.empty()),
            Optional.empty(), false).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
  }

  @Test
  public void testAlignApprox() {
    assertEquals(
        new HashSet<String>() {{
          add("< conductor of electricity; best conductor of electricity >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "It is a conductor of electricity.", "It is the best conductor of electricity.", Optional.empty(), Optional.empty()),
            Optional.empty(), false).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
  }

  @Test
  public void testAlignWordOverlap() {
    assertEquals(
        new HashSet<String>() {{
          add("< Hail; hail >");
          add("< form of precipitation; Examples of precipitation >"); // This is nontrivial
          add("< --- ; include >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "Hail is a form of precipitation.", "Examples of precipitation include hail.", Optional.empty(), Optional.empty()),
            Optional.empty(), false).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
  }

  @Test
  public void testAlignWordManyToMany() {
    assertEquals(
        new HashSet<String>() {{
          add("< Cats; Cats >");
          add("< cats; Cats >");  // many-to-one alignment here
          add("< chase; chase >");
          add("< dogs; dogs >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "Cats chase cats and dogs.", "Cats chase dogs.", Optional.empty(), Optional.empty()),
            Optional.empty(), true).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
    assertEquals(
        new HashSet<String>() {{
          add("< Cats; Cats >");
          add("< Cats; cats >");  // one-to-many alignment alignment here
          add("< chase; chase >");
          add("< dogs; dogs >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "Cats chase dogs.", "Cats chase cats and dogs.", Optional.empty(), Optional.empty()),
            Optional.empty(), true).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));

    assertEquals(
        new HashSet<String>() {{
          add("< Cats; Cats >");
          add("< --- ; cats >");  // one-to-many alignment alignment here
          add("< chase; chase >");
          add("< dogs; dogs >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "Cats chase dogs.", "Cats chase cats and dogs.", Optional.empty(), Optional.empty()),
            Optional.empty(), false).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
  }

  @Test
  public void testAlignConstrainedAlign() {
    assertEquals(
        new HashSet<String>() {{
          add("< create; create >");
          add("< water; something >"); // This is nontrivial
          add("< wine; wine >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "I create water from wine.", "I create something from wine.", Optional.empty(), Optional.empty()),
            Optional.empty(), false).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
  }

  @Test
  public void testRegressions() {
    assertEquals(
        new HashSet<String>() {{
          add("< Conductors of Heat; iron nail >");
          add("< Electricity; best conductor of electricity >");
          add("< General; --- >");
        }},
        EntailmentFeaturizer.align(new EntailmentPair(Trilean.FALSE,
                "Conductors of Heat and Electricity In General.", "an iron nail is the best conductor of electricity.", Optional.empty(), Optional.empty()),
            Optional.empty(), false).stream().map(EntailmentFeaturizer.KeywordPair::toString).collect(Collectors.toSet()));
  }
}
