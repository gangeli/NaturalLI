package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

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

  private static final String QUANTIFIER;

  static {
    String quantifiers = "{lemma:/all|a/}";
    QUANTIFIER = quantifiers;
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
  }});

  private static Pair<Integer, Integer> getSubtreeSpan(SemanticGraph tree, IndexedWord root) {
    int min = root.index();
    int max = root.index();
    Queue<IndexedWord> fringe = new LinkedList<>();
    fringe.addAll(tree.getChildren(root));
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.poll();
      min = Math.min(node.index(), min);
      max = Math.max(node.index(), max);
      fringe.addAll(tree.getChildren(node));
    }
    return Pair.makePair(min, max + 1);
  }

  private static Pair<Integer, Integer> includeInSpan(Pair<Integer, Integer> span, int index) {
    if (span.first > index) {
      return Pair.makePair(index, span.second);
    } else if (span.second <= index) {
      return Pair.makePair(span.first, index + 1);
    } else {
      return span;
    }
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

  private QuantifierScope computeScope(SemanticGraph tree, IndexedWord pivot, IndexedWord quantifier, IndexedWord subject, IndexedWord object) {
    Pair<Integer, Integer> subjectSubtree = getSubtreeSpan(tree, subject);
    Pair<Integer, Integer> subjSpan = excludeFromSpan(subjectSubtree, getSubtreeSpan(tree, quantifier));
    Pair<Integer, Integer> objSpan = excludeFromSpan(includeInSpan(getSubtreeSpan(tree, object), pivot.index()), subjectSubtree);
    return new QuantifierScope(subjSpan.first - 1, subjSpan.second - 1, objSpan.first - 1, objSpan.second - 1);
  }

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    for (SemgrexPattern pattern : PATTERNS) {
      SemgrexMatcher matcher = pattern.matcher(tree);
      while (matcher.find()) {
        IndexedWord quantifier = matcher.getNode("quantifier");
        QuantifierScope scope = computeScope(tree, matcher.getNode("pivot"), quantifier, matcher.getNode("subject"), matcher.getNode("object"));
        sentence.get(CoreAnnotations.TokensAnnotation.class).get(quantifier.index() - 1).set(QuantifierScopeAnnotation.class, scope);
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
