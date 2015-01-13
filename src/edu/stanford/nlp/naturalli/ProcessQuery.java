package edu.stanford.nlp.naturalli;

import edu.smu.tspell.wordnet.Synset;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Pointer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Run the NLP components for pre-processing a query.
 *
 * @author Gabor Angeli
 */
public class ProcessQuery {

  /**
   * Iterate over the constituent spans of this dependency tree, from largest to smallest in span.
   *
   * @param sentence The sentence, as a list of tokens.
   * @param parentIndex The tree, encoded as an array of parent indices.
   * @param callback The callback to call for each span of the sentence, as indexed into the sentence variable
   *                 (i.e., 0 indexed; ignores punctuation).
   */
  private static void foreachSpan(List<IndexedWord> sentence,
                                  int[] parentIndex, BiConsumer<Integer,Integer> callback) {
    // Iterate over spans
    for (int length = sentence.size(); length > 0; --length) {
      LOOP: for (int candidateStart = 0; candidateStart + length <= sentence.size(); ++candidateStart) {
        int candidateEnd = candidateStart + length;
        if (length > 1) {  // length 1 words are always constituents
          // A span must have a unique root element.
          int root = -42;
          for (int k = candidateStart; k < candidateEnd; ++k) {
            if (parentIndex[k] < candidateStart || parentIndex[k] >= candidateEnd) {
              if (root != -42) {
                continue LOOP;
              }
              root = k;
            }
          }
        }
        // This is a valid span; accept it.
        callback.accept(candidateStart, candidateEnd);
      }
    }
  }

