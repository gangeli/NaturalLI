package edu.stanford.nlp.natlog;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;

/**
 * Adapted from edu.stanford.nlp.natlog.Mono.
 *
 * Bill -- if you ever stumble upon this, I'm sorry for the violence I have done to your
 * code.
 *
 * @author Bill MacCartney
 * @author Gabor Angeli (adapted)
 */
public class AdaptedMacCartneyMono implements Mono {

  private static int verbose = 1;

  private static final String NP_PROJECTION = "__ >+(NP) (NP=proj !> NP)";
  private static final String PP_PROJECTION = "__ > PP=proj";
  // private static final String S_PROJECTION = "__ >+(/.*/) (S=proj !> S)";
  // >+(/.*/) should be equivalent to >>
  private static final String S_PROJECTION = "__ >> (S=proj !> S)";
  private static final String PARENT_PROJECTION = "__ > __=proj"; // for (RB not)
  // the parent of (RB not) is usually VP, but can also be ADJP, CONJP, WHADVP, ...

  private static class Operator {

    public final String name;
    public final String patStr;
    public final TregexPattern pat;
    public final int arity;
    public final Monotonicity arg1Mono;
    public final Monotonicity arg2Mono;
    public final String arg1ProjPatStr;
    public final String arg2ProjPatStr;
    public final TregexPattern arg1ProjPat;
    public final TregexPattern arg2ProjPat;

    public Operator(String name,
                    String patStr,
                    int arity,
                    Monotonicity arg1Mono,
                    Monotonicity arg2Mono,
                    String arg1ProjPatStr,
                    String arg2ProjPatStr) {
      this.name = name;
      this.patStr = patStr;
      this.arity = arity;
      this.arg1Mono = arg1Mono;
      this.arg2Mono = arg2Mono;
      this.arg1ProjPatStr = arg1ProjPatStr;
      this.arg2ProjPatStr = arg2ProjPatStr;
      this.pat = TregexPattern.compile(patStr);
      this.arg1ProjPat = TregexPattern.compile(arg1ProjPatStr);
      this.arg2ProjPat = TregexPattern.compile(arg2ProjPatStr);
    }

    @Override
    public String toString() {
      return name;
    }

  }


  // it would be good to make all these patterns case-insensitive!
  // or, transform the tree to be all lowercase!
  private static final List<Operator> operators = new ArrayList<>();

  static {

    // downward-monotone operators ----------------------------------------------

    operators.add(new Operator("not",
                               "RB < /^[Nn][o']t$/", // match either "not" or "n't"
                               1,
                               Monotonicity.DOWN,
                               null,
                               // S_PROJECTION,   // old way
                               PARENT_PROJECTION,
                               null));

    operators.add(new Operator("without",
                               "IN < /^[Ww]ithout$/",
                               1,
                               Monotonicity.DOWN,
                               null,
                               PP_PROJECTION,
                               null));


    // non-monotone operators ---------------------------------------------------

    operators.add(new Operator("SUPERLATIVE",
                               "JJS !< /^([Mm]ost)|([Ll]east)$/",
                               1,
                               Monotonicity.NON,
                               null,
                               NP_PROJECTION,
                               null));


    // down-down operators ------------------------------------------------------
  
    operators.add(new Operator("no",
                               "DT < /^[Nn]o$/",
                               2,
                               Monotonicity.DOWN,
                               Monotonicity.DOWN,
                               NP_PROJECTION,
                               S_PROJECTION));

    operators.add(new Operator("neither",
                               "DT < /^[Nn]either$/",
                               2,
                               Monotonicity.DOWN,
                               Monotonicity.DOWN,
                               NP_PROJECTION,
                               S_PROJECTION));

    operators.add(new Operator("AT_MOST",
                               "QP < (IN < /^[Aa]t$/) < (JJS < /^most$/) < CD",
                               2,
                               Monotonicity.DOWN,
                               Monotonicity.DOWN,
                               NP_PROJECTION,
                               S_PROJECTION));

    operators.add(new Operator("few",
                               "JJ < /^[Ff]ew$/ !$, (DT < /^[Aa]$/)", // exclude "a few"
                               2,
                               Monotonicity.DOWN,
                               Monotonicity.DOWN,
                               NP_PROJECTION,
                               S_PROJECTION));


    // down-up operators --------------------------------------------------------

    operators.add(new Operator("all",
                               "DT < /^[Aa]ll$/",
                               2,
                               Monotonicity.DOWN,
                               Monotonicity.UP,
                               NP_PROJECTION,
                               S_PROJECTION));

    operators.add(new Operator("each",
                               "DT < /^[Ee]ach$/",
                               2,
                               Monotonicity.DOWN,
                               Monotonicity.UP,
                               NP_PROJECTION,
                               S_PROJECTION));

    operators.add(new Operator("every",
                               "DT < /^[Ee]very$/",
                               2,
                               Monotonicity.DOWN,
                               Monotonicity.UP,
                               NP_PROJECTION,
                               S_PROJECTION));


    // non-up operators ---------------------------------------------------------

    operators.add(new Operator("most",
                               "JJS < /^[Mm]ost$/ !> QP", // not under QP, to eliminate "at most"
                               2,
                               Monotonicity.NON,
                               Monotonicity.UP,
                               NP_PROJECTION,
                               S_PROJECTION));

    operators.add(new Operator("most2",
                               "DT < /^[Mm]ost$/",
                               2,
                               Monotonicity.NON,
                               Monotonicity.UP,
                               NP_PROJECTION,
                               S_PROJECTION));


    // non-down operators -------------------------------------------------------

  }

