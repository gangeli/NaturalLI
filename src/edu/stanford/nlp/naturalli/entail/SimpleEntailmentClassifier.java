package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 *
 * A simple entailment classifier, based around a single classifier for each sentence pair.
 *
 * TODO(gabor) align test data on demand!
 *
 * @author Gabor Angeli
 */
public class SimpleEntailmentClassifier implements EntailmentClassifier {

  public final Classifier<Trilean, String> impl;
  public final EntailmentFeaturizer featurizer;
  public final Set<String> vocabulary;


  SimpleEntailmentClassifier(EntailmentFeaturizer featurizer, Set<String> vocabulary, Classifier<Trilean, String> impl) {
    this.impl = impl;
    this.vocabulary = vocabulary;
    this.featurizer = featurizer;
  }

  @Override
  public Trilean classify(Sentence premise, Sentence hypothesis, Optional<String> focus, Optional<Double> luceneScore) {
    return impl.classOf(new RVFDatum<>(featurizer.featurize(new EntailmentPair(Trilean.UNKNOWN, premise, hypothesis, focus, luceneScore), vocabulary, Optional.empty())));
  }

  @Override
  public double truthScore(Sentence premise, Sentence hypothesis, Optional<String> focus, Optional<Double> luceneScore) {
    Counter<Trilean> scores = impl.scoresOf(new RVFDatum<>(featurizer.featurize(new EntailmentPair(Trilean.UNKNOWN, premise, hypothesis, focus, luceneScore), vocabulary, Optional.empty())));
    Counters.logNormalizeInPlace(scores);
    Counters.expInPlace(scores);
    return scores.getCount(Trilean.TRUE);
  }

