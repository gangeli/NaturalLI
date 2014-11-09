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
  public final Optional<String[]> posTags;
  public final int[] governors;
  public final String[] governingRelations;

  @SuppressWarnings("unchecked")
  public DependencyTree(SemanticGraph dependencies, List<CoreLabel> sentence) {
    // Map indices
    int[] oldToNewIndex = new int[sentence.size()];
    List<IndexedWord> sortedVertices = dependencies.vertexListSorted();
    for (int i = 0; i < sortedVertices.size(); ++i) {
      oldToNewIndex[sortedVertices.get(i).index() - 1] = i;
    }
    // Create structures
    nodes = (E[]) new String[dependencies.size()];
    posTags = Optional.of(new String[nodes.length]);
    Arrays.fill(nodes, "__UNK__");
    governors = new int[dependencies.size()];
    Arrays.fill(governors, -1);
    governingRelations = new String[dependencies.size()];
    Arrays.fill(governingRelations, "dep");
    // Copy into dependency tree
    // This is why I hate SemanticGraph as a representation...
    for (IndexedWord vertex : dependencies.vertexSet()) {
      nodes[oldToNewIndex[vertex.index() - 1]] = (E) sentence.get(vertex.index() - 1).lemma();
      posTags.get()[oldToNewIndex[vertex.index() - 1]] = sentence.get(vertex.index() - 1).tag();
      Collection<IndexedWord> parents = dependencies.getParents(vertex);
      assert parents.size() < 2;
      if (parents.size() == 1) {
        IndexedWord governor = parents.iterator().next();
        String incomingEdgeType = dependencies.getIncomingEdgesSorted(vertex).get(0).getRelation().getShortName();
        governors[oldToNewIndex[vertex.index() - 1]] = oldToNewIndex[governor.index() - 1];
        governingRelations[oldToNewIndex[vertex.index() - 1]] = incomingEdgeType;
      }
    }
    for (IndexedWord root : dependencies.getRoots()) {
      governingRelations[oldToNewIndex[root.index() - 1]] = "root";
    }
  }

  public DependencyTree(E[] nodes, int[] governors, String[] governingRelations) {
    this.nodes = nodes;
    this.posTags = Optional.empty();
    this.governors = governors;
    this.governingRelations = governingRelations;
  }

  public List<String> asList() {
    return new AbstractList<String>() {
      @Override
      public String get(int index) {
        if (nodes[index] instanceof Word) {
          return ((Word) nodes[index]).gloss;
        } else {
          return nodes[index].toString();
        }
      }

      @Override
      public int size() {
        return nodes.length;
      }
    };
  }

  private void foreachSpan(Consumer<Pair<Integer,Integer>> callback) {
    for (int length = nodes.length; length > 0; --length) {
      LOOP: for (int candidateStart = 0; candidateStart + length <= nodes.length; ++candidateStart) {
        int candidateEnd = candidateStart + length;
        for (int k = 0; k < candidateStart; ++k) {
          if (governors[k] >= candidateStart && governors[k] < candidateEnd) {
            continue LOOP;
          }
        }
        for (int k = candidateEnd; k < nodes.length; ++k) {
          if (governors[k] >= candidateStart && governors[k] < candidateEnd) {
            continue LOOP;
          }
        }
        callback.accept(Pair.makePair(candidateStart, candidateEnd));
      }
    }
  }

  /**
   * Find the root of the given segment.
   * This is a bit more complicated that one might think, because the segments don't have to align to dependency
   * constituents. For example, "used to" is not a dependency constituent, but is a single segment.
   *
   * @param parents The dependency structure of the original dependency tree. Everything is zero indexed; the root is -1
   * @param segments The segmentation of the dependency tree into chunks. That is, for each token in the original tree,
   *                 this is a mapping to an integer such that every token with that integer is in the same segment.
   * @param i The index of the token we are finding the root for. All indices with the same segment mapping should have the
   *          same root!
   * @return The root of this segment, zero indexed. This root cannot be -1; it must be a node in the tree.
   */
  private int findRootOfSegment(int[] parents, int[] segments, int i) {
    int segment = segments[i];
    int highestNodeInsideSegment = i;
    i = parents[i];
    while (i >= 0) {
      if (segments[i] == segment) {
        highestNodeInsideSegment = i;
      }
      i = parents[i];
    }
    return highestNodeInsideSegment;
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


  public Pair<Integer, Integer> getSubtreeSpan(int index) {
    int min = 999;
    int max = -999;
    for (int i = 0; i < nodes.length; ++i) {
      int k = i;
      while (k != -1) {
        if (k == index) {
          if (i < min) { min = i; }
          if (i > max) { max = i; }
          break;
        }
        k = this.governors[k];
      }
    }
    return Pair.makePair(min, max + 1);
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
    int[] oldToNewIndex = new int[this.nodes.length + 1];
    oldToNewIndex[this.nodes.length] = nodes.length;
    int outputI = 0;
    // First pass: cluster edges
    for (int i = 0; i < indexMap.length; ++i) {
      oldToNewIndex[i] = outputI;
      if (i < indexMap.length - 1 &&  indexMap[i] == indexMap[i+1]) { continue; }
      nodes[outputI] = newWords.get(indexMap[i]);
      int segmentRoot = findRootOfSegment(this.governors, indexMap, i);
      governors[outputI] = this.governors[segmentRoot];
      governingRelations[outputI] = this.governingRelations[segmentRoot];
      outputI += 1;
    }
    // Second pass: fix indices
    for (int i = 0; i < governors.length; ++i) {
      if (governors[i] != -1) {
        governors[i] = oldToNewIndex[governors[i]];
      }
    }
    // Third pass: fix spans
    for (int i = 0; i < nodes.length; ++i) {
      nodes[i] = nodes[i].updateSpans(oldToNewIndex);
    }

    // -- Return --
    return new DependencyTree<>(nodes, governors, governingRelations);
  }

  public String toString(boolean prettyPrint) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < nodes.length; ++i) {
      // Basic info
      if (prettyPrint && nodes[i] instanceof Word) {
        b.append(StaticResources.PHRASE_GLOSSER.get(((Word) nodes[i]).index));
      } else {
        b.append(nodes[i].toString().replace("\t", "\\t"));
      }
      b.append("\t").append(governors[i] + 1)
       .append("\t").append(governingRelations[i].replace("\t", "\\t"));
      // Additional info
      if (nodes[i] instanceof Word) {
        Word node = (Word) nodes[i];
        // (word sense)
        b.append("\t").append(node.sense);
        // (subject mono)
        if (node.subjMonotonicity == Monotonicity.INVALID) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(Operator.monotonicitySignature(node.subjMonotonicity, node.subjType));
        }
        // (subject span)
        if (node.subjSpan == Word.NO_SPAN) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(node.subjSpan[0] + 1).append("-").append(node.subjSpan[1] + 1);
        }
        // (object mono)
        if (node.objMonotonicity == Monotonicity.INVALID) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(Operator.monotonicitySignature(node.objMonotonicity, node.objType));
        }
        // (object span)
        if (node.objSpan == Word.NO_SPAN) {
          b.append("\t").append("-");
        } else {
          b.append("\t").append(node.objSpan[0] + 1).append("-").append(node.objSpan[1] + 1);
        }
      }
      // Trailing newline
      b.append("\n");
    }
    return b.toString();
  }

  @Override
  public String toString() {
    return toString(false);
  }
}