  // ============================================================================


  // ============================================================================

  private static class TreeSpans {
    private final int n;
    private final Span spans[][];
    private final Tree tree;
    private Map<Tree, Span> treesToSpans = new IdentityHashMap<>();

    /**
     * A Span is a view onto a Tuple.  Has a Tuple and two indices.  Doesn't have
     * any storage of its own.  Is immutable. <p/>
     *
     * This class is quite redundant with edu.stanford.nlp.natlog.seqedit.Span.  Does it make sense to
     * combine them?
     *
     * @author Bill MacCartney <wcmac@cs.stanford.edu>
     */
    public class Span {

      private int l;
      private int r;

      public Span(int l, int r) {
        if (l < 0) throw new IllegalArgumentException("l=" + l + " < 0!");
        if (l > n) throw new IllegalArgumentException("l=" + l + " > n=" + n + "!");
        if (r < 0) throw new IllegalArgumentException("r=" + r + " < 0!");
        if (r > n) throw new IllegalArgumentException("r=" + r + " > n=" + n + "!");
        if (l > r) throw new IllegalArgumentException(); // zero-length spans are OK?
        this.l = l;
        this.r = r;
        // this.cats = new ArrayList<String>();
      }

      public int left() { return l; }
      public int right() { return r; }

      @Override
      public String toString() {
        return String.format("span(%d, %d)", l, r);
      }
    }

    private TreeSpans(Tree tree) {
      this.n = tree.yield().size();
      this.tree = tree;
      spans = new Span[n + 1][];
      for (int length = 0; length <= n; length++) {
        spans[length] = new Span[n + 1 - length];
      /*
      for (int left = 0; left <= n - length; left++) {
      }
      */
      }
      treesToSpans = new IdentityHashMap<>();
      // why IdentityHashMap???
      // is it to avoid confusion when there are equal subtrees (e.g. repeated words)?
      collectWordSpans();
      collectPhraseSpans(tree);
      // System.err.println("treesToSpans: " + treesToSpans);
      addParentsToSpans(tree);
    }

    public Span getSpanForSubtree(Tree t) {
      return treesToSpans.get(t);
    }

    private void collectWordSpans() {
      List<Tree> leaves = tree.getLeaves();
      for (int i = 0; i < leaves.size(); i++) {
        Tree leaf = leaves.get(i);
        Span span = new Span(i, i + 1);
        spans[1][i] = span;
        treesToSpans.put(leaf, span);
      }
    }

    private Span collectPhraseSpans(Tree tree) {
      if (tree.isLeaf()) return getSpanForSubtree(tree);
      int left = Integer.MAX_VALUE;
      int right = Integer.MIN_VALUE;
      for (Tree child : tree.children()) {
        Span span = collectPhraseSpans(child);
        left = Math.min(left, span.left());
        right = Math.max(right, span.right());
      }
      Span span = spans[right - left][left];
      if (span == null) {
        span = new Span(left, right);
        spans[right - left][left] = span;
      }
      treesToSpans.put(tree, span);
      return span;
    }


  private void addParentsToSpans(Tree rootTree) {
    Span rootSpan = getSpanForSubtree(rootTree);
    if (rootSpan == null) throw new RuntimeException("WHOA!");
    addParentsToSpansRecursive(rootTree);
  }

  private void addParentsToSpansRecursive(Tree tree) {
    for (Tree childTree : tree.children()) {
      Span childSpan = getSpanForSubtree(childTree);
      if (childSpan == null) throw new RuntimeException("WHOA!");
      addParentsToSpansRecursive(childTree);
    }
  }

  }

  // ============================================================================

  private static AdaptedMacCartneyMono singleton;

  private AdaptedMacCartneyMono() { }

  public static AdaptedMacCartneyMono getInstance() {
    if (singleton == null) {
      singleton = new AdaptedMacCartneyMono();
    }
    return singleton;
  }

