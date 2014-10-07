package edu.stanford.nlp.naturalli;

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

  private static int computeSense(String gloss, DependencyTree<String> tree, int spanStart, int spanEnd) {
    // TODO(gabor)
    return 0;
  }

  private static int[] computeSpans(DependencyTree<String> tree, int index) {
    int[] rtn = new int[]{0, 3, 3, 5};
    // TODO(gabor)
    return rtn;
  }

  public static Optional<Word> compute(DependencyTree<String> tree, int spanStart, int spanEnd, Function<String, Integer> indexer) {
    String gloss = String.join(" ", Arrays.asList(tree.nodes).subList(spanStart, spanEnd));
    int index = indexer.apply(gloss);
    if (index >= 0) {
      if (Quantifier.GLOSSES.contains(gloss)) {
        int[] spans = computeSpans(tree, index);
        if (spans[0] >= 0) {
          if (spans[2] >= 0) {
            for (Quantifier q : Quantifier.values()) {
              if (!q.isUnary() && q.surfaceForm.equals(gloss)) {
                return Optional.of(new Word(
                    gloss, index,
                    computeSense(gloss, tree, spanStart, spanEnd),
                    new int[]{spans[0], spans[1]}, q.subjMono, q.subjType,
                    new int[]{spans[2], spans[3]}, q.objMono, q.objType));
              }
            }
          }
          for (Quantifier q : Quantifier.values()) {
            if (q.isUnary() && q.surfaceForm.equals(gloss)) {
              return Optional.of(new Word(
                  gloss, index,
                  computeSense(gloss, tree, spanStart, spanEnd),
                  new int[]{spans[0], spans[1]}, q.subjMono, q.subjType,
                  NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE));
            }
          }
        }
      }
      return Optional.of(new Word(
          gloss, index,
          computeSense(gloss, tree, spanStart, spanEnd),
          NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE,
          NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE));
    }
    return Optional.empty();
  }

  public static final int[] NO_SPAN = new int[]{ -1, -1 };

  public static final Word UNK = new Word("__UNK__", 0, 0, NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE, NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE);
}
