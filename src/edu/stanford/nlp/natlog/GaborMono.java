package edu.stanford.nlp.natlog;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

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
  private static StanfordCoreNLP pipeline = null;

  public static final Collection<Quantifier> quantifiers = Collections.unmodifiableCollection(new ArrayList<Quantifier>() {{
    List<String> regexps;

    // downward-monotone operators ----------------------------------------------
    regexps = new ArrayList<>();
    for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
      if (q.closestMeaning == org.goobs.truth.Quantifier.LogicalQuantifier.NONE &&
          (q.trigger == org.goobs.truth.Quantifier.TriggerType.UNARY_NOT || q.trigger == org.goobs.truth.Quantifier.TriggerType.NO)) {
        assert q.surfaceForm.length == 1;
        regexps.add("[" + q.surfaceForm[0].charAt(0) + Character.toUpperCase(q.surfaceForm[0].charAt(0)) + "]" + q.surfaceForm[0].substring(1).toLowerCase());
        if (!q.surfaceForm[0].equals(q.literalSurfaceForm[0])) { regexps.add("[" + q.literalSurfaceForm[0].charAt(0) + Character.toUpperCase(q.literalSurfaceForm[0].charAt(0)) + "]" + q.literalSurfaceForm[0].substring(1).toLowerCase()); }
      }
    }
    add(new UnaryQuantifier("down unary under RB",
        "RB < /^" + StringUtils.join(regexps, "|") + "$/",
        PARENT_PROJECTION,
        Monotonicity.DOWN));

    regexps = new ArrayList<>();
    for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
      if (q.closestMeaning == org.goobs.truth.Quantifier.LogicalQuantifier.NONE &&
          q.trigger == org.goobs.truth.Quantifier.TriggerType.UNARY_NOT_IN) {
        assert q.surfaceForm.length == 1;
        regexps.add("[" + q.surfaceForm[0].charAt(0) + Character.toUpperCase(q.surfaceForm[0].charAt(0)) + "]" + q.surfaceForm[0].substring(1).toLowerCase());
        if (!q.surfaceForm[0].equals(q.literalSurfaceForm[0])) { regexps.add("[" + q.literalSurfaceForm[0].charAt(0) + Character.toUpperCase(q.literalSurfaceForm[0].charAt(0)) + "]" + q.literalSurfaceForm[0].substring(1).toLowerCase()); }
      }
    }
    add(new UnaryQuantifier("down unary under IN",
        "IN < /^" + StringUtils.join(regexps, "|") + "$/",
        PP_PROJECTION,
        Monotonicity.DOWN));

    add(new UnaryQuantifier("unary 'no'",
        "DT < /^[Nn]o$/ > NP >> VP",
        NP_PROJECTION,
        Monotonicity.DOWN));

    // non-monotone operators ---------------------------------------------------

    add(new UnaryQuantifier("SUPERLATIVE",
        "JJS !< /^([Mm]ost)|([Ll]east)$/",
        NP_PROJECTION,
        Monotonicity.NON));


    // down-down operators ------------------------------------------------------
    regexps = new ArrayList<>();
    for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
      if (q.closestMeaning == org.goobs.truth.Quantifier.LogicalQuantifier.NONE &&
          (q.trigger == org.goobs.truth.Quantifier.TriggerType.DEFAULT || q.trigger == org.goobs.truth.Quantifier.TriggerType.NO)) {
        assert q.surfaceForm.length == 1;
        regexps.add("[" + q.surfaceForm[0].charAt(0) + Character.toUpperCase(q.surfaceForm[0].charAt(0)) + "]" + q.surfaceForm[0].substring(1).toLowerCase());
        if (!q.surfaceForm[0].equals(q.literalSurfaceForm[0])) { regexps.add("[" + q.literalSurfaceForm[0].charAt(0) + Character.toUpperCase(q.literalSurfaceForm[0].charAt(0)) + "]" + q.literalSurfaceForm[0].substring(1).toLowerCase()); }
      }
    }
    add(new BinaryQuantifier("down down under DT/JJ",
        "(/^DT|JJS?$/ < /^" + StringUtils.join(regexps, "|") + "$/ !$, (DT < /^[Aa]$/)) !>> VP",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.DOWN));

    // Some other special cases
    add(new BinaryQuantifier("AT_MOST",
        "QP < (IN < /^[Aa]t$/) < (JJS < /^most$/) < CD",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.DOWN));

    // down-up operators --------------------------------------------------------

    regexps = new ArrayList<>();
    for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
      if (q.closestMeaning == org.goobs.truth.Quantifier.LogicalQuantifier.FORALL &&
          q.trigger == org.goobs.truth.Quantifier.TriggerType.DEFAULT) {
        assert q.surfaceForm.length == 1;
        regexps.add("[" + q.surfaceForm[0].charAt(0) + Character.toUpperCase(q.surfaceForm[0].charAt(0)) + "]" + q.surfaceForm[0].substring(1).toLowerCase());
        if (!q.surfaceForm[0].equals(q.literalSurfaceForm[0])) { regexps.add("[" + q.literalSurfaceForm[0].charAt(0) + Character.toUpperCase(q.literalSurfaceForm[0].charAt(0)) + "]" + q.literalSurfaceForm[0].substring(1).toLowerCase()); }
      }
    }
    add(new BinaryQuantifier("down up under DT",
        "DT < /^" + StringUtils.join(regexps, "|") + "$/ !>> VP",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.UP));

    regexps = new ArrayList<>();
    for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
      if (q.closestMeaning == org.goobs.truth.Quantifier.LogicalQuantifier.FEW) {
        assert q.surfaceForm.length == 1;
        regexps.add("[" + q.surfaceForm[0].charAt(0) + Character.toUpperCase(q.surfaceForm[0].charAt(0)) + "]" + q.surfaceForm[0].substring(1).toLowerCase());
        if (!q.surfaceForm[0].equals(q.literalSurfaceForm[0])) { regexps.add("[" + q.literalSurfaceForm[0].charAt(0) + Character.toUpperCase(q.literalSurfaceForm[0].charAt(0)) + "]" + q.literalSurfaceForm[0].substring(1).toLowerCase()); }
      }
    }
    add(new BinaryQuantifier("down up under JJ (few)",
        "/JJS?/ < /^" + StringUtils.join(regexps, "|") + "$/ !>> VP !$, (DT < /^[Aa]$/)",
        NP_PROJECTION,
        Monotonicity.DOWN,
        S_PROJECTION,
        Monotonicity.UP));
    add(new UnaryQuantifier("down under JJ (few)",
        "/JJS?/ < /^" + StringUtils.join(regexps, "|") + "$/ ( > NP >> VP ) ( !$, ( DT < /^[Aa]$/ ) )",
        NP_PROJECTION,
        Monotonicity.DOWN));

    // non-up operators ---------------------------------------------------------
    regexps = new ArrayList<>();
    for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
      if (q.closestMeaning == org.goobs.truth.Quantifier.LogicalQuantifier.MOST &&
          q.trigger == org.goobs.truth.Quantifier.TriggerType.DEFAULT) {
        assert q.surfaceForm.length == 1;
        regexps.add("[" + q.surfaceForm[0].charAt(0) + Character.toUpperCase(q.surfaceForm[0].charAt(0)) + "]" + q.surfaceForm[0].substring(1).toLowerCase());
        if (!q.surfaceForm[0].equals(q.literalSurfaceForm[0])) { regexps.add("[" + q.literalSurfaceForm[0].charAt(0) + Character.toUpperCase(q.literalSurfaceForm[0].charAt(0)) + "]" + q.literalSurfaceForm[0].substring(1).toLowerCase()); }
      }
    }
    add(new BinaryQuantifier("non up under DT/JJ/RBS",
        "/^(DT|JJS?|RBS?|NNP?S?)$/ < /^" + StringUtils.join(regexps, "|") + "$/ !> QP !>> VP",
        NP_PROJECTION,
        Monotonicity.NON,
        S_PROJECTION,
        Monotonicity.UP));
  }});

