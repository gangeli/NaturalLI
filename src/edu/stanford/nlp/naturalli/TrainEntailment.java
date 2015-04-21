package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.entail.BleuMeasurer;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.kbp.common.CollectionUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Trilean;
import scala.tools.nsc.backend.icode.Primitives;

import java.io.*;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * Train an entailment classifier, constrained to various degrees by natural logic.
 *
 * @author Gabor Angeli
 */
public class TrainEntailment {

  @Execution.Option(name="trainFile", gloss="The file to use for training the classifier")
  public static File TRAIN_FILE = new File("tmp/aristo_train.tab");
  @Execution.Option(name="trainCache", gloss="A cache of the training annotations")
  public static File TRAIN_CACHE = null;

  @Execution.Option(name="testFile", gloss="The file to use for testing the classifier")
  public static File TEST_FILE = new File("tmp/aristo_test.tab");
  @Execution.Option(name="testCache", gloss="A cache of the test annotations")
  public static File TEST_CACHE = null;

  @Execution.Option(name="model", gloss="The file to load/save the model to/from.")
  public static File MODEL = new File("tmp/model.ser.gz");
  @Execution.Option(name="retrain", gloss="If true, force the retraining of the classifier")
  public static boolean RETRAIN = true;

  @Execution.Option(name="lexicalize", gloss="If true, use lexical features")
  public static boolean LEXICALIZE = false;


  private static class EntailmentPair {
    Trilean truth;
    Sentence premise;
    Sentence conclusion;

    public EntailmentPair(Trilean truth, String premise, String conclusion) {
      this.truth = truth;
      this.premise = new Sentence(premise);
      this.conclusion = new Sentence(conclusion);
    }

