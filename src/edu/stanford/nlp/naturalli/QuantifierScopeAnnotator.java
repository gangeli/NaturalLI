package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.NumberAnnotator;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;

import java.util.*;
import java.util.function.Function;

/**
 * An annotator marking quantifiers with their scope.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unchecked")
public class QuantifierScopeAnnotator extends SentenceAnnotator {

  /**
   * An annotation which attaches to a CoreLabel to denote that quantifier's scope in the sentence
   */
  public static final class QuantifierAnnotation implements CoreAnnotation<QuantifierSpec> {
    @Override
    public Class<QuantifierSpec> getType() {
      return QuantifierSpec.class;
    }
  }

  /**
   * A regex for arcs that act as determiners
   */
  private static final String DET = "/(pre)?det|a(dv)?mod|neg|num/";
  /**
   * A regex for arcs that we pretend are subject arcs
   */
  private static final String GEN_SUBJ = "/[ni]subj/";
  /**
   * A regex for arcs that we pretend are object arcs
   */
  private static final String GEN_OBJ = "/[di]obj|xcomp/";
  /**
   * A regex for arcs that we pretend are copula
   */
  private static final String GEN_COP = "/cop|aux/";

  /**
   * A Semgrex fragment for matching a quantifier
   */
  private static final String QUANTIFIER;

  static {
    Set<String> singleWordQuantifiers = new HashSet<>();
    for (Quantifier q : Quantifier.values()) {
      String[] tokens = q.surfaceForm.split("\\s+");
      if (!tokens[tokens.length - 1].startsWith("_")) {
        singleWordQuantifiers.add("(" + tokens[tokens.length - 1].toLowerCase() + ")");
      }
    }
    QUANTIFIER = "[ {lemma:/" + StringUtils.join(singleWordQuantifiers, "|") + "/}=quantifier | {pos:CD}=quantifier ]";
  }

  /**
   * The patterns to use for marking quantifier scopes.
   */
  private static final List<SemgrexPattern> PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    // { All cats eat mice,
    //   All cats want milk }
    add(SemgrexPattern.compile("{}=pivot >"+GEN_SUBJ+" ({}=subject >"+DET+" "+QUANTIFIER+") >"+GEN_OBJ+" {}=object"));
    // { All cats are in boxes,
    //   All cats voted for Obama }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" ({}=subject >"+DET+" "+QUANTIFIER+") >/prep/ {}=object"));
    // { All cats are cute,
    //   All cats can purr }
    add(SemgrexPattern.compile("{}=object >"+GEN_SUBJ+" ({}=subject >"+DET+" "+QUANTIFIER+") >"+GEN_COP+" {}=pivot"));
    // { Felix likes cat food }
    add(SemgrexPattern.compile("{}=pivot >"+GEN_SUBJ+" {pos:NNP}=Subject >"+GEN_OBJ+" {}=object"));
    // { Some cats do n't like dogs }
    add(SemgrexPattern.compile("{}=pivot >neg "+QUANTIFIER+" >"+GEN_OBJ+" {}=object"));
  }});

  /** A helper method for
   * {@link edu.stanford.nlp.naturalli.QuantifierScopeAnnotator#getModifierSubtreeSpan(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)} and
   * {@link edu.stanford.nlp.naturalli.QuantifierScopeAnnotator#getSubtreeSpan(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)}.
   */
  private static Pair<Integer, Integer> getGeneralizedSubtreeSpan(SemanticGraph tree, IndexedWord root, Set<String> validArcs) {
    int min = root.index();
    int max = root.index();
    Queue<IndexedWord> fringe = new LinkedList<>();
    for (SemanticGraphEdge edge : tree.getOutEdgesSorted(root)) {
      String edgeLabel = edge.getRelation().getShortName();
      if (validArcs == null || validArcs.contains(edgeLabel)) {
        fringe.add(edge.getDependent());
      }
    }
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.poll();
      min = Math.min(node.index(), min);
      max = Math.max(node.index(), max);
      fringe.addAll(tree.getChildren(node));
    }
    return Pair.makePair(min, max + 1);
  }

  private static final Set<String> MODIFIER_ARCS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("aux");
    add("prep");
  }});

  /**
   * Returns the yield span for the word rooted at the given node, but only traversing a fixed set of relations.
   * @param tree The dependency graph to get the span from.
   * @param root The root word of the span.
   * @return A one indexed span rooted at the given word.
   */
  private static Pair<Integer, Integer> getModifierSubtreeSpan(SemanticGraph tree, IndexedWord root) {
    return getGeneralizedSubtreeSpan(tree, root, MODIFIER_ARCS);
  }

  /**
   * Returns the yield span for the word rooted at the given node. So, for example, all cats like dogs rooted at the word
   * "cats" would yield a span (1, 3) -- "all cats".
   * @param tree The dependency graph to get the span from.
   * @param root The root word of the span.
   * @return A one indexed span rooted at the given word.
   */
  private static Pair<Integer, Integer> getSubtreeSpan(SemanticGraph tree, IndexedWord root) {
    return getGeneralizedSubtreeSpan(tree, root, null);
  }

  /**
   * Effectively, merge two spans
   */
  private static Pair<Integer, Integer> includeInSpan(Pair<Integer, Integer> span, Pair<Integer, Integer> toInclude) {
    return Pair.makePair(Math.min(span.first, toInclude.first), Math.max(span.second, toInclude.second));
  }

  /**
   * Exclude the second span from the first, if the second is on the edge of the first. If the second is in the middle, it's
   * unclear what this function should do, so it just returns the original span.
   */
  private static Pair<Integer, Integer> excludeFromSpan(Pair<Integer, Integer> span, Pair<Integer, Integer> toExclude) {
    if (toExclude.second <= span.first || toExclude.first >= span.second) {
      // Case: toExclude is outside of the span anyways
      return span;
    } else if (toExclude.first <= span.first && toExclude.second > span.first) {
      // Case: overlap on the front
      return Pair.makePair(toExclude.second, span.second);
    } else if (toExclude.first < span.second && toExclude.second >= span.second) {
      // Case: overlap on the front
      return Pair.makePair(span.first, toExclude.first);
    } else if (toExclude.first > span.first && toExclude.second < span.second) {
      // Case: toExclude is within the span
      return span;
    } else {
      throw new IllegalStateException("This case should be impossible");
    }
  }

  /**
   * Compute the span for a given matched pattern.
   * At a high level:
   *
   * <ul>
   *   <li>If both a subject and an object exist, we take the subject minus the quantifier, and the object plus the pivot. </li>
   *   <li>If only an object exists, we make the subject the object, and create a dummy object to signify a one-place quantifier. </li>
   * </ul>
   *
   * But:
   *
   * <ul>
   *   <li>If we have a two-place quantifier, the object is allowed to absorb various specific arcs from the pivot.</li>
   *   <li>If we have a one-place quantifier, the object is allowed to absorb only prepositions from the pivot.</li>
   * </ul>
   */
  private QuantifierSpec computeScope(SemanticGraph tree, Quantifier quantifier,
                                      IndexedWord pivot, Pair<Integer, Integer> quantifierSpan,
                                      IndexedWord subject, IndexedWord object) {
    Pair<Integer, Integer> subjSpan;
    Pair<Integer, Integer> objSpan;
    if (subject != null) {
      Pair<Integer, Integer> subjectSubtree = getSubtreeSpan(tree, subject);
      subjSpan = excludeFromSpan(subjectSubtree, quantifierSpan);
      objSpan = excludeFromSpan(includeInSpan(getSubtreeSpan(tree, object), getModifierSubtreeSpan(tree, pivot)), subjectSubtree);
    } else {
      subjSpan = includeInSpan(getSubtreeSpan(tree, object), getGeneralizedSubtreeSpan(tree, pivot, Collections.singleton("prep")));
      objSpan = Pair.makePair(subjSpan.second, subjSpan.second);
    }
    return new QuantifierSpec(quantifier,
        quantifierSpan.first - 1, quantifierSpan.second - 1,
        subjSpan.first - 1, subjSpan.second - 1,
        objSpan.first - 1, objSpan.second - 1);
  }

  /**
   * Try to find which quantifier we matched, given that we matched the head of a quantifier at the given IndexedWord, and that
   * this whole deal is taking place in the given sentence.
   *
   * @param sentence The sentence we are matching.
   * @param quantifier The word at which we matched a quantifier.
   * @return An optional triple consisting of the particular quantifier we matched, as well as the span of that quantifier in the sentence.
   */
  private Optional<Triple<Quantifier,Integer,Integer>> validateQuantiferByHead(CoreMap sentence, IndexedWord quantifier) {
    int end = quantifier.index();
    for (int start = Math.max(0, end - 10); start < end; ++start) {
      Function<CoreLabel,String> glossFn = (label) -> "CD".equals(label.tag()) ? "__NUM__" : label.lemma();
      String gloss = StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class), " ", glossFn, start, end).toLowerCase();
      for (Quantifier q : Quantifier.values()) {
        if (q.surfaceForm.equals(gloss)) {
          return Optional.of(Triple.makeTriple(q, start + 1, end + 1));
        }
      }
    }
    return Optional.empty();
  }

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    for (SemgrexPattern pattern : PATTERNS) {
      SemgrexMatcher matcher = pattern.matcher(tree);
      while (matcher.find()) {

        // Get terms
        IndexedWord properSubject = matcher.getNode("Subject");
        IndexedWord quantifier, subject;
        boolean namedEntityQuantifier = false;
        if (properSubject != null) {
          quantifier = subject = properSubject;
          namedEntityQuantifier = true;
        } else {
          quantifier = matcher.getNode("quantifier");
          subject = matcher.getNode("subject");
        }

        // Validate quantifier
        Optional<Triple<Quantifier,Integer,Integer>> quantifierInfo;
        if (namedEntityQuantifier) {
          // named entities have the "all" semantics by default.
          quantifierInfo = Optional.of(Triple.makeTriple(Quantifier.ALL, quantifier.index(), quantifier.index()));  // note: empty quantifier span given
        } else {
          // find the quantifier, and return some info about it.
          quantifierInfo = validateQuantiferByHead(sentence, quantifier);
        }

        // Set tokens
        if (quantifierInfo.isPresent()) {
          // Compute span
          QuantifierSpec scope = computeScope(tree, quantifierInfo.get().first,
              matcher.getNode("pivot"), Pair.makePair(quantifierInfo.get().second, quantifierInfo.get().third), subject, matcher.getNode("object"));
          // Set annotation
          CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(quantifier.index() - 1);
          QuantifierSpec oldScope = token.get(QuantifierAnnotation.class);
          if (oldScope == null || oldScope.quantifierLength() < scope.quantifierLength() ||
              oldScope.instance != scope.instance) {
            token.set(QuantifierAnnotation.class, scope);
          } else {
            token.set(QuantifierAnnotation.class, QuantifierSpec.merge(oldScope, scope));
          }
        }
      }
    }

    // Ensure we didn't select overlapping quantifiers. For example, "a" and "a few" can often overlap.
    // In these cases, take the longer quantifier match.
    List<QuantifierSpec> quantifiers = new ArrayList<>();
    for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
      if (token.has(QuantifierAnnotation.class)) {
        quantifiers.add(token.get(QuantifierAnnotation.class));
      }
    }
    quantifiers.sort( (x, y) -> y.quantifierLength() - x.quantifierLength());
    for (QuantifierSpec quantifier : quantifiers) {
      for (int i = quantifier.quantifierBegin; i < quantifier.quantifierEnd; ++i) {
        if (i != quantifier.quantifierHead) {
          sentence.get(CoreAnnotations.TokensAnnotation.class).get(i).remove(QuantifierAnnotation.class);
        }
      }
    }
  }

  @Override
  protected int nThreads() {
    return 1;
  }

  @Override
  protected long maxTime() {
    return Long.MAX_VALUE;
  }

  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    System.err.println("Failed to annotate: " + sentence.get(CoreAnnotations.TextAnnotation.class));
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.EMPTY_SET;
  }

  @Override
  public Set<Requirement> requires() {
    return TOKENIZE_SSPLIT_PARSE;
  }
}
