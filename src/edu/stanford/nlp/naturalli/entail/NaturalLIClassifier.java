package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.naturalli.ProcessPremise;
import edu.stanford.nlp.naturalli.ProcessQuery;
import edu.stanford.nlp.naturalli.QRewrite;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.endTrack;
import static edu.stanford.nlp.util.logging.Redwood.Util.forceTrack;
import static edu.stanford.nlp.util.logging.Redwood.Util.log;

/**
 * An interface for calling NaturalLI in alignment mode.
 *
 * @author Gabor Angeli
 */
public class NaturalLIClassifier implements EntailmentClassifier {

  static final String COUNT_ALIGNED      = "count_aligned";
  static final String COUNT_ALIGNABLE    = "count_alignable";
  static final String COUNT_UNALIGNED    = "count_unaligned";

  static final String COUNT_UNALIGNABLE_PREMISE    = "count_unalignable_premise";
  static final String COUNT_UNALIGNABLE_CONCLUSION = "count_unalignable_conclusion";
  static final String COUNT_UNALIGNABLE_JOINT      = "count_unalignable_joint";

  static final String COUNT_PREMISE    = "count_premise";
  static final String COUNT_CONCLUSION = "count_conclusion";


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

  private static final Pattern alignmentIndexPattern = Pattern.compile("\"closestSoftAlignment\": ([0-9]+)");
  private static final Pattern alignmentScorePattern = Pattern.compile("\"closestSoftAlignmentScores\": (-?(:?[0-9\\.]+|inf))");
  private static final Pattern alignmentSearchCostPattern = Pattern.compile("\"closestSoftAlignmentSearchCosts\": (-?(:?[0-9\\.]+|inf))");


  public final String searchProgram;
  public final Classifier<Trilean, String> impl;
  public final EntailmentFeaturizer featurizer;

  private BufferedReader fromNaturalLI;
  private OutputStreamWriter toNaturalLI;
  private final Counter<String> weights;

  private final StanfordCoreNLP pipeline;


  NaturalLIClassifier(String naturalliSearch, EntailmentFeaturizer featurizer, Classifier<Trilean, String> impl) {
    this.searchProgram = naturalliSearch;
    this.impl = impl;
    this.featurizer = featurizer;
    this.weights = ((LinearClassifier<Trilean,String>) impl).weightsAsMapOfCounters().get(Trilean.TRUE);
    this.pipeline = ProcessPremise.constructPipeline("depparse");
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
    this.pipeline = ProcessPremise.constructPipeline("depparse");
    init();
  }

  /**
   * Initialize the pipe to NaturalLI.
   */
  private synchronized void init() {
    try {
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
      StreamGobbler errGobbler = new StreamGobbler(searcher.getErrorStream(), errWriter);
      errGobbler.start();

      // Create the pipe
      fromNaturalLI = new BufferedReader(new InputStreamReader(searcher.getInputStream()));
      toNaturalLI = new OutputStreamWriter(new BufferedOutputStream(searcher.getOutputStream()));

      // Set some parameters
      toNaturalLI.write("%softCosts=true\n");
      toNaturalLI.write("%skipNegationSearch=true\n");
      toNaturalLI.write("%maxTicks=100000\n");
      toNaturalLI.flush();


      endTrack("Creating connection to NaturalLI");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String toParseTree(String text) {
//    log("Annotating sentence: '" + text + "'");
    Pointer<String> debug = new Pointer<>();
    String annotated = ProcessQuery.annotate(QRewrite.FOR_PREMISE, text, pipeline, debug, true);
//    debug.dereference().ifPresent(Redwood.Util::log);
    return annotated;
  }

  /**
   * Get the best alignment score between the hypothesis and the best premise.
   * @param premises The premises to possibly align to.
   * @param hypothesis The hypothesis we are testing.
   * @return The alignment score returned from NaturalLI.
   * @throws IOException Thrown if the pipe to NaturalLI is broken.
   */
  private synchronized Pair<Integer, Double> getBestAlignment(List<String> premises, String hypothesis) throws IOException {
    // Write the premises
    for (String premise : premises) {
      String tree = toParseTree(premise);
      if (tree.split("\n").length > 30) {
        // Tree is too long; don't write it or else the program will crash
        toNaturalLI.write(toParseTree("cats have tails"));
      } else {
        toNaturalLI.write(toParseTree(premise));
      }
      toNaturalLI.write("\n");
    }

    // Write the query
    toNaturalLI.write(toParseTree(hypothesis));

    // Start the search
    toNaturalLI.write("\n");
    toNaturalLI.write("\n");
    toNaturalLI.flush();

    // Read the result
    Matcher matcher;
    String json = fromNaturalLI.readLine();
    if (json == null || !(matcher = alignmentIndexPattern.matcher(json)).find()) {
      throw new IllegalArgumentException("Invalid JSON response: " + json);
    }
    int alignmentIndex = Integer.parseInt(matcher.group(1));
    if (!(matcher = alignmentScorePattern.matcher(json)).find()) {
      throw new IllegalArgumentException("Invalid JSON response: " + json);
    }
    double alignmentScore = matcher.group(1).equals("-inf") ? Double.NEGATIVE_INFINITY : Double.parseDouble(matcher.group(1));
    if (!(matcher = alignmentSearchCostPattern.matcher(json)).find()) {
      throw new IllegalArgumentException("Invalid JSON response: " + json);
    }
    double alignmentSearchCost = matcher.group(1).equals("-inf") ? Double.NEGATIVE_INFINITY : Double.parseDouble(matcher.group(1));

    // Return
    return Pair.makePair(alignmentIndex, alignmentScore);
  }



  private double scoreBeforeNaturalli(Counter<String> features) {
    double sum = 0.0;
    for (Map.Entry<String, Double> entry : features.entrySet()) {
      switch (entry.getKey()) {
        case COUNT_ALIGNED:
        case PERCENT_ALIGNED_PREMISE:
        case PERCENT_ALIGNED_CONCLUSION:
        case PERCENT_ALIGNED_JOINT:
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
        case PERCENT_ALIGNED_PREMISE:
        case PERCENT_ALIGNED_CONCLUSION:
        case PERCENT_ALIGNED_JOINT:
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
    Pair<Integer, Double> bestNaturalLIScore;
    try {
      bestNaturalLIScore = getBestAlignment(premisesText, hypothesisText);
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
      if (bestNaturalLIScore.first == i) {
        score += bestNaturalLIScore.second;
      } else {
        score += scoreAlignmentSimple(features);
      }
      assert !Double.isNaN(score);
      assert Double.isFinite(score);
      // Computer the probability
      double prob = 1.0 / (1.0 + Math.exp(-score));
      // Discount the score if the focus is not present
      if (focus.isPresent() &&
          !focus.get().contains(" ") &&
          !premise.text().toLowerCase().replaceAll("\\s+", " ").contains(focus.get().toLowerCase().replaceAll("\\s+", " "))) {
        prob *= 0.25;
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
