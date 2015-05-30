package edu.stanford.nlp.naturalli.entail;

import com.google.gson.Gson;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.naturalli.ProcessPremise;
import edu.stanford.nlp.naturalli.ProcessQuery;
import edu.stanford.nlp.naturalli.QRewrite;
import edu.stanford.nlp.naturalli.SentenceFragment;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * An interface for calling NaturalLI in alignment mode.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class NaturalLIClassifier implements EntailmentClassifier {

  @Execution.Option(name="naturalli.use", gloss="If true, incorporate input from NaturalLI")
  private static boolean USE_NATURALLI = true;

  @Execution.Option(name="naturalli.weight", gloss="The weight to incorporate NaturalLI with")
  private static double ALIGNMENT_WEIGHT = 1.00;

  @Execution.Option(name="naturalli.incache", gloss="The cache to read from")
  private static String NATURALLI_INCACHE = "logs/all_4.cache";

  @Execution.Option(name="naturalli.outcache", gloss="The cache to write from")
  private static String NATURALLI_OUTCACHE = "tmp/naturalli.cacheout";

  static final String COUNT_ALIGNED      = "count_aligned";
  static final String COUNT_ALIGNABLE    = "count_alignable";
  static final String COUNT_UNALIGNED    = "count_unaligned";

  static final String COUNT_UNALIGNABLE_PREMISE    = "count_unalignable_premise";
  static final String COUNT_UNALIGNABLE_CONCLUSION = "count_unalignable_conclusion";
  static final String COUNT_INEXACT                = "count_inexact";

  static final String COUNT_PREMISE    = "count_premise";
  static final String COUNT_CONCLUSkION = "count_conclusion";


  static final String PERCENT_ALIGNABLE_PREMISE    = "percent_alignable_premise";
  static final String PERCENT_ALIGNABLE_CONCLUSION = "percent_alignable_conclusion";
  static final String PERCENT_ALIGNABLE_JOINT      = "percent_alignable_joint";

  static final String PERCENT_ALIGNED_PREMISE    = "percent_aligned_premise";
  static final String PERCENT_ALIGNED_CONCLUSION = "percent_aligned_conclusion";
  static final String PERCENT_ALIGNED_JOINT      = "percent_aligned_joint";

  static final String PERCENT_UNALIGNED_PREMISE    = "percent_unaligned_premise";
  static final String PERCENT_UNALIGNED_CONCLUSION = "percent_unaligned_conclusion";
  static final String PERCENT_UNALIGNED_JOINT      = "percent_unaligned_joint";

  static final String PERCENT_UNALIGNABLE_PREMISE    = "percent_unalignable_premise";
  static final String PERCENT_UNALIGNABLE_CONCLUSION = "percent_unalignable_conclusion";
  static final String PERCENT_UNALIGNABLE_JOINT      = "percent_unalignable_joint";

  private static final Pattern truthPattern = Pattern.compile("\"truth\": *([0-9\\.]+)");
  private static final Pattern alignmentIndexPattern = Pattern.compile("\"closestSoftAlignment\": *([0-9]+)");
  private static final Pattern alignmentScoresPattern = Pattern.compile("\"closestSoftAlignmentScores\": *\\[ *((:?-?(:?[0-9\\.]+|inf), *)*-?(:?[0-9\\.]+|inf)) *\\]");
  private static final Pattern hardGuessPattern = Pattern.compile("\"hardGuess\": \"(true|false|uknown)\"");
  private static final Pattern softGuessPattern = Pattern.compile("\"softGuess\": \"(true|false|uknown)\"");

  /**
   * A query to NaturalLI. Serialized with GSON.
   */
  private static final class NaturalLIQuery {
    public String[] premises;
    public String   hypothesis;

    public NaturalLIQuery(List<String> premises, String hypothesis) {
      this.premises = premises.toArray(new String[premises.size()]);
      this.hypothesis = hypothesis;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NaturalLIQuery)) return false;

      NaturalLIQuery that = (NaturalLIQuery) o;

      if (!hypothesis.toLowerCase().replace(" ", "").equals(that.hypothesis.toLowerCase().replace(" ", ""))) return false;
      if (!Arrays.equals(
          Arrays.stream(premises).map(x -> x.toLowerCase().replace(" ", "")).toArray(),
          Arrays.stream(that.premises).map(x -> x.toLowerCase().replace(" ", "")).toArray())) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(Arrays.stream(premises).map(x -> x.toLowerCase().replace(" ", "")).toArray());
      result = 31 * result + hypothesis.toLowerCase().replace(" ", "").hashCode();
      return result;
    }
  }

  /**
   * A response to a NaturalLI query. Populated with GSON.
   */
  private static final class NaturalLIResponse {
    public int numResults;
    public int totalTicks;
    public String hardGuess;
    public String softGuess;
    public String query;
    public String bestPremise;
    public boolean success;
    public int closestSoftAlignment;
    public double[] closestSoftAlignmentScores;
    public double[] closestSoftAlignmentSearchCosts;
  }

  private static final class NaturalLIPair {
    public NaturalLIQuery query;
    public NaturalLIResponse response;

    public NaturalLIPair(NaturalLIQuery query, NaturalLIResponse response) {
      this.query = query;
      this.response = response;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NaturalLIPair)) return false;

      NaturalLIPair that = (NaturalLIPair) o;

      if (!query.equals(that.query)) return false;
      if (!response.equals(that.response)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = query.hashCode();
      result = 31 * result + response.hashCode();
      return result;
    }
  }


  public final String searchProgram;
  public final Classifier<Trilean, String> impl;
  public final EntailmentFeaturizer featurizer;

  private BufferedReader fromNaturalLI;
  private OutputStreamWriter toNaturalLI;
  private final Counter<String> weights;

  private final Lazy<StanfordCoreNLP> pipeline = Lazy.of(() -> ProcessPremise.constructPipeline("parse") );

  private final Map<NaturalLIQuery, NaturalLIResponse> naturalliCache = new HashMap<>();
  private PrintWriter naturalliWriteCache;
  private PrintWriter queryStream;


  NaturalLIClassifier(String naturalliSearch, EntailmentFeaturizer featurizer, Classifier<Trilean, String> impl) {
    this.searchProgram = naturalliSearch;
    this.impl = impl;
    this.featurizer = featurizer;
    this.weights = ((LinearClassifier<Trilean,String>) impl).weightsAsMapOfCounters().get(Trilean.TRUE);
    init();
  }

  NaturalLIClassifier(String naturalliSearch, EntailmentClassifier underlyingImpl) {
    this.searchProgram = naturalliSearch;
    if (underlyingImpl instanceof SimpleEntailmentClassifier) {
      this.impl = ((SimpleEntailmentClassifier) underlyingImpl).impl;
      this.featurizer = ((SimpleEntailmentClassifier) underlyingImpl).featurizer;
    } else {
      throw new IllegalArgumentException("Cannot create NaturalLI classifier from " + underlyingImpl.getClass());
    }
    this.weights = ((LinearClassifier<Trilean,String>) impl).weightsAsMapOfCounters().get(Trilean.TRUE);
    init();
  }

  /**
   * Initialize the pipe to NaturalLI.
   */
  private synchronized void init() {
    try {
      Gson gson = new Gson();
      if (new File(NATURALLI_INCACHE).exists()) {
        BufferedReader reader = new BufferedReader( new FileReader(NATURALLI_INCACHE) );
        String line;
        while ( (line = reader.readLine()) != null) {
          NaturalLIPair entry = gson.fromJson(line, NaturalLIPair.class);
          naturalliCache.put(entry.query, entry.response);
        }
      }
      naturalliWriteCache = new PrintWriter(new FileWriter(NATURALLI_OUTCACHE));
      queryStream = new PrintWriter(new FileWriter("tmp/queries.examples"));
      Runtime.getRuntime().addShutdownHook(new Thread() {
        public void run() {
          naturalliWriteCache.close();
          queryStream.close();
        }});


      forceTrack("Creating connection to NaturalLI");
      ProcessBuilder searcherBuilder = new ProcessBuilder(searchProgram);
      final Process searcher = searcherBuilder.start();
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          searcher.destroy();
        }

      });
      // Gobble naturalli.stderr to real stderr
      Writer errWriter = new BufferedWriter(new FileWriter(new File("/dev/null")));
