package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class to rewrite sentences so they match the semantics of natural logic better.
 * The possible filters are:
 *
 * <ul>
 *   <li><b>therebe</b> Rewrites "there is" to a simple existential -- which helps match the dependency tree to what we want.</li>
 *   <li><b>alot</b> Rewrites a lot to be a dependent of the thing that is a lot. This allows it to be inserted / deleted as it should.</li>
 *   <li><b>rootedq</b> If a quantifier is the root of a sentence, shuffle the sentence to assign a different root.</li>
 * </ul>
 *
 * @author Gabor Angeli
 */
public class QRewrite {

  public static final QRewrite FOR_PREMISE = new QRewrite("therebe,mods,rootedq");
  public static final QRewrite FOR_QUERY   = new QRewrite("therebe,mods,rootedq");

  private Set<String> filters = new HashSet<>();

  public QRewrite(String filters) {
    this.filters.addAll(Arrays.asList(filters.split(",")));
  }

  /**
   * Rewrite the raw gloss of a sentence.
   * @param input The sentence to rewrite.
   * @return A potentially modified version of the sentence, intended to be better suited for natural logic.
   */
  public String rewriteGloss(String input) {
    if (filters.contains("therebe")) {
      input = rewriteThereBe(input);
    }
    return input;
  }

  /**
   * Rewrite the dependency tree of a sentence. THIS MAY OR MAY NOT BE IN PLACE.
   * Therefore, you should always assume the input tree has been modified, but also take the output of the function
   * nonetheless.
   *
   * @param tree The tree to rewrite. It will potentially be mutated.
   * @return A potentially new tree, intended to be better suited for inference.
   */
  public SemanticGraph rewriteDependencies(SemanticGraph tree) {
    if (filters.contains("mods")) {
      tree = rewriteMods(tree);
    }
    if (filters.contains("rootedq")) {
      tree = rewriteRootedQuantifier(tree);
    }
    if (filters.contains("dropdet")) {
      tree = rewriteDropDeterminers(tree);
    }
    return tree;
  }

  private static Pattern thereBePattern = Pattern.compile("^[Tt]here (?:is|are|was|were|will be|would be|has been|have been) (.*) (?:who|which) (.*)$");

  public static String rewriteThereBe(String input) {
    Matcher m = thereBePattern.matcher(input);
    if (m.matches()) {
      input = m.group(1) + " " + m.group(2);
      boolean wellScoped = false;
      for (Operator op : Operator.values()) {
        if (input.startsWith(op.surfaceForm)) {
          wellScoped = true;
          break;
        }
      }
      if (!wellScoped) {
        return "Some " + input;
      } else {
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
      }
    } else {
      return input;
    }
  }

  private static final SemgrexPattern alotPattern = SemgrexPattern.compile("{}=verb >dobj ( {word:lot}=lot ?>det {word:a} >prep_of {}=obj )");
  private static final SemgrexPattern nofPattern = SemgrexPattern.compile("{}=verb >nsubj ( {tag:CD}=num >prep_of {}=obj )");

  public static SemanticGraph rewriteMods(SemanticGraph tree) {
    // A LOT
    SemgrexMatcher matcher = alotPattern.matcher(tree);
    while (matcher.find()) {
      // Get nodes
      IndexedWord verb = matcher.getNode("verb");
      IndexedWord lot = matcher.getNode("lot");
      IndexedWord obj = matcher.getNode("obj");
      // Butcher tree
      if (!tree.removeEdge(new SemanticGraphEdge(verb, lot, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "dobj"), Double.NEGATIVE_INFINITY, false))) {
        throw new IllegalStateException("Could not remove edge!");
      }
      if (!tree.removeEdge(new SemanticGraphEdge(lot, obj, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "prep_of"), Double.NEGATIVE_INFINITY, false))) {
        throw new IllegalStateException("Could not remove edge!");
      }
      // Re-assemble tree
      tree.addEdge(verb, obj, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "dobj"), Double.NEGATIVE_INFINITY, false);
      tree.addEdge(obj, lot, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "amod"), Double.NEGATIVE_INFINITY, false);
    }

    // N OF [the]
    matcher = nofPattern.matcher(tree);
    while (matcher.find()) {
      // Get nodes
      IndexedWord verb = matcher.getNode("verb");
      IndexedWord num = matcher.getNode("num");
      IndexedWord obj = matcher.getNode("obj");
      // Butcher tree
      if (!tree.removeEdge(new SemanticGraphEdge(verb, num, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "nsubj"), Double.NEGATIVE_INFINITY, false))) {
        throw new IllegalStateException("Could not remove edge!");
      }
      if (!tree.removeEdge(new SemanticGraphEdge(num, obj, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "prep_of"), Double.NEGATIVE_INFINITY, false))) {
        throw new IllegalStateException("Could not remove edge!");
      }
      // Re-assemble tree
      tree.addEdge(verb, obj, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "nsubj"), Double.NEGATIVE_INFINITY, false);
      tree.addEdge(obj, num, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "amod"), Double.NEGATIVE_INFINITY, false);
    }

    return tree;
  }

  public static SemanticGraph rewriteRootedQuantifier(SemanticGraph tree) {
    // Make sure the root isn't a quantifier
    for (IndexedWord root : new ArrayList<>(tree.getRoots())) {
      if (root.get(NaturalLogicAnnotations.OperatorAnnotation.class) != null) {
        List<SemanticGraphEdge> outEdges = tree.getOutEdgesSorted(root);
        if (!outEdges.isEmpty()) {
          SemanticGraphEdge edgeToReverse = outEdges.get(0);
          for (SemanticGraphEdge candidate : outEdges) {
            if (candidate.getDependent().index() > root.index()) {
              edgeToReverse = candidate;
              break;
            }
          }
          tree.removeEdge(edgeToReverse);
          tree.getRoots().remove(root);
          tree.addRoot(edgeToReverse.getDependent());
          tree.addEdge(edgeToReverse.getDependent(), edgeToReverse.getGovernor(), GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "op"), edgeToReverse.getWeight(), false);
        }
      }
    }
    return tree;
  }

  public static SemanticGraph rewriteDropDeterminers(SemanticGraph tree) {
    // Remove [logically] meaningless determiners
    tree.getLeafVertices().stream().filter(vertex -> vertex.word().equalsIgnoreCase("the") || vertex.word().equalsIgnoreCase("a") ||
        vertex.word().equalsIgnoreCase("an")).forEach(tree::removeVertex);
    for (IndexedWord vertex : tree.getLeafVertices()) {
      // Rewrite edges into 'WP' as 'det'
      if ("WP".equals(vertex.tag())) {
        for (SemanticGraphEdge edge : tree.incomingEdgeList(vertex)) {
          tree.removeEdge(edge);
          tree.addEdge(edge.getGovernor(), edge.getDependent(), GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "det"), edge.getWeight(), edge.isExtra());
        }
      }
    }
    return tree;
  }

}
