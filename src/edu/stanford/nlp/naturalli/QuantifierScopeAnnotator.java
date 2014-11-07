package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;

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
  public static final class QuantifierScopeAnnotation implements CoreAnnotation<QuantifierScope> {
    @Override
    public Class<QuantifierScope> getType() {
      return QuantifierScope.class;
    }
  }

  /**
   * A regex for arcs that act as determiners
   */
  private static final String DET = "/(pre)?det/";
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
    // Simple case: single-word quantifiers
    List<String> singleWordQuantifiers = new ArrayList<>();
    for (Quantifier q : Quantifier.values()) {
      if (!q.surfaceForm.contains(" ")) {
        singleWordQuantifiers.add("(" + q.surfaceForm.toLowerCase() + ")");
      }
    }
    String simpleQuantifiers = "{lemma:/" + StringUtils.join(singleWordQuantifiers, "|") + "/}";
    // Complex case: dependency path quantifiers
    // TODO(gabor)
    // Create Semgrex pattern
    QUANTIFIER = simpleQuantifiers;
  }

  /**
   * The patterns to use for marking quantifier scopes.
   */
  private static final List<SemgrexPattern> PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    // { All cats eat mice,
    //   All cats want milk }
    add(SemgrexPattern.compile("{}=pivot >"+GEN_SUBJ+" ({}=subject >"+DET+" "+QUANTIFIER+"=quantifier) >"+GEN_OBJ+" {}=object"));
    // { All cats are in boxes,
    //   All cats voted for Obama }
    add(SemgrexPattern.compile("{pos:/V.*/}=pivot >"+GEN_SUBJ+" ({}=subject >"+DET+" "+QUANTIFIER+"=quantifier) >/prep/ {}=object"));
    // { All cats are cute,
    //   All cats can purr }
    add(SemgrexPattern.compile("{}=object >"+GEN_SUBJ+" ({}=subject >"+DET+" "+QUANTIFIER+"=quantifier) >"+GEN_COP+" {}=pivot"));
    // { Felix likes cat food }
    add(SemgrexPattern.compile("{}=pivot >"+GEN_SUBJ+" {pos:NNP}=Subject >"+GEN_OBJ+" {}=object"));
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
    add("aux"); add("prep");
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

  private static Pair<Integer, Integer> includeInSpan(Pair<Integer, Integer> span, Pair<Integer, Integer> toInclude) {
    return Pair.makePair(Math.min(span.first, toInclude.first), Math.max(span.second, toInclude.second));
  }

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

  private QuantifierScope computeScope(SemanticGraph tree, IndexedWord pivot, IndexedWord quantifier,
                                       IndexedWord subject, IndexedWord object, boolean cutQuantifier) {
    Pair<Integer, Integer> subjectSubtree = getSubtreeSpan(tree, subject);
    Pair<Integer, Integer> subjSpan;
    if (cutQuantifier) {
      subjSpan = excludeFromSpan(subjectSubtree, getSubtreeSpan(tree, quantifier));
    } else {
      subjSpan = subjectSubtree;
    }
    Pair<Integer, Integer> objSpan = excludeFromSpan(
        includeInSpan(getSubtreeSpan(tree, object), getModifierSubtreeSpan(tree, pivot)),
        subjectSubtree);
    return new QuantifierScope(subjSpan.first - 1, subjSpan.second - 1, objSpan.first - 1, objSpan.second - 1);
  }

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    for (SemgrexPattern pattern : PATTERNS) {
      SemgrexMatcher matcher = pattern.matcher(tree);
      while (matcher.find()) {
        IndexedWord properSubject = matcher.getNode("Subject");
        IndexedWord quantifier, subject;
        boolean cutQuantifier = true;
        if (properSubject != null) {
          quantifier = subject = properSubject;
          cutQuantifier = false;
        } else {
          quantifier = matcher.getNode("quantifier");
          subject = matcher.getNode("subject");
        }
        QuantifierScope scope = computeScope(tree, matcher.getNode("pivot"), quantifier, subject, matcher.getNode("object"), cutQuantifier);
        CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(quantifier.index() - 1);
        QuantifierScope oldScope = token.get(QuantifierScopeAnnotation.class);
        if (oldScope == null) {
          token.set(QuantifierScopeAnnotation.class, scope);
        } else {
          token.set(QuantifierScopeAnnotation.class, QuantifierScope.merge(oldScope, scope));
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