  /**
   * Compute the most likely sense for a given word.
   *
   * @param wordAsInt The indexed word we are computing the sense for.
   * @param gloss The gloss of the word we are computing the sense for.
   * @param sentence The sentence this word is in (for POS tags).
   * @param words The context this word is in (for Lesk context).
   * @param spanStart The start span of the word we are getting the sense of.
   * @param spanEnd The end span of the word we are getting the sense of.
   *
   * @return The sense of the given word.
   */
  private static int computeSense(int wordAsInt, String gloss, List<IndexedWord> sentence, List<String> words,
                                  int spanStart, int spanEnd) {
    // Check if its a quantifire
    if (Operator.GLOSSES.contains(gloss.toLowerCase())) {
      return 0;
    }

    // Get the best Synset
    Optional<Synset> synset;
    if (sentence.get(spanStart).tag() != null) {
      Counter<String> posCounts = new ClassicCounter<>();
      for (int i = spanStart; i < spanEnd; ++i) {
        String pos = sentence.get(i).tag();
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
      synset = WSD.sense(words, spanStart, spanEnd, pos);
    } else {
      synset = WSD.sense(words, spanStart, spanEnd, "???");
    }

    // match the Synset to an index
    if (synset.isPresent()) {
      return Optional.ofNullable(StaticResources.SENSE_INDEXER.get(wordAsInt)).map(x -> x.get(synset.get().getDefinition())).orElse(0);
    } else {
      return 0;
    }
  }

  /**
   * A simple representation of a token, fit to be dumped to a CoNLL string.
   */
  private static class Token implements Comparable<Token> {
    public final String gloss;
    public final int word;
    public final int sense;
    public final Optional<OperatorSpec> operatorInfo;
    public final int originalStartIndex;
    public final int originalEndIndex;

    public Token(String gloss, int word, int sense, Optional<OperatorSpec> operatorInfo, int originalStartIndex, int originalEndIndex) {
      this.gloss = gloss;
      this.word = word;
      this.sense = sense;
      this.operatorInfo = operatorInfo;
      this.originalStartIndex = originalStartIndex;
      this.originalEndIndex = originalEndIndex;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(Token o) {
      return this.originalStartIndex - o.originalStartIndex;
    }
  }

  /**
   * Create a new dummy UNK token.
   * @param originalIndex The index of the token in the original sentence, 0 indexed.
   * @return A token corresponding to the UNK word.
   */
  private static Token UNK(int originalIndex) {
    return new Token("__UNK__", 1, 0, Optional.empty(), originalIndex, originalIndex + 1);
  }

  private static int originalToTokenizedIndex(List<Token> newTokens, int originalIndex) {
    // Corner case: end of the sentence
    if (originalIndex == newTokens.get(newTokens.size() - 1).originalEndIndex) {
      return newTokens.size();
    }
    // Else, search through the sentence for the right index
    int newIndex = -1;
    for (int k = 0; k < newTokens.size(); ++k) {
      if (originalIndex >= newTokens.get(k).originalStartIndex &&
          originalIndex < newTokens.get(k).originalEndIndex) {
         newIndex = k;
      }
    }
    assert newIndex >= 0;
    return newIndex;
  }

  /**
   * Perform a number of sanity rewrites on the incoming tree. For instance:
   *
   * <ul>
   *   <li>Replace arcs into opreators with the special 'op' arc.</li>
   *   <li>Replace 'prepc_' with 'prep_'</li>
   *   <li>Filter out unknown arcs.</li>
   * </ul>
   *
   * @param tree The tree to sanitize IN PLACE.
   */
  private static void sanitizeTreeForNaturalli(SemanticGraph tree) {
    List<SemanticGraphEdge> toRemove = new ArrayList<>();
    List<SemanticGraphEdge> toAdd = new ArrayList<>();

    // Register rewrites
    for (SemanticGraphEdge edge : tree.edgeIterable()) {
      String rel = edge.getRelation().toString().toLowerCase();
      if (edge.getDependent().backingLabel().get(NaturalLogicAnnotations.OperatorAnnotation.class) != null) {
        // Replace arcs into operators with 'op'
        toRemove.add(edge);
        synchronized (SemanticGraphEdge.class) {
          toAdd.add(new SemanticGraphEdge(edge.getGovernor(), edge.getDependent(), GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "op"), edge.getWeight(), edge.isExtra()));
        }
      } else if (rel.startsWith("prepc_")) {
        // Rewrite 'prepc_' edges to 'prep_'
        String newRel = rel.replaceAll("prepc_", "prep_");
        toRemove.add(edge);
        synchronized (SemanticGraphEdge.class) {
          toAdd.add(new SemanticGraphEdge(edge.getGovernor(), edge.getDependent(), GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, newRel), edge.getWeight(), edge.isExtra()));
        }
      }
    }

    // Perform rewrites
    for (SemanticGraphEdge e : toRemove) {
      tree.removeEdge(e);
    }
    for (SemanticGraphEdge e : toAdd) {
      tree.addEdge(e.getGovernor(), e.getDependent(), e.getRelation(), e.getWeight(), e.isExtra());
    }
    toRemove.clear();
    toAdd.clear();

    // Filter unknown edges
    for (SemanticGraphEdge edge : tree.edgeIterable()) {
      String rel = edge.getRelation().toString().toLowerCase();
      if (!NaturalLogicRelation.knownDependencyArc(rel)) {
        toRemove.add(edge);
        if (rel.startsWith("prep_")) {
          toAdd.add(new SemanticGraphEdge(edge.getGovernor(), edge.getDependent(), GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "prep_dep"), edge.getWeight(), edge.isExtra()));
        } else {
          toAdd.add(new SemanticGraphEdge(edge.getGovernor(), edge.getDependent(), GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "dep"), edge.getWeight(), edge.isExtra()));
        }
      }
    }

    // Perform rewrites (pass 2)
    for (SemanticGraphEdge e : toRemove) {
      tree.removeEdge(e);
    }
    for (SemanticGraphEdge e : toAdd) {
      tree.addEdge(e.getGovernor(), e.getDependent(), e.getRelation(), e.getWeight(), e.isExtra());
    }
  }

  public static String conllDump(SemanticGraph tree) {
    return conllDump(tree, new Pointer<>());
  }

  public static String conllDump(SemanticGraph tree, Pointer<String> readableDump) {
    sanitizeTreeForNaturalli(tree);
    // Variables
    List<IndexedWord> sentence = new ArrayList<>();
    tree.vertexListSorted().forEach(sentence::add);
    List<String> words = sentence.stream().map(IndexedWord::lemma).collect(Collectors.toList());

    // Compute the tree to sentence index
    int[] treeToSentenceIndex = new int[sentence.get(sentence.size() - 1).index() + 1];
    for (int i = 0; i < sentence.size(); ++i ) {
      if (i > 0) {
        for (int k = sentence.get(i - 1).index(); k < sentence.get(i).index() - 1; ++k) {
          treeToSentenceIndex[k] = i;
        }
      }
      treeToSentenceIndex[sentence.get(i).index() - 1] = i;
    }
    treeToSentenceIndex[sentence.get(sentence.size() - 1).index()] = sentence.size();

    // Compute the parents in a more readable form
    int[] parentIndex = new int[sentence.size()];
    for (int i = 0; i < sentence.size(); ++i) {
      if (tree.getRoots().contains(sentence.get(i))) {
        parentIndex[i] = -1;
      } else {
        parentIndex[i] = treeToSentenceIndex[tree.incomingEdgeIterator(sentence.get(i)).next().getGovernor().index() - 1];
      }
    }

    // Compute possible spans
    List<Token> conllTokenByStartIndex = new ArrayList<>();
    BitSet accountedFor = new BitSet();
    foreachSpan(sentence, parentIndex, (start, end) -> {
      // Index the word
      String gloss = String.join(" ", words.subList(start, end));
      int wordAsInt = StaticResources.INDEXER.apply(gloss);
      if (wordAsInt >= 0) {
        // Find the word sense of the word
        int sense = computeSense(wordAsInt, gloss, sentence, words, start, end);
        // Find any operators, and check if we've already covered this token
        Optional<OperatorSpec> operator = Optional.empty();
        boolean add = true;
        for (int k = start; k < end; ++k) {
          if (accountedFor.get(k)) {
            add = false;
          }
          accountedFor.set(k);
          if (!operator.isPresent()) {
            operator = Optional.ofNullable(sentence.get(k).get(NaturalLogicAnnotations.OperatorAnnotation.class));
          }
        }
        // Add the token
        if (add) {
          conllTokenByStartIndex.add(new Token(gloss, wordAsInt, sense, operator, start, end));
        }
      }
    });

    // Add UNKs
    for (int k = 0; k < sentence.size(); ++k) {
      if (!accountedFor.get(k)) {
        conllTokenByStartIndex.add(UNK(k));
      }
    }
    // Sort the tokens
    Collections.sort(conllTokenByStartIndex);

    // Find the incoming edge of every token
    List<Pair<Integer,String>> governors = conllTokenByStartIndex.stream().map( token -> {
      // Find the root of the token
      int root = -42;
      for (int k = token.originalStartIndex; k < token.originalEndIndex; ++k) {
        if (parentIndex[k] < token.originalStartIndex || parentIndex[k] >= token.originalEndIndex) {
          root = k;
        }
      }
      assert root != -42;
      // Find the incoming edge of that root
      String incomingEdge = "root";
      int governorIndex = -1;
      if (parentIndex[root] >= 0) {
        SemanticGraphEdge incomingSemGraphEdge = tree.incomingEdgeIterator(sentence.get(root)).next();
        incomingEdge = incomingSemGraphEdge.getRelation().toString();
        governorIndex = originalToTokenizedIndex(conllTokenByStartIndex, treeToSentenceIndex[incomingSemGraphEdge.getGovernor().index() - 1]);
      }
      return Pair.makePair(governorIndex, incomingEdge);
    }).collect(Collectors.toList());

    // Dump String
    StringBuilder production = new StringBuilder();
    StringBuilder debug = new StringBuilder();
    for (int i = 0; i < conllTokenByStartIndex.size(); ++i) {
      Token token = conllTokenByStartIndex.get(i);
      Pair<Integer, String> incomingEdge = governors.get(i);
      // Encode tree
      production.append(token.word);
      debug.append(token.gloss.replace("\\t", " "));//.append("\t").append(token.word);
      StringBuilder b = new StringBuilder();
      b.append("\t").append(incomingEdge.first + 1)
          .append("\t").append(incomingEdge.second.replace("\t", "\\t"));
      // Word sense
      b.append("\t").append(token.sense);
      // Operator info
      if (token.operatorInfo.isPresent()) {
        // (if present)
        OperatorSpec operatorInfo = token.operatorInfo.get();
        Operator operator = operatorInfo.instance;
        if (operatorInfo.subjectBegin >= treeToSentenceIndex.length || treeToSentenceIndex[operatorInfo.subjectBegin] >= sentence.size()) {
          b.append("\t-\t-\t-\t-");  // The operator is actually just wiped out
        } else {
          assert operatorInfo.subjectBegin < treeToSentenceIndex.length;
          b.append("\t").append(Operator.monotonicitySignature(operator.subjMono, operator.subjType));
          b.append("\t")
              .append(originalToTokenizedIndex(conllTokenByStartIndex, treeToSentenceIndex[operatorInfo.subjectBegin]) + 1)
              .append("-")
              .append(originalToTokenizedIndex(conllTokenByStartIndex, treeToSentenceIndex[Math.min(operatorInfo.subjectEnd, treeToSentenceIndex.length - 1)]) + 1);
          if (operator.isUnary()) {
            b.append("\t-\t-");
          } else {
            if (operatorInfo.objectBegin >= treeToSentenceIndex.length || treeToSentenceIndex[operatorInfo.objectBegin] >= sentence.size()) {
              b.append("\t-\t-");  // Case: we chopped off the second half of a quantifier scope  (all cats like dogs -> all cats)
            } else {
              assert operatorInfo.objectBegin < treeToSentenceIndex.length;
              b.append("\t").append(Operator.monotonicitySignature(operator.objMono, operator.objType));
              b.append("\t")
                  .append(originalToTokenizedIndex(conllTokenByStartIndex, treeToSentenceIndex[operatorInfo.objectBegin]) + 1)
                  .append("-")
                  .append(originalToTokenizedIndex(conllTokenByStartIndex, treeToSentenceIndex[Math.min(operatorInfo.objectEnd, treeToSentenceIndex.length - 1)]) + 1);
            }
          }
        }
      } else {
        // (if not present)
        b.append("\t-\t-\t-\t-");
      }
      // Trailing newline
      b.append("\n");
      production.append(b.toString());
      debug.append(b.toString());
    }
    readableDump.set(debug.toString());
    return production.toString();

  }

  protected static StanfordCoreNLP constructPipeline() {
    Properties props = new Properties() {{
      setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,natlog");
      setProperty("depparse.extradependencies", "ref_only_collapsed");
      setProperty("ssplit.isOneSentence", "true");
      setProperty("tokenize.class", "PTBTokenizer");
      setProperty("tokenize.language", "en");
      setProperty("naturalli.doPolarity", "false");
    }};
    return new StanfordCoreNLP(props);
  }

  protected static String annotateHumanReadable(String line, StanfordCoreNLP pipeline) {
    Pointer<String> debug = new Pointer<>();
    annotate(line, pipeline, debug);
    return debug.dereference().get();
  }

  protected static String annotate(String line, StanfordCoreNLP pipeline) {
    return annotate(line, pipeline, new Pointer<>());
  }

  protected static String annotate(String line, StanfordCoreNLP pipeline, Pointer<String> debugDump) {
    Annotation ann = new Annotation(line);
    pipeline.annotate(ann);
    SemanticGraph dependencies = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    Util.cleanTree(dependencies);  // note: in place!
    return conllDump(dependencies, debugDump);
  }

  public static void main(String[] args) throws IOException {
    // Create pipeline
    StanfordCoreNLP pipeline = constructPipeline();

    // Read input
    System.err.println(ProcessQuery.class.getSimpleName() + " is ready for input");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.trim().equals("")) {
        System.err.println("Annotating '" + line + "'");
        String annotated = annotate(line, pipeline);
        System.out.println(annotated);
        System.out.flush();
      }
    }
  }
}
