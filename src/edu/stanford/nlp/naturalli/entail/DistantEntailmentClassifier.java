package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.WeightedRVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.naturalli.ProcessPremise;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Lazy;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Trilean;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * A distantly supervised entailment classifier.
 * At least one of the shortenings of the premise should entail the conclusion.
 *
 * @author Gabor Angeli
 *
 */
public class DistantEntailmentClassifier implements EntailmentClassifier {
  public final EntailmentFeaturizer featurizer;
  public final Classifier<Trilean, String> pZGivenX;

  private Lazy<StanfordCoreNLP> pipeline = Lazy.of(() -> ProcessPremise.constructPipeline("depparse"));

  public DistantEntailmentClassifier(EntailmentFeaturizer featurizer, Classifier<Trilean, String> classifier) {
    this.featurizer = featurizer;
    this.pZGivenX = classifier;
  }

  public DistantEntailmentClassifier(EntailmentFeaturizer featurizer, Classifier<Trilean, String> classifier, StanfordCoreNLP pipeline) {
    this(featurizer, classifier);
    this.pipeline = Lazy.from(pipeline);
  }

  @Override
  public Trilean classify(Sentence premise, Sentence hypothesis) {
    return classify(new DistantEntailmentPair(Trilean.UNKNOWN, premise.text(), hypothesis.text(), pipeline.get()));
  }

  @Override
  public double truthScore(Sentence premise, Sentence hypothesis) {
    return truthScore(new DistantEntailmentPair(Trilean.UNKNOWN, premise.text(), hypothesis.text(), pipeline.get()));
  }

  @SuppressWarnings("SuspiciousNameCombination")
  @Override
  public Object serialize() {
    return Pair.makePair(featurizer, pZGivenX);
  }

  public Trilean classify(DistantEntailmentPair ex) {
    boolean atLeastOnce = false;
    for (EntailmentPair z : ex.latentFactors()) {
      Trilean zPred = pZGivenX.classOf(new RVFDatum<>(featurizer.featurize(z, Optional.<DebugDocument>empty())));
      if (zPred.isTrue()) {
        atLeastOnce = true;
      }
    }
    return Trilean.from(atLeastOnce);
  }

  public double truthScore(DistantEntailmentPair ex) {
    double maxScore = 0.0f;
    for (EntailmentPair z : ex.latentFactors()) {
      double zScore = pZGivenX.scoresOf(new RVFDatum<>(featurizer.featurize(z, Optional.<DebugDocument>empty()))).getCount(Trilean.TRUE);
      maxScore = Math.max(zScore, maxScore);
    }
    return maxScore;
  }

  @SuppressWarnings("unchecked")
  public static DistantEntailmentClassifier deserialize(Object model) {
    Pair<EntailmentFeaturizer, Classifier<Trilean, String>> data = (Pair) model;
    return new DistantEntailmentClassifier(data.first, data.second);
  }

  private static Classifier<Trilean, String> trainIteration(Classifier<Trilean, String> pZX, List<DistantEntailmentPair> data,
                                                            ClassifierTrainer trainer) {
    // Variables of interest
    WeightedRVFDataset<Trilean, String> dataset = new WeightedRVFDataset<>();
    long totalGoodSamples = 0;
    long totalSamples = 0;
    double sumWeightTrue = 0.0;
    double sumWeightFalse = 0.0;

    // E Step
    forceTrack("E step");
    for (int datumI = 0; datumI < data.size(); ++datumI) {
      DistantEntailmentPair datum = data.get(datumI);
      List<EntailmentPair> zFactors = datum.latentFactors();

      List<RVFDatum<Trilean, String>> datums = new ArrayList<>(zFactors.size());
      for (EntailmentPair ex : zFactors) {
        datums.add(new RVFDatum<>(trainer.featurizer.featurize(ex, Optional.empty()), datum.truth));
      }

      if (datum.truth.isFalse() || datum.truth.isUnknown()) {
        // Easy case: the supervision is false.
        dataset.addAll(datums);  // default: weight 1.0
        sumWeightFalse += datums.size();
      } else if (datum.truth.isTrue()) {
        // Rejection sample for calibrated probabilities

        // (variables)
        int[] countTrue = new int[zFactors.size()];
        boolean[] sample = new boolean[zFactors.size()];
        double[] zProb = new double[zFactors.size()];
        int numGoodSamples = 0;
        // (compute z probabilities)
        for (int zI = 0; zI < zFactors.size(); ++zI) {
          Counter<Trilean> scores = pZX.scoresOf(datums.get(zI));
          Counters.logNormalizeInPlace(scores);
          Counters.expInPlace(scores);
          zProb[zI] = scores.getCount(Trilean.TRUE);
          assert zProb[zI] >= 0.0;
          assert zProb[zI] <= 1.0;
        }

        // (sample)
        Random rand = new Random();
        while (numGoodSamples < trainer.TRAIN_DISTSUP_SAMPLE_COUNT) {
          // ((draw sample))
          boolean atLeastOneTrue = false;
          for (int zI = 0; zI < zFactors.size(); ++zI) {
            if (rand.nextDouble() < zProb[zI]) {
              // ((drew a positive sample))
              sample[zI] = true;
              atLeastOneTrue = true;
            } else {
              // ((drew a negative sample))
              sample[zI] = false;
            }
          }
          // ((validate sample))
          if (atLeastOneTrue) {
            numGoodSamples += 1;
            totalGoodSamples += 1;
            // ((add sample))
            for (int zI = 0; zI < zFactors.size(); ++zI) {
              if (sample[zI]) {
                countTrue[zI] += 1;
              }
            }
          }
          totalSamples += 1;
        }

        // (add datum)
        for (int zI = 0; zI < zFactors.size(); ++zI) {
          double weightTrue = ((double) countTrue[zI]) / ((double) numGoodSamples);
          dataset.add(new RVFDatum<>(datums.get(zI).asFeaturesCounter(), Trilean.TRUE), (float) weightTrue);
          dataset.add(new RVFDatum<>(datums.get(zI).asFeaturesCounter(), Trilean.FALSE), (float) (1.0 - weightTrue));
          sumWeightTrue += weightTrue;
          sumWeightFalse += (1.0 - weightTrue);
        }
      }

      // Debug
      if (datumI % 100 == 0 && datumI != 0) {
        log("[" + datumI + "]  " + (totalSamples - totalGoodSamples) + " rejected / " + totalSamples + " = " + new DecimalFormat("0.000%").format((double) totalGoodSamples / (double) totalSamples));
      }
    }
    log("[" + data.size() + "]  " + (totalSamples - totalGoodSamples) + " rejected / " + totalSamples + " = " + new DecimalFormat("0.000%").format((double) totalGoodSamples / (double) totalSamples));
    log("[" + data.size() + "]  percent true: " + new DecimalFormat("0.000%").format(sumWeightTrue / (sumWeightTrue + sumWeightFalse)));
        endTrack("E step");

    // M Step
    forceTrack("M step");
    Classifier<Trilean, String> newClassifier = trainer.trainClassifier(dataset);
    endTrack("M step");

    // Return
    return newClassifier;
  }


