package edu.stanford.nlp.naturalli;

import edu.smu.tspell.wordnet.Synset;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
    if (Quantifier.GLOSSES.contains(gloss.toLowerCase())) {
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

  private static Set<String> SUBJ_RELATIONS = new HashSet<String>() {{
    add("nsubj");
  }};

  private static Set<String> OBJ_RELATIONS = new HashSet<String>() {{
    add("dobj");
    add("pobj");
    add("prep");
    add("xcomp");
  }};

  private static Set<String> NEG_RELATIONS = new HashSet<String>() {{
    add("neg");
  }};

  public Word updateSpans(int[] oldToNewIndex) {
    return new Word(
        this.gloss, this.index, this.sense,
        this.subjSpan == NO_SPAN ? NO_SPAN : new int[]{oldToNewIndex[this.subjSpan[0]], oldToNewIndex[this.subjSpan[1]]},
        this.subjMonotonicity, this.subjType,
        this.objSpan == NO_SPAN ? NO_SPAN : new int[]{oldToNewIndex[this.objSpan[0]], oldToNewIndex[this.objSpan[1]]},
        this.objMonotonicity, this.objType);
  }

  private static enum ARITY { UNARY, BINARY, NEG, UNK }

  private static int[] computeSpans(DependencyTree<String> tree, int index) {
    int[] rtn = new int[]{-1, -1, -1, -1};

    // Find the root of the phrase
    int quantifierSide = -1;
    int negRoot = -1;
    int root = index;
    ARITY arity = ARITY.UNK;
    while (!"root".equals(tree.governingRelations[root])) {
      if (SUBJ_RELATIONS.contains(tree.governingRelations[root])) {
        arity = ARITY.BINARY;
        quantifierSide = root;
        root = tree.governors[root];
        break;
      } else if (OBJ_RELATIONS.contains(tree.governingRelations[root])) {
        arity = ARITY.UNARY;
        quantifierSide = root;
        root = tree.governors[root];
        break;
      } else if (NEG_RELATIONS.contains(tree.governingRelations[root])) {
        arity = ARITY.NEG;
        quantifierSide = root;
        if (negRoot < 0) { negRoot = root; }
        root = tree.governors[root];
        // don't break -- it may still be binary
      } else {
        root = tree.governors[root];
      }
    }
    if (arity == ARITY.NEG) {
      root = negRoot;
    }

    // Get the subject span
    Pair<Integer, Integer> subj = tree.getSubtreeSpan(quantifierSide);
    assert index < subj.second && index >= subj.first;
    rtn[0] = index + 1;
    rtn[1] = Math.max(subj.second, root);

    // Get the object span
    if (arity == ARITY.BINARY) {
      for (int i = 0; i < tree.nodes.length; ++i) {
        // Regular object
        if (tree.governors[i] == root && OBJ_RELATIONS.contains(tree.governingRelations[i])) {
          Pair<Integer, Integer> obj = tree.getSubtreeSpan(i);
          rtn[2] = Math.min(root, obj.first);
          rtn[3] = obj.second;
          break;
        }
        // Copula
        if (tree.governors[i] == root && "cop".equals(tree.governingRelations[i])) {
          Pair<Integer, Integer> obj = tree.getSubtreeSpan(i);
          rtn[1] = Math.max(subj.second, i);
          rtn[2] = Math.max(i, obj.first);
          rtn[3] = Math.max(tree.governors[i] + 1, obj.second);
          break;
        }
      }
    }

    // Return
    return rtn;
  }

  public static Optional<Word> compute(DependencyTree<String> tree, int spanStart, int spanEnd, Function<String, Integer> indexer) {
    String gloss = String.join(" ", Arrays.asList(tree.nodes).subList(spanStart, spanEnd));
    int indexedWord = indexer.apply(gloss);
    if (indexedWord >= 0) {
      if (Quantifier.GLOSSES.contains(gloss.toLowerCase())) {
        int[] spans = computeSpans(tree, spanStart);
        if (spans[0] >= 0) {
          if (spans[2] >= 0) {
            for (Quantifier q : Quantifier.values()) {
              if (!q.isUnary() && q.surfaceForm.equals(gloss)) {
                return Optional.of(new Word(
                    gloss, indexedWord,
                    computeSense(indexedWord, gloss, tree, spanStart, spanEnd),
                    new int[]{spans[0], spans[1]}, q.subjMono, q.subjType,
                    new int[]{spans[2], spans[3]}, q.objMono, q.objType));
              }
            }
          }
          for (Quantifier q : Quantifier.values()) {
            if (q.isUnary() && q.surfaceForm.equals(gloss)) {
              return Optional.of(new Word(
                  gloss, indexedWord,
                  computeSense(indexedWord, gloss, tree, spanStart, spanEnd),
                  new int[]{spans[0], spans[1]}, q.subjMono, q.subjType,
                  NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE));
            }
          }
        }
      }
      return Optional.of(new Word(
          gloss, indexedWord,
          computeSense(indexedWord, gloss, tree, spanStart, spanEnd),
          NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE,
          NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE));
    }
    return Optional.empty();
  }

  public static final int[] NO_SPAN = new int[]{ -1, -1 };

  public static final Word UNK = new Word("__UNK__", 1, 0, NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE, NO_SPAN, Monotonicity.INVALID, MonotonicityType.NONE);
}
