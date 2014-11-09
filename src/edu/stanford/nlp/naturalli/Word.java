package edu.stanford.nlp.naturalli;

import edu.smu.tspell.wordnet.Synset;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * A representation of a word, complete with a sense tag and optional
 * monotonicity markings
 *
 * @author Gabor Angeli
 */
public class Word {
  public final String gloss;
  public final int index;
  public final int sense;
  public final int[] subjSpan;
  public final Monotonicity subjMonotonicity;
  public final MonotonicityType subjType;
  public final int[] objSpan;
  public final Monotonicity objMonotonicity;
  public final MonotonicityType objType;

  protected Word(String gloss, int index, int sense, int[] subjSpan, Monotonicity subjMonotonicity, MonotonicityType subjType, int[] objSpan, Monotonicity objMonotonicity, MonotonicityType objType) {
    this.gloss = gloss;
    this.index = index;
    this.sense = sense;
    this.subjSpan = subjSpan;
    this.subjMonotonicity = subjMonotonicity;
    this.subjType = subjType;
    this.objSpan = objSpan;
    this.objMonotonicity = objMonotonicity;
    this.objType = objType;
  }

  @Override
  public String toString() {
    return Integer.toString(index);
  }

  @SuppressWarnings("UnnecessaryUnboxing")
  private static int computeSense(int indexedWord, String gloss, DependencyTree<String> tree, int spanStart, int spanEnd) {
    // Check if its a quantifire
    if (Operator.GLOSSES.contains(gloss.toLowerCase())) {
      return 0;
    }

    // Get the best Synset
    Optional<Synset> synset;
    if (tree.posTags.isPresent()) {
      Counter<String> posCounts = new ClassicCounter<>();
      for (int i = spanStart; i < spanEnd; ++i) {
        String pos = tree.posTags.get()[i];
        if (pos.startsWith("N") ||
            pos.startsWith("V") ||
            pos.startsWith("J") ||
            pos.startsWith("R") ) {
          posCounts.incrementCount(pos);
        }
      }
      String pos;
      if (posCounts.size() > 0) {
        pos = Counters.argmax(posCounts);
      } else {
        pos = "???";
      }
      synset = WSD.sense(tree.asList(), spanStart, spanEnd, pos);
    } else {
      synset = WSD.sense(tree.asList(), spanStart, spanEnd, "???");
    }

    // match the Synset to an index
    if (synset.isPresent()) {
      Integer senseOrNull = StaticResources.SENSE_INDEXER.get(Pair.makePair(indexedWord, synset.get().getDefinition()));
      if (senseOrNull == null) {
        return 0;
      } else {
        return senseOrNull.intValue();
      }
    } else {
      return 0;
    }
  }

  public Word updateSpans(int[] oldToNewIndex) {
    return new Word(
        this.gloss, this.index, this.sense,
        this.subjSpan == NO_SPAN ? NO_SPAN : new int[]{oldToNewIndex[this.subjSpan[0]], oldToNewIndex[this.subjSpan[1]]},
        this.subjMonotonicity, this.subjType,
        this.objSpan == NO_SPAN ? NO_SPAN : new int[]{oldToNewIndex[this.objSpan[0]], oldToNewIndex[this.objSpan[1]]},
        this.objMonotonicity, this.objType);
  }

  public static Optional<Word> compute(DependencyTree<String> tree, int spanStart, int spanEnd,
                                       Optional<OperatorSpec> operator,
                                       Function<String, Integer> indexer) {
    String gloss = String.join(" ", Arrays.asList(tree.nodes).subList(spanStart, spanEnd));
    int indexedWord = indexer.apply(gloss);
    if (indexedWord >= 0) {
      int sense = computeSense(indexedWord, gloss, tree, spanStart, spanEnd);
      if (operator.isPresent()) {
        int[] subjectSpan = new int[]{ operator.get().subjectBegin, operator.get().subjectEnd };
        Operator instance = operator.get().instance;
        if (operator.get().isBinary()) {
          int[] objectSpan = new int[]{ operator.get().objectBegin, operator.get().objectEnd };
          return Optional.of(new Word(gloss, indexedWord, sense, subjectSpan, instance.subjMono, instance.subjType,
              objectSpan, instance.objMono, instance.objType));
        } else {
          return Optional.of(new Word(gloss, indexedWord, sense, subjectSpan, instance.subjMono, instance.subjType,
              NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE));
        }
      } else {
        return Optional.of(new Word(gloss, indexedWord, sense,
            NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE,
            NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE));
      }
    } else {
      return Optional.empty();
    }
  }

  public static final int[] NO_SPAN = new int[]{ -1, -1 };

  public static final Word UNK = new Word("__UNK__", 1, 0, NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE, NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE);
}