  public static DistantEntailmentClassifier train(ClassifierTrainer trainer) throws IOException, ClassNotFoundException {
    startTrack("Training distant classifier");
    // Create the pipeline
    forceTrack("Creating a pipeline");
    StanfordCoreNLP pipeline = ProcessPremise.constructPipeline("depparse");
    endTrack("Creating a pipeline");


    // Read the test data
    forceTrack("Reading test data");
    Stream<DistantEntailmentPair> testData = trainer.readDistantDataset(trainer.TEST_FILE, trainer.TEST_CACHE, -1, pipeline);
    endTrack("Reading test data");

    // Read the training data
    forceTrack("Reading training data");
    List<DistantEntailmentPair> trainingData = trainer.readDistantDataset(trainer.TRAIN_FILE, trainer.TRAIN_CACHE, -1, pipeline).collect(Collectors.toList());
    endTrack("Reading training data");

    DistantEntailmentClassifier classifier = trainer.trainOrLoad(() -> {
      forceTrack("Training initial classifier");
      Classifier<Trilean, String> impl = trainer.trainClassifier(trainingData);
      endTrack("Training initial classifier");

      forceTrack("Running EM");
      for (int iter = 0; iter < trainer.TRAIN_DISTSUP_ITERS; ++iter) {
        forceTrack("EM Iteration " + iter);
        impl = trainIteration(impl, trainingData, trainer);
        endTrack("EM Iteration " + iter);
      }
      endTrack("Running EM");
      return new DistantEntailmentClassifier(trainer.featurizer, impl, pipeline);
    });


    // Calculate accuracy
    forceTrack("Results");
    // (calculation)
    List<Pair<Trilean, Trilean>> guessGold = testData.map(testDatum -> new Pair<>(classifier.classify(testDatum), testDatum.truth)).collect(Collectors.toList());
    int correct = 0;
    int correctAndTrue = 0;
    int pred = 0;
    int gold = 0;
    for (Pair<Trilean, Trilean> pair : guessGold) {
      if (pair.first.isTrue()) { pred += 1; }
      if (pair.second.isTrue()) { gold += 1; }
      if (pair.first.equals(pair.second)) { correct += 1; }
      if (pair.first.isTrue() && pair.second.isTrue()) { correctAndTrue += 1; }
    }
    // (print)
    DecimalFormat df = new DecimalFormat("0.00");
    log("Accuracy: " + df.format(((double) correct) / ((double) guessGold.size())));
    log("Majority: " + df.format( ( (gold > (guessGold.size()) / 2) ? ((double) gold) : ((double) (guessGold.size() - gold))) / ((double) guessGold.size())));
    log("");
    double p = ((double) correctAndTrue) / ((double) pred);
    log("P:        " + df.format(p));
    double r = ((double) correctAndTrue) / ((double) gold);
    log("R:        " + df.format(r));
    log("F1:       " + df.format(2.0 * p * r / (p + r)));
    endTrack("Results");

    endTrack("Training distant classifier");
    return classifier;
  }
}
