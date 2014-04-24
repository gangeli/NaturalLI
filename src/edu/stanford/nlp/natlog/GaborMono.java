package edu.stanford.nlp.natlog;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * Chopping up other people's code is a bad idea. Let's try to understand what it's doing
 * and re-implement it properly.
 *
 * @see edu.stanford.nlp.natlog.AdaptedMcCartneyMono
 *
 * @author Gabor Angeli
 */
public class GaborMono implements Mono {

  public static final Collection<Quantifier> quantifiers = Collections.unmodifiableCollection(new ArrayList<Quantifier>() {{
    // downward-monotone operators ----------------------------------------------
    add(new UnaryQuantifier("not",
        "RB < /^[Nn][o']t$/", // match either "not" or "n't"
        PARENT_PROJECTION,
        Monotonicity.DOWN));

    add(new UnaryQuantifier("without",
        "IN < /^[Ww]ithout$/",
        PP_PROJECTION,
        Monotonicity.DOWN));

    // non-monotone operators ---------------------------------------------------

    add(new UnaryQuantifier("SUPERLATIVE",
        "JJS !< /^([Mm]ost)|([Ll]east)$/",
        NP_PROJECTION,
        Monotonicity.NON));


    // down-down operators ------------------------------------------------------

    add(new BinaryQuantifier("no",
        "DT < /^[Nn]o$/",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.DOWN));

    add(new BinaryQuantifier("neither",
        "DT < /^[Nn]either$/",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.DOWN));

    add(new BinaryQuantifier("AT_MOST",
        "QP < (IN < /^[Aa]t$/) < (JJS < /^most$/) < CD",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.DOWN));

    add(new BinaryQuantifier("few",
        "JJ < /^[Ff]ew$/ !$, (DT < /^[Aa]$/)", // exclude "a few"
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.DOWN));


    // down-up operators --------------------------------------------------------

    add(new BinaryQuantifier("all",
        "DT < /^[Aa]ll$/",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.UP));

    add(new BinaryQuantifier("each",
        "DT < /^[Ee]ach$/",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.UP));

    add(new BinaryQuantifier("every",
        "DT < /^[Ee]very$/",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.UP));


    // non-up operators ---------------------------------------------------------

    add(new BinaryQuantifier("most",
        "JJS < /^[Mm]ost$/ !> QP", // not under QP, to eliminate "at most"
        NP_PROJECTION,
        Monotonicity.NON,
        S_PROJECTION,
        Monotonicity.UP));

    add(new BinaryQuantifier("many",
        "/DT|JJ/ < /^[Mm]any$/",
        NP_PROJECTION,
        Monotonicity.NON,
        S_PROJECTION,
        Monotonicity.UP));
  }});

  private static final String NP_PROJECTION = "__ >+(NP) (NP=proj !> NP)";
  private static final String PP_PROJECTION = "__ > PP=proj";
  // private static final String S_PROJECTION = "__ >+(/.*/) (S=proj !> S)";
  // >+(/.*/) should be equivalent to >>
  private static final String S_PROJECTION = "__ >> (S=proj !> S)";
  private static final String PARENT_PROJECTION = "__ > __=proj"; // for (RB not)
  // the parent of (RB not) is usually VP, but can also be ADJP, CONJP, WHADVP, ...

  /**
   * The abstract characterization of a quantifier, as far as Monotonicity is conerned
   */
  public static interface Quantifier {
    public Collection<Pair<Tree, Monotonicity>> annotate(Tree input);
  }


  /**
   * A unary quantifier. For example, 'not'.
   */
  public static class UnaryQuantifier implements Quantifier {
    public final String humanReadableName;
    public final TregexPattern trigger;

    public final TregexPattern arg1Matcher;
    public final Monotonicity arg1Monotonicity;

    public UnaryQuantifier(String humanReadableName, String trigger, String arg1Matcher, Monotonicity arg1Monotonicity) {
      this.humanReadableName = humanReadableName;
      this.trigger = TregexPattern.compile(trigger);
      this.arg1Matcher = TregexPattern.compile(arg1Matcher);
      this.arg1Monotonicity = arg1Monotonicity;
    }

    @Override
    public Collection<Pair<Tree, Monotonicity>> annotate(Tree input) {
      TregexMatcher triggerMatcher = trigger.matcher(input);
      if (triggerMatcher.find()) {
        Tree triggerMatch = triggerMatcher.getMatch();
        final TregexMatcher arg1Matcher = this.arg1Matcher.matcher(input);
        if (arg1Matcher.matchesAt(triggerMatch)) {
          return Collections.singletonList(Pair.makePair(arg1Matcher.getNode("proj"), arg1Monotonicity));
        }
      }
      //noinspection unchecked
      return Collections.EMPTY_LIST;
    }
  }