    public EntailmentPair(Trilean truth, Sentence premise, Sentence conclusion) {
      this.truth = truth;
      this.premise = premise;
      this.conclusion = conclusion;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public void serialize(final OutputStream cacheStream) {
      try {
        synchronized (cacheStream) {
          if (truth.isTrue()) {
            cacheStream.write(1);
          } else if (truth.isFalse()) {
            cacheStream.write(0);
          } else if (truth.isUnknown()) {
            cacheStream.write(2);
          } else {
            throw new IllegalStateException();
          }
          premise.serialize(cacheStream);
          conclusion.serialize(cacheStream);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public static Stream<EntailmentPair> deserialize(InputStream cacheStream) throws IOException {
      return Stream.generate(() -> {
        try {
          synchronized (cacheStream) {
            // Read the truth byte
            int truthByte;
            try {
              truthByte = cacheStream.read();
            } catch (IOException e) {
              return null;  // Stream closed exception
            }
            // Convert the truth byte to a truth value
            Trilean truth;
            switch (truthByte) {
              case -1:
                cacheStream.close();
                return null;
              case 0:
                truth = Trilean.FALSE;
                break;
              case 1:
                truth = Trilean.TRUE;
                break;
              case 2:
                truth = Trilean.UNKNOWN;
                break;
              default:
                throw new IllegalStateException("Error parsing cache!");
            }
            // Read the sentences
            Sentence premise = Sentence.deserialize(cacheStream);
            Sentence conclusion = Sentence.deserialize(cacheStream);
            // Create the datum
            return new EntailmentPair(truth, premise, conclusion);
          }
        } catch(IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }


  private static Stream<EntailmentPair> readDataset(File source, File cache) throws IOException {
    // Read size
    int size = 0;
    forceTrack("Counting lines");
    BufferedReader scanner = IOUtils.readerFromFile(source);
    while (scanner.readLine() != null) {
      size += 1;
    }
    log("dataset has size " + size);
    endTrack("Counting lines");

    // Create stream
    if (cache.exists()) {
      log("reading from cache (" + cache + ")");
      return EntailmentPair.deserialize(new GZIPInputStream(new BufferedInputStream(new FileInputStream(cache)))).limit(size);
    } else {
      log("no cached version found. Reading from file (" + source + ")");
      BufferedReader reader = IOUtils.readerFromFile(source);
      return Stream.generate(() -> {
        try {
          String line;
          synchronized (reader) {  // In case we're reading multithreaded
            line = reader.readLine();
          }
          if (line == null) {
            return null;
          }
          // Parse the line
          String[] fields = line.split("\t");
          Trilean truth = Trilean.fromString(fields[1].toLowerCase());
          String premise = fields[2];
          String conclusion = fields[3];
          // Add the pair
          return new EntailmentPair(truth, premise, conclusion);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }).limit(size);
    }

  }

  private static Counter<String> featurize(EntailmentPair ex) {
    ClassicCounter<String> feats = new ClassicCounter<>();

    // Lemma overlap
    int intersect = CollectionUtils.intersect(new HashSet<>(ex.premise.lemmas()), new HashSet<>(ex.conclusion.lemmas())).size();
    feats.incrementCount("lemma_overlap", intersect);
    feats.incrementCount("lemma_overlap_percent", ((double) intersect) / ((double) Math.min(ex.premise.length(), ex.conclusion.length())));

    // BLEU score
    BleuMeasurer bleuScorer = new BleuMeasurer(3);
    bleuScorer.addSentence(ex.premise.lemmas().toArray(new String[ex.premise.length()]),
        ex.conclusion.lemmas().toArray(new String[ex.conclusion.length()]));
    double bleu = bleuScorer.bleu();
    if (Double.isNaN(bleu)) {
      bleu = 0.0;
    }
    feats.incrementCount("BLEU", bleu);

    // Relevant POS intersection
    for (char pos : new HashSet<Character>(){{ add('V'); add('N'); add('J'); add('R'); }}) {
      Set<String> premiseVerbs = new HashSet<>();
      for (int i = 0; i < ex.premise.length(); ++i) {
        if(ex.premise.posTag(i).charAt(0) == pos) {
          premiseVerbs.add(ex.premise.lemma(i));
        }
      }
      Set<String> conclusionVerbs = new HashSet<>();
      for (int i = 0; i < ex.conclusion.length(); ++i) {
        if(ex.conclusion.posTag(i).charAt(0) == pos) {
          premiseVerbs.add(ex.conclusion.lemma(i));
        }
      }
      Set<String> intersectVerbs = CollectionUtils.intersect(premiseVerbs, conclusionVerbs);
      feats.incrementCount("" + pos + "_overlap_percent", ((double) intersectVerbs.size() / ((double) Math.min(ex.premise.length(), ex.conclusion.length()))));
      feats.incrementCount("" + pos + "_overlap: " + intersectVerbs.size());
    }

    // Length differences
    feats.incrementCount("length_diff:" + (ex.conclusion.length() - ex.premise.length()));
    feats.incrementCount("conclusion_longer?:" + (ex.conclusion.length() > ex.premise.length()));
    feats.incrementCount("conclusion_length:" + ex.conclusion.length());

    // Unigram entailments
    if (LEXICALIZE) {
      for (int pI = 0; pI < ex.premise.length(); ++pI) {
        for (int cI = 0; cI < ex.conclusion.length(); ++cI) {
          if (ex.premise.posTag(pI).charAt(0) == ex.conclusion.posTag(cI).charAt(0)) {
            feats.incrementCount("lemma_entail:" + ex.premise.lemma(pI) + "_->_" + ex.conclusion.lemma(cI));
          }
        }
      }
    }

    // Consequent uni/bi-gram
    if (LEXICALIZE) {
      for (int i = 0; i < ex.conclusion.length(); ++i) {
        String elem = "^_";
        if (i > 0) {
          elem = ex.conclusion.lemma(i - 1) + "_";
        }
        elem += ex.conclusion.lemma(i);
        feats.incrementCount("conclusion_unigram:" + ex.conclusion.lemma(i));
        feats.incrementCount("conclusion_bigram:" + elem);
      }
    }

    return feats;
  }

  private static GeneralDataset<Trilean, String> featurize(Stream<EntailmentPair> data, OutputStream cacheStream) throws IOException {
    GeneralDataset<Trilean, String> dataset = new RVFDataset<>();
    final AtomicInteger i = new AtomicInteger(0);
    data.parallel().forEach(ex -> {
      if (ex != null) {
        Counter<String> featurized = featurize(ex);
        if (i.incrementAndGet() % 1000 == 0) {
          log("featurized " + (i.get() / 1000) + "k examples");
        }
        synchronized (TrainEntailment.class) {
          dataset.add(new RVFDatum<>(featurized, ex.truth));
          ex.serialize(cacheStream);
        }
      }
    });
    cacheStream.close();
    return dataset;
  }


  public static void main(String[] args) throws IOException, ClassNotFoundException {
    // Initialize the parameters
    Execution.fillOptions(TrainEntailment.class, args);
    forceTrack("main");
    if (TRAIN_CACHE == null) {
      TRAIN_CACHE = new File(TRAIN_FILE.getPath() + ".cache");
    }
    if (TEST_CACHE == null) {
      TEST_CACHE = new File(TEST_FILE.getPath() + ".cache");
    }
    File tmp = File.createTempFile("cache", ".ser.gz");
    tmp.deleteOnExit();

    // Read the test data
    forceTrack("Reading test data");
    Stream<EntailmentPair> testData = readDataset(TEST_FILE, TEST_CACHE);
    GeneralDataset<Trilean, String> testDataset = featurize(testData, new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))));
    IOUtils.cp(tmp, TEST_CACHE);
    endTrack("Reading test data");

    // Train a model
    LinearClassifier<Trilean, String> classifier;
    if (RETRAIN || MODEL == null || !MODEL.exists()) {
      // Create the datasets
      forceTrack("Reading training data");
      Stream<EntailmentPair> trainData = readDataset(TRAIN_FILE, TRAIN_CACHE);
      GeneralDataset<Trilean, String> trainDataset = featurize(trainData, new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))));
      IOUtils.cp(tmp, TRAIN_CACHE);
      endTrack("Reading training data");

      // Training
      // (create factory)
      forceTrack("Training the classifier");
      LinearClassifierFactory<Trilean, String> factory = new LinearClassifierFactory<>();
      log("created factory");
      // (train classifier)
      classifier = factory.trainClassifier(trainDataset);
      // (save classifier)
      log("trained classifier");
      if (MODEL != null) {
        try {
          IOUtils.writeObjectToFile(classifier, MODEL);
          log("saved classifier to " + MODEL);
        } catch (Throwable t) {
          err("ERROR SAVING MODEL TO: " + MODEL);
          t.printStackTrace();
        }
      } else {
        log("no file to save the classifier to.");
      }
      // (evaluate training accuracy)
      log("Training accuracy: " + classifier.evaluateAccuracy(trainDataset));
      endTrack("Training the classifier");
    } else {
      classifier = IOUtils.readObjectFromFile(MODEL);
    }

    // Evaluating
    forceTrack("Evaluating the classifier");
    double accuracy = classifier.evaluateAccuracy(testDataset);
    Pair<Double, Double> pr = classifier.evaluatePrecisionAndRecall(testDataset, Trilean.TRUE);
    DecimalFormat df = new DecimalFormat("0.00%");
    log("Accuracy: " + df.format(accuracy));
    log("P:        " + df.format(pr.first));
    log("R:        " + df.format(pr.second));
    log("F1:       " + df.format(2.0 * pr.first * pr.second / (pr.first + pr.second)));
    endTrack("Evaluating the classifier");

    endTrack("main");
  }
}



