package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.naturalli.ProcessQuery;
import edu.stanford.nlp.naturalli.StaticResources;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.endTrack;
import static edu.stanford.nlp.util.logging.Redwood.Util.forceTrack;

/**
 * An interface for calling NaturalLI in alignment mode.
 *
 * @author Gabor Angeli
 */
public class NaturalLIClassifier implements EntailmentClassifier {

  static final String PREMISE_KEYWORD_COUNT = "premiseKeywordCount";
  static final String CONCLUSION_KEYWORD_COUNT = "conclusionKeywordCount";
  static final String ALIGNED_KEYWORD_COUNT = "alignedKeywordCount";

  static final String ONLY_IN_PREMISE = "onlyInPremise";
  static final String ONLY_IN_CONCLUSION = "onlyInConclusion";
  static final String ANY_OVERLAP_COUNT = "anyOverlapCount";
  static final String CONCLUSION_OVERLAP_NO = "conclusionOverlapNo";
  static final String CONCLUSION_OVERLAP_PERFECT = "conclusionOverlapPerfect";
  static final String JOINT_OVERLAP_PERFECT = "jointOverlapPerfect";
  static final String JOINT_OVERLAP_PERFECT_COUNT = "jointOverlapPerfectCount";
  static final String JOINT_OVERLAP_NO_COUNT = "jointOverlapNoCount";

  private static final Pattern alignmentIndexPattern = Pattern.compile("\"closestSoftAlignment\": ([0-9]+)");
  private static final Pattern alignmentScorePattern = Pattern.compile("\"closestSoftAlignmentScore\": (-?(:?[0-9\\.]+|inf))");
  private static final Pattern alignmentSearchCostPattern = Pattern.compile("\"closestSoftAlignmentSearchCost\": (-?(:?[0-9\\.]+|inf))");


  public final String searchProgram;
  public final Classifier<Trilean, String> impl;
  public final EntailmentFeaturizer featurizer;

  private BufferedReader fromNaturalLI;
  private OutputStreamWriter toNaturalLI;
  private final Counter<String> weights;


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
      Writer errWriter = new BufferedWriter(new OutputStreamWriter(System.err));
      StreamGobbler errGobbler = new StreamGobbler(searcher.getErrorStream(), errWriter);
      errGobbler.start();

      // Create the pipe
      fromNaturalLI = new BufferedReader(new InputStreamReader(searcher.getInputStream()));
      toNaturalLI = new OutputStreamWriter(new BufferedOutputStream(searcher.getOutputStream()));


      endTrack("Creating connection to NaturalLI");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  /*
  private String generateAlignmentSpec(String premiseCoNLL, String hypothesisCoNLL, Counter<String> features) {
    // Create a premise and hypothesis sentence
    Sentence premise = new Sentence(Arrays.asList(premiseCoNLL.split("\n")).stream().map(x -> StaticResources.SURFACE_FORM.get().get(Integer.parseInt(x.split("\t")[0]))).collect(Collectors.toList()));
    Sentence hypothesis = new Sentence(Arrays.asList(hypothesisCoNLL.split("\n")).stream().map(x -> StaticResources.SURFACE_FORM.get().get(Integer.parseInt(x.split("\t")[0]))).collect(Collectors.toList()));
    // Align the sentences
    Set<EntailmentFeaturizer.KeywordPair> alignment = EntailmentFeaturizer.alignTokens(premise, hypothesis);
    //
    return ""; // TODO(gabor)
  }
  */

//  /**
//   * Get the best alignment score between the hypothesis and the best premise.
//   * @param premises The premises to possibly align to.
//   * @param hypothesis The hypothesis we are testing.
//   * @return The alignment score returned from NaturalLI.
//   * @throws IOException Thrown if the pipe to NaturalLI is broken.
//   */
  /*
  private synchronized double getBestAlignment(List<String> premises, String hypothesis) throws IOException {
    // Write the premises
    for (String premise : premises) {
//      toNaturalLI.write("%alignment = " + generateAlignmentSpec(premise, hypothesis) + "\n");
      toNaturalLI.write(premise);
    }

    // Write the query
    toNaturalLI.write(hypothesis);

    // Start the search
    toNaturalLI.write("\n");
    toNaturalLI.flush();

    // Read the result
    Matcher matcher;
    String json = fromNaturalLI.readLine();
    if (!(matcher = alignmentIndexPattern.matcher(json)).matches()) {
      throw new IllegalArgumentException("Invalid JSON response: " + json);
    }
    int alignmentIndex = Integer.parseInt(matcher.group(1));
    if (!(matcher = alignmentScorePattern.matcher(json)).matches()) {
      throw new IllegalArgumentException("Invalid JSON response: " + json);
    }
    double alignmentScore = matcher.group(1).equals("-inf") ? Double.NEGATIVE_INFINITY : Double.parseDouble(matcher.group(1));
    if (!(matcher = alignmentSearchCostPattern.matcher(json)).matches()) {
      throw new IllegalArgumentException("Invalid JSON response: " + json);
    }
    double alignmentSearchCost = matcher.group(1).equals("-inf") ? Double.NEGATIVE_INFINITY : Double.parseDouble(matcher.group(1));

    // Return
    return alignmentScore;
  }
  */

  private double scoreBeforeNaturalli(Counter<String> features) {
    double sum = 0.0;
    for (Map.Entry<String, Double> entry : features.entrySet()) {
      switch (entry.getKey()) {
        case ONLY_IN_PREMISE:
        case ONLY_IN_CONCLUSION:
        case ANY_OVERLAP_COUNT:
        case CONCLUSION_OVERLAP_NO:
        case CONCLUSION_OVERLAP_PERFECT:
        case JOINT_OVERLAP_PERFECT:
        case JOINT_OVERLAP_PERFECT_COUNT:
        case JOINT_OVERLAP_NO_COUNT:
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
        case ONLY_IN_PREMISE:
        case ONLY_IN_CONCLUSION:
        case ANY_OVERLAP_COUNT:
        case CONCLUSION_OVERLAP_NO:
        case CONCLUSION_OVERLAP_PERFECT:
        case JOINT_OVERLAP_PERFECT:
        case JOINT_OVERLAP_PERFECT_COUNT:
        case JOINT_OVERLAP_NO_COUNT:
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

    for (int i = 0; i < premises.size(); ++i) {
      // Featurize the pair
      Sentence premise = premises.get(i);
      Optional<Double> luceneScore = luceneScores.isPresent() ? Optional.of(luceneScores.get().get(i)) : Optional.empty();
      Counter<String> features = featurizer.featurize(new EntailmentPair(Trilean.UNKNOWN, premise, hypothesis, focus, luceneScore), Optional.empty());
      // Get the raw score
      double score = scoreBeforeNaturalli(features) + scoreAlignmentSimple(features);
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