//      Writer errWriter = new OutputStreamWriter(System.err);
      StreamGobbler errGobbler = new StreamGobbler(searcher.getErrorStream(), errWriter);
      errGobbler.start();
      Thread t = new Thread() {
        public void run() {
          while (true) {
            try {
              errWriter.flush();
              Thread.sleep(100);
            } catch (IOException | InterruptedException ignored) {
            }
          }
        }
      };
      t.setDaemon(true);
      t.start();

      // Create the pipe
      fromNaturalLI = new BufferedReader(new InputStreamReader(searcher.getInputStream()));
      toNaturalLI = new OutputStreamWriter(new BufferedOutputStream(searcher.getOutputStream()));

      // Set some parameters
//      toNaturalLI.write("%defaultCosts = true\n");
//      toNaturalLI.write("%skipNegationSearch=true\n");
      //toNaturalLI.write("%maxTicks=500000\n");
      toNaturalLI.flush();


      endTrack("Creating connection to NaturalLI");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Convert a plain-text string to a CoNLL parse tree that can be fed into NaturalLI */
  private String toParseTree(String text) {
    Pointer<String> debug = new Pointer<>();
    try {
      String annotated = ProcessQuery.annotate(QRewrite.FOR_PREMISE, text, pipeline.get(), debug, true);
      return annotated;
    } catch (AssertionError e) {
      err("Assertion error when processing sentence: " + text);
      return "cats\t0\troot\t0";
    }
  }

  /** Convert a comma-separated list to a list of doubles */
  private List<Double> parseDoubleList(String list) {
    return Arrays.stream(list.split(" *, *")).map(x -> {switch (x) {
      case "-inf": return Double.NEGATIVE_INFINITY;
      case "inf": return Double.POSITIVE_INFINITY;
      default: return Double.parseDouble(x);
    }}).collect(Collectors.toList());
  }

  /**
   * Get the best alignment score between the hypothesis and the best premise.
   * @param premises The premises to possibly align to.
   * @param hypothesis The hypothesis we are testing.
   * @return A triple: the truth of the hypothesis, the best alignment, and the best soft alignment scores.
   * @throws IOException Thrown if the pipe to NaturalLI is broken.
   */
  private synchronized NaturalLIResponse queryNaturalLI(List<String> premises, String hypothesis) throws IOException {
    NaturalLIQuery query = new NaturalLIQuery(premises, hypothesis);
    if (naturalliCache.containsKey(query)) {
      return naturalliCache.get(query);
    }

    // Write the premises
    // Note: we write all the premises first so that the index of the costs
    // matches the index of the premise. Also, so we don't overflow the soft match buffer
    // with entailments from the first premise.
    for (String premise : premises) {
      try {
        String tree = toParseTree(premise);
        if (tree.split("\n").length > 30) {
          // Tree is too long; don't write it or else the program will crash
          toNaturalLI.write(toParseTree("cats have tails"));
          queryStream.println(toParseTree("cats have tails"));
        } else {
          toNaturalLI.write(tree);
          queryStream.println(tree);
        }
        toNaturalLI.write("\n");
      } catch (Exception e) {
        err("Caught exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }

    // Write entailments
    for (String premise : premises) {
      for (SentenceFragment entailment : ProcessPremise.forwardEntailments(premise, pipeline.get())) {
        if (!entailment.toString().equals(premise)) {
          try {
            String tree = ProcessQuery.conllDump(entailment.parseTree, new Pointer<>(), false, true);
            if (tree.split("\n").length > 30) {
              // Tree is too long; don't write it or else the program will crash
              toNaturalLI.write(toParseTree("cats have tails"));
              queryStream.println(toParseTree("cats have tails"));
            } else {
              toNaturalLI.write(tree);
              queryStream.println(tree);
            }
            toNaturalLI.write("\n");
          } catch (Exception e) {
            err("Caught exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
          }
        }
      }
    }

    // Write the query
    toNaturalLI.write(toParseTree(hypothesis));

    // Start the search
    toNaturalLI.write("\n\n");
    toNaturalLI.flush();
    queryStream.println("\n");
    queryStream.flush();

    // Read the result
    Matcher matcher;
    String json = fromNaturalLI.readLine();
    Gson gson = new Gson();
    NaturalLIResponse response = gson.fromJson(json, NaturalLIResponse.class);
    naturalliWriteCache.println(gson.toJson(new NaturalLIPair(query, response)));
    naturalliWriteCache.flush();
    return response;
  }



  private double scoreBeforeNaturalli(Counter<String> features) {
    double sum = 0.0;
    for (Map.Entry<String, Double> entry : features.entrySet()) {
      switch (entry.getKey()) {
        case COUNT_ALIGNED:
        case COUNT_ALIGNABLE:
        case COUNT_INEXACT:
        case COUNT_UNALIGNABLE_PREMISE:
        case COUNT_UNALIGNABLE_CONCLUSION:
        case "bias":
          break;
        default:
          sum += weights.getCount(entry.getKey()) * features.getCount(entry.getKey());
      }
    }
    return sum;
  }

  private double scoreAlignmentSimple(Counter<String> features) {
    double sum = 0.0;
    for (Map.Entry<String, Double> entry : features.entrySet()) {
      switch(entry.getKey()) {
        case COUNT_ALIGNED:
        case COUNT_ALIGNABLE:
        case COUNT_INEXACT:
        case COUNT_UNALIGNABLE_PREMISE:
        case COUNT_UNALIGNABLE_CONCLUSION:
        case "bias":
          sum += weights.getCount(entry.getKey()) * features.getCount(entry.getKey());
          break;
        default:
          // do nothing
      }
    }
    return sum;
  }

  @Override
  public Pair<Sentence, Double> bestScore(List<String> premisesText, String hypothesisText,
                                          Optional<String> focus, Optional<List<Double>> luceneScores,
                                          Function<Integer, Double> decay) {
    double max = Double.NEGATIVE_INFINITY;
    int argmax = -1;
    Sentence hypothesis = new Sentence(hypothesisText);
    List<Sentence> premises = premisesText.stream().map(Sentence::new).collect(Collectors.toList());

    // Query NaturalLI
    NaturalLIResponse bestNaturalLIScores;
    try {
      bestNaturalLIScores = queryNaturalLI(premisesText, hypothesisText);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    for (int i = 0; i < premises.size(); ++i) {
      // Featurize the pair
      Sentence premise = premises.get(i);
      Optional<Double> luceneScore = luceneScores.isPresent() ? Optional.of(luceneScores.get().get(i)) : Optional.empty();
      Counter<String> features = featurizer.featurize(new EntailmentPair(Trilean.UNKNOWN, premise, hypothesis, focus, luceneScore), Optional.empty());
      // Get the raw score
      double score = scoreBeforeNaturalli(features);
      double naturalLIScore;
      if (USE_NATURALLI) {
        naturalLIScore = i < bestNaturalLIScores.closestSoftAlignmentScores.length ? bestNaturalLIScores.closestSoftAlignmentScores[i] : Double.NEGATIVE_INFINITY;
      } else {
        naturalLIScore = scoreAlignmentSimple(features);
      }
      score += naturalLIScore * ALIGNMENT_WEIGHT;
      assert !Double.isNaN(score);
      assert Double.isFinite(score);
      // Computer the probability
      double prob = 1.0 / (1.0 + Math.exp(-score));
      // Discount the score if the focus is not present
      if (focus.isPresent()) {
        if (focus.get().contains(" ")) {
          if (!premise.text().toLowerCase().replaceAll("\\s+", " ").contains(focus.get().toLowerCase().replaceAll("\\s+", " "))) {
            prob *= 0.75;  // Slight penalty for not matching a long focus
          }
        } else {
          if (!premise.text().toLowerCase().replaceAll("\\s+", " ").contains(focus.get().toLowerCase().replaceAll("\\s+", " "))) {
            prob *= 0.25;  // Big penalty for not matching a short focus.
          }
        }
      }
      // Discount the score if NaturalLI thinks this conclusion is false
      if (USE_NATURALLI) {
        Trilean naturalLIVote = Trilean.fromString(bestNaturalLIScores.hardGuess);
        Trilean naturalLISoftVote = Trilean.fromString(bestNaturalLIScores.softGuess);
        if (naturalLIVote.isFalse()) {
//          prob *= 0.1;
        } else if (naturalLIVote.isTrue()) {
//          prob = 0.9 + prob / 10.0;
        } else if (naturalLISoftVote.isFalse()) {
//          prob *= 0.5;
        } else if (naturalLISoftVote.isTrue()) {
//          prob = 0.5 + prob / 2.0;
        }
      }
      // Take the argmax
      if (prob > max) {
        max = prob;
        argmax = i;
      }
    }

    return Pair.makePair(premises.get(argmax), max);
  }


  @Override
  public double truthScore(Sentence premise, Sentence hypothesis, Optional<String> focus, Optional<Double> luceneScore) {
    return bestScore(Collections.singletonList(premise.text()), hypothesis.text(), focus,
        luceneScore.isPresent() ? Optional.of(Collections.singletonList(luceneScore.get())) : Optional.empty(), i -> 1.0).second;
  }

  @Override
  public Trilean classify(Sentence premise, Sentence hypothesis, Optional<String> focus, Optional<Double> luceneScore) {
    return truthScore(premise, hypothesis, focus, luceneScore) >= 0.5 ? Trilean.TRUE : Trilean.FALSE;
  }

  @Override
  public Object serialize() {
    return Triple.makeTriple(searchProgram, featurizer, impl);
  }

  @SuppressWarnings("unchecked")
  public static NaturalLIClassifier deserialize(Object model) {
    Triple<String, EntailmentFeaturizer, Classifier<Trilean, String>> data = (Triple) model;
    return new NaturalLIClassifier(data.first, data.second, data.third());
  }
}