  @Override
  public Object serialize() {
    return Triple.makeTriple(featurizer, vocabulary, impl);
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

    Pointer<GeneralDataset<Trilean, String>> trainDataset = new Pointer<>();

    // Read the test data
    forceTrack("Reading test data");
    List<EntailmentPair> testData = trainer.readFlatDataset(trainer.TEST_FILE, trainer.TEST_CACHE, -1).collect(Collectors.toList());
    endTrack("Reading test data");

    // Train a model
    SimpleEntailmentClassifier classifier = trainer.trainOrLoad(() -> {
      Classifier<Trilean, String> impl;
      Set<String> vocab = new HashSet<>();
      try {
        DebugDocument debugDocument = trainer.TRAIN_DEBUGDOC.getPath().equals("/dev/null") ? null : new DebugDocument(trainer.TRAIN_DEBUGDOC);

        // Create the vocabulary
        if (trainer.VOCAB_THRESHOLD > 1) {
          forceTrack("Counting vocabulary");
          Counter<String> words = new ClassicCounter<>();
          Stream<EntailmentPair> vocabData = trainer.readFlatDataset(trainer.TRAIN_FILE, trainer.TRAIN_CACHE, trainer.TRAIN_COUNT);
          vocabData.forEach(pair -> {
            if (pair != null) {
              for (String word : pair.premise.lemmas()) {
                words.incrementCount(word);
              }
              for (String word : pair.conclusion.lemmas()) {
                words.incrementCount(word);
              }
            }
          });
          for (Map.Entry<String, Double> entry : words.entrySet()) {
            if (entry.getValue() >= trainer.VOCAB_THRESHOLD) {
              vocab.add(entry.getKey());
            }
          }
          endTrack("Counting vocabulary");
        }

        // Create the datasets
        forceTrack("Reading training data");
        List<EntailmentPair> trainData = trainer.readFlatDataset(trainer.TRAIN_FILE, trainer.TRAIN_CACHE, trainer.TRAIN_COUNT).collect(Collectors.toList());
        // Optionally align the data
        if (!trainer.featurizer.FEATURES_NOLEX &&
            ( trainer.featurizer.hasFeature(EntailmentFeaturizer.FeatureTemplate.MT_UNIGRAM) ||
                trainer.featurizer.hasFeature(EntailmentFeaturizer.FeatureTemplate.MT_PHRASE) ) ) {
          EntailmentPair.align(trainer.TRAIN_ALIGN_MINCOUNT, trainer.TRAIN_ALIGN_MAXLENGTH, trainData, testData);
        }
        trainDataset.set(trainer.featurizer.featurize(trainData.stream(), vocab, trainer.TRAIN_CACHE == null ? null : new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))), Optional.ofNullable(debugDocument), trainer.PARALLEL));
        if (trainer.TRAIN_CACHE != null) {
          IOUtils.cp(tmp, trainer.TRAIN_CACHE);
        }
        endTrack("Reading training data");

        // Training
        impl = trainer.trainClassifier(trainDataset.dereference().get());
        // (write the weights)
        startTrack("Top weights");
        if (impl instanceof LinearClassifier) {
          Map<Trilean, Counter<String>> weights = ((LinearClassifier<Trilean, String>) impl).weightsAsMapOfCounters();
          DecimalFormat df = new DecimalFormat("0.0000");
          for (Pair<String, Double> entry : Counters.toSortedListWithCounts(weights.get(Trilean.TRUE)).subList(0, Math.min(100, weights.get(Trilean.TRUE).size()))) {
            log(((entry.second < 0) ? "" : " ") + df.format(entry.second) + "    " + entry.first);
          }
        }
        endTrack("Top weights");
        // (debug document)
        forceTrack("Writing debug document");
        if (debugDocument != null) {
          debugDocument.write();
        }
        endTrack("Writing debug document");
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
      // (return the classifier)
      return new SimpleEntailmentClassifier(trainer.featurizer, vocab, impl);
    });

    // Create the test dataset object
    Set<String> vocab = classifier.vocabulary;
    GeneralDataset<Trilean, String> testDataset = trainer.featurizer.featurize(testData.stream(), vocab, trainer.TEST_CACHE == null ? null : new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))), Optional.empty(), trainer.PARALLEL);
    if (trainer.TEST_CACHE != null) { IOUtils.cp(tmp, trainer.TEST_CACHE); }

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
        Classifier<Trilean, String> foldClassifier = trainer.trainClassifier(data.first);
        sumAccuracy += foldClassifier.evaluateAccuracy(data.second);
        Pair<Double, Double> pr = foldClassifier.evaluatePrecisionAndRecall(data.second, Trilean.TRUE);
        sumP += pr.first;
        sumR += pr.second;
      }
      accuracy = sumAccuracy / 10.0;
      p = sumP / 10.0;
      r = sumR / 10.0;
    } else {
      // Evaluate accuracy
      log("");
      accuracy = classifier.impl.evaluateAccuracy(testDataset);
      Pair<Double, Double> pr = classifier.impl.evaluatePrecisionAndRecall(testDataset, Trilean.TRUE);
      p = pr.first;
      r = pr.second;
      // Print mistakes
      if (trainer.TEST_ERRORS != null && !"null".equals(trainer.TEST_ERRORS)) {
        PrintWriter errWriter = new PrintWriter(trainer.TEST_ERRORS);
        errWriter.println("Guess_Truth\tGold_Truth\tPremise\tHypothesis");
        for (EntailmentPair ex : testData) {
          Trilean guess = classifier.impl.classOf(new RVFDatum<>(trainer.featurizer.featurize(ex, vocab, Optional.empty())));
          if (!guess.equals(ex.truth)) {
            errWriter.println(guess + "\t" + ex.truth + "\t" + ex.premise.text() + "\t" + ex.conclusion.text());
          }
        }
        errWriter.close();
      }
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
    Triple<EntailmentFeaturizer, Set<String>, Classifier<Trilean, String>> data = (Triple) model;
    return new SimpleEntailmentClassifier(data.first, data.second, data.third);
  }

}