  // replaces projectOperators
  public Monotonicity[] annotate(Tree tree) {
    if (tree == null) {
      System.err.println("WARNING: can't project operators in null tree!");
      return null;
    }
    tree = ensureTreeIsRooted(tree);
    Monotonicity[] markings = new Monotonicity[tree.yield().size()];

    TreeSpans spanData = new TreeSpans(tree);

    for (Operator op : operators) {
      if (verbose > 2) {
        System.err.printf("Seeking operator \"%s\" (pattern \"%s\"):%n", op, op.patStr);
      }
      TregexMatcher opMat = op.pat.matcher(tree);
      while (opMat.find()) {
        Tree operator = opMat.getMatch();
        if (verbose > 2) {
          System.err.printf("  Found operator: %s%n", operator);
        }
        for (int i = 0; i < op.arity; i++) {
          Monotonicity monotonicity;
          String projPatStr;
          TregexPattern projPat;
          if (i == 0) {
            monotonicity = op.arg1Mono;
            projPatStr = op.arg1ProjPatStr;
            projPat = op.arg1ProjPat;
          } else if (i == 1) {
            monotonicity = op.arg2Mono;
            projPatStr = op.arg2ProjPatStr;
            projPat = op.arg2ProjPat;
          } else {
            throw new RuntimeException();
          }
          if (verbose > 2) {
            System.err.printf("    Projecting place %d (%s) (pattern \"%s\"):%n",
                              i + 1,
                              monotonicity,
                              projPatStr);
          }
          TregexMatcher projMat = projPat.matcher(tree);
          if (projMat.matchesAt(operator)) {
            Tree projection = projMat.getNode("proj");
            if (verbose > 2) {
              System.err.printf("      %s%n", StringUtils.trim(projection, 60));
            }
//            marking.addMonoMark(projection, monotonicity, operator);
            TreeSpans.Span span = spanData.getSpanForSubtree(projection);
            for (int k = span.left(); k < span.right(); ++k) {
              if (markings[k] == null) {
                markings[k] = monotonicity;
              }
            }
          } else {
            if (verbose > 2) {
              System.err.println("      NO PROJECTION!");
            }
          }
        }
      }
    }

    for (int i = 0; i < markings.length; ++i) {
      if (markings[i] == null) {
        markings[i] = Monotonicity.UP;
      }
    }
    return markings;
  }

  private static StanfordCoreNLP pipeline = null;

  @Override
  public Monotonicity[] annotate(String[] gloss) {
    // Create pipeline
    if (pipeline == null) {
      pipeline = new StanfordCoreNLP(new Properties() {{
        setProperty("annotators", "tokenize,ssplit,parse");
        setProperty("tokenize.whitespace", "true");
        setProperty("parse.model", "edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz");
      }});
    }
    // Annotate
    Annotation ann = new Annotation(StringUtils.join(gloss, " "));
    pipeline.annotate(ann);
    Tree tree = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(TreeCoreAnnotations.TreeAnnotation.class);
    return annotate(tree);
  }

  /**
   * If the given tree does not have a root node with label "ROOT", this method
   * returns one that does.
   */
  public static Tree ensureTreeIsRooted(Tree tree) {
    if ("ROOT".equals(tree.value())) {
      return tree;
    } else {
      return tree.treeFactory().newTreeNode("ROOT", CollectionUtils.makeList(tree));
    }
  }


  // testing ====================================================================

  private static final List<String> treeStrs = Arrays.asList(
      "(S (NP (NNS monkeys)) (VP (VBP do) (RB not) (VP (VB dance))))",
      "(S (NP (PRP She)) (VP (VBD decided) (S (RB not) (VP (TO to) (VP (VB go))))))",
      "(S (NP (PRP She)) (VP (VBD lost) (NP (NN weight)) (PP (IN by) (S (RB not) (VP (VBG eating))))))",
      "(ROOT (S (NP (NP (DT No) (NN man)) (PP (IN without) (NP (NN food)))) (VP (VBZ feels) (ADJP (JJ good))) (. .)))",
      "(NP (NP (DT No) (NN man)) (PP (IN without) (NP (NN food))))",
      "(NP (DT No) (NN man))",
      "(S (NP (PRP He)) (VP (VBD raised) (NP (DT no) (NNS objections))))",
      "(S (NP (QP (IN At) (JJS most) (CD six)) (NNS men)) (VP (VBD bet)))",
      "(S (NP (PRP We)) (VP (MD can) (VP (VB hire) (NP (QP (IN at) (JJS most) (CD six)) (NNS men)))))",
      "(S (NP (JJS Most) (NNS players)) (VP (VBP cheat)))",
      "(S (NP (PRP I)) (VP (VBP abhor) (NP (JJS most) (NNS bugs))))",
      "(NP (JJS angriest) (NN senator))");

  public static void main(String[] args) throws Exception {
    verbose += 1;
    for (String treeStr : treeStrs) {
      System.out.println("================================================================================");
      Tree tree = Tree.valueOf(treeStr);

      Monotonicity[] marking = getInstance().annotate(tree);
      System.out.println();
      int i = 0;
      for (Label w : tree.yield()) {
        String mark;
        switch (marking[i]) {
          case UP: mark = "^"; break;
          case DOWN: mark = "v"; break;
          case NON: mark = "-"; break;
          default: mark = "?"; break;
        }
        System.out.print(w.value() + "[" + mark + "] ");
        i += 1;
      }
      System.out.println();
      System.out.println();
    }
  }

}
