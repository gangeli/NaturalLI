package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Pair;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * I hate SemanticGraph as a representation.
 *
 * @author Gabor Angeli
 */
public class DependencyTree<E> {
  public final E[] nodes;
  public final int[] governors;
  public final String[] governingRelations;

  @SuppressWarnings("unchecked")
  public DependencyTree(SemanticGraph dependencies, List<CoreLabel> sentence) {
    // Create structures
    nodes = (E[]) new String[dependencies.size()];
    Arrays.fill(nodes, "__UNK__");
    governors = new int[dependencies.size()];
    Arrays.fill(governors, 0);
    governingRelations = new String[dependencies.size()];
    Arrays.fill(governingRelations, "dep");
    // Copy into dependency tree
    // This is why I hate SemanticGraph as a representation...
    for (IndexedWord vertex : dependencies.vertexSet()) {
      nodes[vertex.index() - 1] = (E) sentence.get(vertex.index() - 1).lemma();
      Set<IndexedWord> parents = dependencies.getParents(vertex);
      assert parents.size() < 2;
      if (parents.size() == 1) {
        governors[vertex.index() - 1] = parents.iterator().next().index();
        governingRelations[vertex.index() - 1] = dependencies.getIncomingEdgesSorted(vertex).get(0).getRelation().getShortName();
      }
    }
    for (IndexedWord root : dependencies.getRoots()) {
      governingRelations[root.index() - 1] = "root";

    }
  }

  public DependencyTree(E[] nodes, int[] governors, String[] governingRelations) {
    this.nodes = nodes;
    this.governors = governors;
    this.governingRelations = governingRelations;
  }

  private void foreachSpan(Consumer<Pair<Integer,Integer>> callback) {
    for (int length = nodes.length; length > 0; --length) {
      LOOP: for (int candidateStart = 0; candidateStart + length <= nodes.length; ++candidateStart) {
        int candidateEnd = candidateStart + length;
        for (int k = 0; k < candidateStart; ++k) {
          if (governors[k] - 1 >= candidateStart && governors[k] - 1 < candidateEnd) {
            continue LOOP;
          }
        }
        for (int k = candidateEnd; k < nodes.length; ++k) {
          if (governors[k] - 1 >= candidateStart && governors[k] - 1 < candidateEnd) {
            continue LOOP;
          }
        }
        callback.accept(Pair.makePair(candidateStart, candidateEnd));
      }
    }
  }

  private int findRootOfSegment(int[] parents, int[] segments, int i) {
    int currentToken = segments[i];
    int lastI = i;
    i = parents[i] - 1;
    while (i >= 0 && segments[i] == currentToken) {
      lastI = i;
      i = parents[i] - 1;
    }
    return lastI;
  }

  @SuppressWarnings("unchecked")
  private void indexSpan(List<Word> newWords, int[] indexMap, int spanStart, int spanEnd, Function<String, Integer> indexer) {
    boolean needFill = true;
    for (int i = spanStart; i < spanEnd; ++i) {
      if (indexMap[i] != -1) {
        needFill = false;
      }
    }
    if (needFill) {
      Optional<Word> wordForSpan = Word.compute((DependencyTree<String>) this, spanStart, spanEnd, indexer);
      if (wordForSpan.isPresent()) {
        for (int i = spanStart; i < spanEnd; ++i) {
          indexMap[i] = newWords.size();
        }
        newWords.add(wordForSpan.get());
      }
    }

  }


  /** Convert this tree into a DependencyTree of Words */
  public DependencyTree<Word> process(Function<String,Integer> indexer) {
    // -- Index --
    int[] indexMap = new int[nodes.length];
    Arrays.fill(indexMap, -1);
    List<Word> newWords = new ArrayList<>();
    // First index by constituents
    foreachSpan( span -> { if (span.second - span.first > 1) { indexSpan(newWords, indexMap, span.first, span.second, indexer); } });
    // Then index by spans
    for (int length = nodes.length; length > 0; --length) {
      for (int candidateStart = 0; candidateStart + length <= nodes.length; ++candidateStart) {
        indexSpan(newWords, indexMap, candidateStart, candidateStart + length, indexer);
      }
    }

    // -- Fill In UNKs --
    for (int i = 0; i < indexMap.length; ++i) {
      if (indexMap[i] == -1) {
        indexMap[i] = newWords.size();
        newWords.add(Word.UNK);
      }
    }

    // -- Fix Edges --
    Word[] nodes = new Word[newWords.size()];
    int[] governors = new int[newWords.size()];
    String[] governingRelations = new String[newWords.size()];
    int[] oldToNewIndex = new int[this.nodes.length];
    int outputI = 0;
    // First pass: cluster edges
    for (int i = 0; i < indexMap.length; ++i) {
      oldToNewIndex[i] = outputI;
      if (i > 0 && indexMap[i - 1] == indexMap[i]) { continue; }
      nodes[outputI] = newWords.get(indexMap[i]);
      int segmentRoot = findRootOfSegment(this.governors, indexMap, i);
      governors[outputI] = this.governors[segmentRoot];
      governingRelations[outputI] = this.governingRelations[segmentRoot];
      outputI += 1;
    }
    // Second pass: fix indices
    for (int i = 0; i < governors.length; ++i) {
      if (governors[i] != 0) {
        governors[i] = oldToNewIndex[governors[i] - 1] + 1;
      }
    }
    // TODO(gabor) fix the word spans to match the new indices

    // -- Return --
    return new DependencyTree<>(nodes, governors, governingRelations);
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < nodes.length; ++i) {
      // Basic info
      b.append(nodes[i].toString().replace("\t", "\\t"))
          .append("\t").append(governors[i])
          .append("\t").append(governingRelations[i].replace("\t", "\\t"));
      // Additional info
      if (nodes[i] instanceof Word) {
        Word node = (Word) nodes[i];
        // (subject mono)
        if (node.subjMonotonicity == Monotonicity.INVALID) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(Quantifier.monotonicitySignature(node.subjMonotonicity, node.subjType));
        }
        // (subject span)
        if (node.subjSpan == Word.NO_SPAN) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(node.subjSpan[0]).append("-").append(node.subjSpan[1]);
        }
        // (object mono)
        if (node.objMonotonicity == Monotonicity.INVALID) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(Quantifier.monotonicitySignature(node.objMonotonicity, node.objType));
        }
        // (object span)
        if (node.objSpan == Word.NO_SPAN) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(node.objSpan[0]).append("-").append(node.objSpan[1]);
        }
      }
      // Trailing newline
      b.append("\n");
    }
    return b.toString();
  }
}