//  private static final String NP_PROJECTION = "__ >+(NP) (NP=proj !> NP)";
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

    @Override
    public String toString() { return humanReadableName; }
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
    List<String> yield = new ArrayList<>(monoMarks.length);
    for (Label w : tree.yield()) {
      yield.add(w.value());
    }

    // Run Monotonicity
    for (Quantifier quantifier : quantifiers) {
      // Compute local monotonicity markings from this quantifier
      boolean doCompose = false;
      for (Pair<Tree, Monotonicity> match : quantifier.annotate(tree)) {
        doCompose = true;
        Span span = spans.findSpan(match.first);
        // Don't mark the quantifier itself
        // This block is a mess of off-by-ones.
        // We start with a span, and try to find a quantifier as far left as possible, going
        // no more than two words to the right of the left edge (if this sounds hacky, it's because
        // it is).
        OUTER: for (int start = 0; start < Math.min(2, span.size() - 1); ++start) {
          for (int len = span.size() - start - 1; len > 0; --len) {
            if (org.goobs.truth.Quantifier.quantifierGlosses.contains(StringUtils.join(yield.subList(span.start() + start, span.start() + start + len), " ").toLowerCase())) {
              span.setStart(span.start() + start + len);
              break OUTER;
            }
          }
        }
        // Apply the local markings
        for (int index : span) {
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

    // Return
    return monoMarks;
  }

  @Override
  public Monotonicity[] annotate(String[] words) {
    // Create pipeline
    if (pipeline == null) {
      pipeline = new StanfordCoreNLP(new Properties() {{
        setProperty("annotators", "tokenize,ssplit,parse");
        setProperty("tokenize.whitespace", "true");
        setProperty("parse.model", "edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz");
      }});
    }
    // Add 'most' quantifier if none given
    boolean hasQuantifier = false;
    String gloss = StringUtils.join(words, " ");
    for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
      if (gloss.toLowerCase().startsWith(StringUtils.join(q.literalSurfaceForm, " "))) { hasQuantifier = true; }
    }
    if (!hasQuantifier) {
      Annotation ann = new Annotation(gloss);
      pipeline.annotate(ann);
      String lemmatized = StringUtils.join( ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class), " ");
      for (org.goobs.truth.Quantifier q : org.goobs.truth.Quantifier.values()) {
        if (lemmatized.toLowerCase().startsWith(StringUtils.join(q.literalSurfaceForm, " "))) { hasQuantifier = true; }
      }
      if (!hasQuantifier) {
        gloss = "Most "  + gloss;
      }
    }
    // Annotate
    Annotation ann = new Annotation(gloss);
    pipeline.annotate(ann);
    Tree tree = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(TreeCoreAnnotations.TreeAnnotation.class);
    Monotonicity[] raw = annotate(tree);
    if (!hasQuantifier) {
      Monotonicity[] withoutQuantifier = new Monotonicity[raw.length - 1];
      System.arraycopy(raw, 1, withoutQuantifier, 0, withoutQuantifier.length);
      return withoutQuantifier;
    } else {
      return raw;
    }
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