  /**
   * A binary classifier, for example 'all'.
   */
  public static class BinaryQuantifier extends UnaryQuantifier {
    public final TregexPattern arg2Matcher;
    public final Monotonicity arg2Monotonicity;

    public BinaryQuantifier(String humanReadableName, String trigger, String arg1Matcher, Monotonicity arg1Monotonicity, String arg2Matcher, Monotonicity arg2Monotonicity) {
      super(humanReadableName, trigger, arg1Matcher, arg1Monotonicity);
      this.arg2Matcher = TregexPattern.compile(arg2Matcher);
      this.arg2Monotonicity = arg2Monotonicity;
    }

    @Override
    public Collection<Pair<Tree, Monotonicity>> annotate(Tree input) {
      TregexMatcher triggerMatcher = trigger.matcher(input);
      if (triggerMatcher.find()) {
        Tree triggerMatch = triggerMatcher.getMatch();
        final TregexMatcher arg1Matcher = this.arg1Matcher.matcher(input);
        final TregexMatcher arg2Matcher = this.arg2Matcher.matcher(input);
        if (arg1Matcher.matchesAt(triggerMatch) && arg2Matcher.matchesAt(triggerMatch)) {
          return new ArrayList<Pair<Tree,Monotonicity>>() {{
            add(Pair.makePair(arg1Matcher.getNode("proj"), arg1Monotonicity));
            add(Pair.makePair(arg2Matcher.getNode("proj"), arg2Monotonicity));
          }};
        }
      }
      //noinspection unchecked
      return Collections.EMPTY_LIST;
    }
  }


  /**
   * A translator between a constituent in a tree, and the span of that constituent.
   */
  private static class SpanFinder {
    private Map<Tree, Span> chart = new IdentityHashMap<>();

    public SpanFinder(Tree tree) {
      // Base case: mark leaves
      int i = 0;
      for (Tree leaf : tree.getLeaves()) {
        chart.put(leaf, new Span(i, i + 1));
        i += 1;
      }
      // Recurse from root
      Span fullTree = computeChart(tree);
      // Sanity check
      if ((fullTree.end() - fullTree.start()) != tree.yield().size()) {
        throw new IllegalStateException("Error in computing tree spans!");
      }
    }

    private Span computeChart(Tree node) {
      if (node.isLeaf()) {
        return chart.get(node);
      } else {
        int start = Integer.MAX_VALUE;
        int end   = Integer.MIN_VALUE;
        for (Tree child : node.children()) {
          Span childSpan = computeChart(child);
          start = Math.min(start, childSpan.start());
          end = Math.max(end, childSpan.end());
        }
        Span span = new Span(start, end);
        chart.put(node, span);
        return span;
      }
    }

    public Span findSpan(Tree subtree) {
      Span candidate = chart.get(subtree);
      if (candidate == null) {
        throw new IllegalArgumentException("In the words of Bill McCartney: 'This is suboptimal!!!'");
      }
      return candidate;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Monotonicity[] annotate(Tree tree) {
    // Collect Tree -> Span mapping
    SpanFinder spans = new SpanFinder(tree);

    // Variables of interest
    Monotonicity[] monoMarks = new Monotonicity[tree.yield().size()];
    Arrays.fill(monoMarks, Monotonicity.DEFAULT);
    Monotonicity[] quantifierMarks = new Monotonicity[tree.yield().size()];
    Arrays.fill(quantifierMarks, Monotonicity.UP);

    // Run Monotonicity
    for (Quantifier quantifier : quantifiers) {
      // Compute local monotonicity markings from this quantifier
      boolean doCompose = false;
      for (Pair<Tree, Monotonicity> match : quantifier.annotate(tree)) {
        doCompose = true;
        for (int index : spans.findSpan(match.first)) {
          if (quantifierMarks[index] == Monotonicity.UP) {
            quantifierMarks[index] = match.second;
          }
        }
      }
      // Apply these markings to the final markings
      if (doCompose) {
        for (int i = 0; i < monoMarks.length; ++i) {
          monoMarks[i] = monoMarks[i].compose(quantifierMarks[i]);
        }
        Arrays.fill(quantifierMarks, Monotonicity.UP);
      }
    }
    return monoMarks;
  }


  private static GaborMono instance = null;

  public static GaborMono getInstance() {
    if (instance == null) {
      instance = new GaborMono();
    }
    return instance;
  }

  public static void main(String[] args) {
    new GaborMono().annotate(Tree.valueOf("(ROOT (S (NP (DT all) (NNS cats)) (VP (VBP have) (NP (NNS tails)))))"));
  }

}
