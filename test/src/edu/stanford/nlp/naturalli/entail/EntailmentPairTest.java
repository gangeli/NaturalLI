package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Trilean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test various bits of the EntailmentPair class
 *
 * @author Gabor Angeli
 */
public class EntailmentPairTest {

  @Test
  public void testConstructorMinimal() {
    EntailmentPair pair = new EntailmentPair(Trilean.TRUE, "the premise is this", "the conclusion is this", Optional.empty(), Optional.empty());
    assertEquals(Trilean.TRUE, pair.truth);
    assertEquals("the premise is this", pair.premise.text());
    assertEquals("the conclusion is this", pair.conclusion.text());
    assertEquals(Optional.empty(), pair.focus);
    assertEquals(Optional.empty(), pair.luceneScore);
  }

  @Test
  public void testConstructorOptionals() {
    EntailmentPair pair = new EntailmentPair(Trilean.TRUE, "the premise is this", "the conclusion is this", Optional.of("this"), Optional.of(42.0));
    assertEquals(Trilean.TRUE, pair.truth);
    assertEquals("the premise is this", pair.premise.text());
    assertEquals("the conclusion is this", pair.conclusion.text());
    assertEquals(Optional.of("this"), pair.focus);
    assertEquals(Optional.of(42.0), pair.luceneScore);
  }

  @Test
  public void testAlignment() {
    List<EntailmentPair> dataset = new ArrayList<EntailmentPair>(){{
      add(new EntailmentPair(Trilean.TRUE, "the cat is blue", "el gato es azul", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "the cat is green", "el gato es verde", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "the fish is green", "la pescado es verde", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "the fish is blue", "la pescado es azul", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "all fish are blue", "todos pescados son azules", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "red fish", "pescado rojo", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "blue fish", "pescado azul", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "one fish", "un pescado", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "two fish", "dos pescados", Optional.empty(), Optional.empty()));
      add(new EntailmentPair(Trilean.TRUE, "blue cat", "gato azul", Optional.empty(), Optional.empty()));
    }};

    EntailmentPair.align(3, 2, dataset);

    // Test first sentence
    assertEquals(0, dataset.get(0).conclusionToPremiseAlignment[0]);
    assertEquals(1, dataset.get(0).conclusionToPremiseAlignment[1]);
    assertEquals(2, dataset.get(0).conclusionToPremiseAlignment[2]);
    assertEquals(3, dataset.get(0).conclusionToPremiseAlignment[3]);

    // Test nonlinear sentence
    assertEquals(1, dataset.get(9).conclusionToPremiseAlignment[0]);
    assertEquals(0, dataset.get(9).conclusionToPremiseAlignment[1]);

    // Test phrase table mapping
    assertFalse(dataset.get(0).phraseTable.isEmpty());
    assertTrue(dataset.get(0).phraseTable.contains(Pair.makePair("the cat", "el gato")));
    assertTrue(dataset.get(0).phraseTable.contains(Pair.makePair("be blue", "es azul")));
    assertTrue(dataset.get(0).phraseTable.contains(Pair.makePair("the cat be", "el gato es")));
  }

}
