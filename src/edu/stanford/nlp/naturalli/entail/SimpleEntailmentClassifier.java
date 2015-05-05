package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 *
 * A simple entailment classifier, based around a single classifier for each sentence pair.
 *
 * @author Gabor Angeli
 */
public class SimpleEntailmentClassifier implements EntailmentClassifier {

  public final Classifier<Trilean, String> impl;
  public final EntailmentFeaturizer featurizer;


  SimpleEntailmentClassifier(EntailmentFeaturizer featurizer, Classifier<Trilean, String> impl) {
    this.impl = impl;
    this.featurizer = featurizer;
  }

  @Override
  public Trilean classify(Sentence premise, Sentence hypothesis) {
    return impl.classOf(new RVFDatum<>(featurizer.featurize(new EntailmentPair(Trilean.UNKNOWN, premise, hypothesis), Optional.empty())));
  }

  @Override
  public double truthScore(Sentence premise, Sentence hypothesis) {
    return impl.scoresOf(new RVFDatum<>(featurizer.featurize(new EntailmentPair(Trilean.UNKNOWN, premise, hypothesis), Optional.empty()))).getCount(Trilean.TRUE);
  }

  @Override
  public Object serialize() {
    return Pair.makePair(featurizer, impl);
  }

  /**
   * Train a simple classifier.
   *
   * @param trainer The command line arguments, as filled by {@link edu.stanford.nlp.util.Execution}.
   *
   * @throws IOException
   */
  public static SimpleEntailmentClassifier train(ClassifierTrainer trainer) throws IOException, ClassNotFoundException {
    // Initialize the parameters
    startTrack("SimpleEntailmentClassifier.train()");
    File tmp = File.createTempFile("cache", ".ser.gz");
    tmp.deleteOnExit();

    // Read the test data
    forceTrack("Reading test data");
    Stream<EntailmentPair> testData = trainer.readFlatDataset(trainer.TEST_FILE, trainer.TEST_CACHE, -1);
    GeneralDataset<Trilean, String> testDataset = trainer.featurizer.featurize(testData, trainer.TEST_CACHE == null ? null : new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))), Optional.empty(), trainer.PARALLEL);
    if (trainer.TEST_CACHE != null) { IOUtils.cp(tmp, trainer.TEST_CACHE); }
    endTrack("Reading test data");

    Pointer<GeneralDataset<Trilean, String>> trainDataset = new Pointer<>();

    // Train a model
    SimpleEntailmentClassifier classifier = trainer.trainOrLoad(() -> {
      LinearClassifier<Trilean, String> impl;
      try {
        DebugDocument debugDocument = new DebugDocument(trainer.TRAIN_DEBUGDOC);
        // Create the datasets
        forceTrack("Reading training data");
        Stream<EntailmentPair> trainData = trainer.readFlatDataset(trainer.TRAIN_FILE, trainer.TRAIN_CACHE, trainer.TRAIN_COUNT);
        trainDataset.set(trainer.featurizer.featurize(trainData, trainer.TRAIN_CACHE == null ? null : new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))), Optional.of(debugDocument), trainer.PARALLEL));
        if (trainer.TRAIN_CACHE != null) {
          IOUtils.cp(tmp, trainer.TRAIN_CACHE);
        }
        endTrack("Reading training data");

        // Training
        impl = trainer.trainClassifier(trainDataset.dereference().get());
        // (write the weights)
        startTrack("Top weights");
        Map<Trilean, Counter<String>> weights = impl.weightsAsMapOfCounters();
        DecimalFormat df = new DecimalFormat("0.0000");
        for (Pair<String, Double> entry : Counters.toSortedListWithCounts(weights.get(Trilean.TRUE)).subList(0, Math.min(100, weights.get(Trilean.TRUE).size()))) {
          log(((entry.second < 0) ? "" : " ") + df.format(entry.second) + "    " + entry.first);
        }
        endTrack("Top weights");
        // (debug document)
        forceTrack("Writing debug document");
        debugDocument.write();
        endTrack("Writing debug document");
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
      // (return the classifier)
      return new SimpleEntailmentClassifier(trainer.featurizer, impl);
    });

    // Evaluating
    forceTrack("Evaluating the classifier");
    double accuracy;
    double p;
    double r;
    if (trainer.TRAIN_FILE.getPath().equals(trainer.TEST_FILE.getPath()) && trainDataset.dereference().isPresent()) {
      double sumAccuracy = 0.0;
      double sumP = 0.0;
      double sumR = 0.0;
      for (int i = 0; i < 10; ++i) {
        Pair<GeneralDataset<Trilean, String>, GeneralDataset<Trilean, String>> data = trainDataset.dereference().get().splitOutFold(i, 10);
        LinearClassifier<Trilean, String> foldClassifier = trainer.trainClassifier(data.first);
        sumAccuracy += foldClassifier.evaluateAccuracy(data.second);
        Pair<Double, Double> pr = foldClassifier.evaluatePrecisionAndRecall(data.second, Trilean.TRUE);
        sumP += pr.first;
        sumR += pr.second;
      }
      accuracy = sumAccuracy / 10.0;
      p = sumP / 10.0;
      r = sumR / 10.0;
    } else {
      log("");
      accuracy = classifier.impl.evaluateAccuracy(testDataset);
      Pair<Double, Double> pr = classifier.impl.evaluatePrecisionAndRecall(testDataset, Trilean.TRUE);
      p = pr.first;
      r = pr.second;
    }

    // Report results
    log("");
    forceTrack("Score");
    DecimalFormat df = new DecimalFormat("0.00%");
    log("Majority: " + df.format(Counters.max(testDataset.numDatumsPerLabel()) / testDataset.size()));
    if (trainDataset.dereference().isPresent()) {
      log("Training: " + df.format(classifier.impl.evaluateAccuracy(trainDataset.dereference().get())));
    }
    log("");
    log("Accuracy: " + df.format(accuracy));
    log("");
    log("P:        " + df.format(p));
    log("R:        " + df.format(r));
    log("F1:       " + df.format(2.0 * p * r / (p + r)));
    endTrack("Score");
    log("");
    endTrack("Evaluating the classifier");

    endTrack("SimpleEntailmentClassifier.train()");
    return classifier;
  }


  @SuppressWarnings("unchecked")
  public static SimpleEntailmentClassifier deserialize(Object model) {
    Pair<EntailmentFeaturizer, Classifier<Trilean, String>> data = (Pair) model;
    return new SimpleEntailmentClassifier(data.first, data.second);
  }

}
