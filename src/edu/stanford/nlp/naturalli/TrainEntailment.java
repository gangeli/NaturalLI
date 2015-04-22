package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.entail.BleuMeasurer;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.kbp.common.CollectionUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Trilean;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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

  @Execution.Option(name="train.file", gloss="The file to use for training the classifier")
  public static File TRAIN_FILE = new File("tmp/snli_train.tab.large");
  @Execution.Option(name="train.cache", gloss="A cache of the training annotations")
  public static File TRAIN_CACHE = null;
  @Execution.Option(name="train.cache.do", gloss="If false, do not cache the training annotations")
  public static boolean TRAIN_CACHE_DO = true;
  @Execution.Option(name="train.count", gloss="The number of training examples to use.")
  public static int TRAIN_COUNT = -1;
  @Execution.Option(name="train.feature_count_threshold", gloss="The minimum number of times we have to see a feature before considering it.")
  public static int TRAIN_FEATURE_COUNT_THRESHOLD = 0;
  @Execution.Option(name="train.sigma", gloss="The regularization constant sigma for the classifier")
  public static double TRAIN_SIGMA = 1.0;

  @Execution.Option(name="testFile", gloss="The file to use for testing the classifier")
  public static File TEST_FILE = new File("tmp/snli_test.tab");
  @Execution.Option(name="testCache", gloss="A cache of the test annotations")
  public static File TEST_CACHE = null;
  @Execution.Option(name="test.cache.do", gloss="If false, do not cache the test annotations")
  public static boolean TEST_CACHE_DO = true;

  @Execution.Option(name="model", gloss="The file to load/save the model to/from.")
  public static File MODEL = new File("tmp/model.ser.gz");
  @Execution.Option(name="retrain", gloss="If true, force the retraining of the classifier")
  public static boolean RETRAIN = true;

  enum FeatureTemplate {
    BLEU, LENGTH_DIFF,
    OVERLAP, POS_OVERLAP, KEYWORD_OVERLAP,
    ENTAIL_UNIGRAM, ENTAIL_BIGRAM, ENTAIL_KEYWORD,
    CONCLUSION_NGRAM,
  }
  @Execution.Option(name="features", gloss="The feature templates to use during training")
  public static Set<FeatureTemplate> FEATURE_TEMPLATES = new HashSet<FeatureTemplate>(){{
    add(FeatureTemplate.BLEU);
    add(FeatureTemplate.LENGTH_DIFF);
    add(FeatureTemplate.OVERLAP);
    add(FeatureTemplate.POS_OVERLAP);
    add(FeatureTemplate.KEYWORD_OVERLAP);
    add(FeatureTemplate.ENTAIL_UNIGRAM);
    add(FeatureTemplate.ENTAIL_BIGRAM);
    add(FeatureTemplate.ENTAIL_KEYWORD);
    add(FeatureTemplate.CONCLUSION_NGRAM);
  }};
  @Execution.Option(name="features.nolex", gloss="If true, prohibit all lexical features")
  public static boolean FEATURES_NOLEX = false;


  /**
   * A candidate entailment pair. This corresponds to an un-featurized datum.
   */
  private static class EntailmentPair {
    public final Trilean truth;
    public final Sentence premise;
    public final Sentence conclusion;

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

  /**
   * A pair of aligned keywords that exist in either one of, or both the premise and the hypothesis.
   */
  private static class KeywordPair {
    public final Sentence premise;
    public final Span premiseSpan;
    public final Sentence conclusion;
    public final Span conclusionSpan;

    public KeywordPair(Sentence premise, Span premiseSpan, Sentence conclusion, Span conclusionSpan) {
      this.premise = premise;
      this.premiseSpan = premiseSpan == null ? new Span(-1, -1) : premiseSpan;
      this.conclusion = conclusion;
      this.conclusionSpan = conclusionSpan == null ? new Span(-1, -1) : conclusionSpan;
    }

    public String premiseChunk() {
      return StringUtils.join(premise.lemmas().subList(premiseSpan.start(), premiseSpan.end()), " ");
    }

    public String conclusionChunk() {
      return StringUtils.join(conclusion.lemmas().subList(conclusionSpan.start(), conclusionSpan.end()), " ");
    }

    public boolean isAligned() {
      return premiseSpan.size() > 0 && conclusionSpan.size() > 0;
    }

    @Override
    public String toString() {
      return "< " + (premiseSpan.size() > 0 ? premiseChunk() : "--- ") + "; " + (conclusionSpan.size() > 0 ? conclusionChunk() : "---") + " >";
    }
  }

  private static Set<KeywordPair> align(EntailmentPair ex) {
    // Get the spans
    List<Span> premiseSpans = ex.premise.algorithms().keyphraseSpans();
    List<Span> conclusionSpans = ex.conclusion.algorithms().keyphraseSpans();

    // Get other useful metadata
    List<String> premisePhrases = ex.premise.algorithms().keyphrases(Sentence::lemmas).stream().map(String::toLowerCase).collect(Collectors.toList());
    boolean[] premiseConsumed = new boolean[premiseSpans.size()];
    List<String> conclusionPhrases = ex.conclusion.algorithms().keyphrases(Sentence::lemmas).stream().map(String::toLowerCase).collect(Collectors.toList());
    boolean[] conclusionConsumed = new boolean[conclusionSpans.size()];
    int[] conclusionForPremise = new int[premiseSpans.size()];
    Arrays.fill(conclusionForPremise, -1);

    // The return set
    Set<KeywordPair> alignments = new HashSet<>();

    // Pass 1: exact match
    for (int pI = 0; pI < premiseSpans.size(); ++pI) {
      if (premiseConsumed[pI]) { continue; }
      for (int cI = 0; cI < conclusionSpans.size(); ++cI) {
        if (conclusionConsumed[cI]) { continue; }
        if (premisePhrases.get(pI).equals(conclusionPhrases.get(cI))) {
          alignments.add(new KeywordPair(ex.premise, premiseSpans.get(pI), ex.conclusion, conclusionSpans.get(cI)));
          premiseConsumed[pI] = true;
          conclusionConsumed[cI] = true;
          conclusionForPremise[pI] = cI;
          break;
        }
      }
    }

    // Pass 2: approximate match (ends with)
    for (int pI = 0; pI < premiseSpans.size(); ++pI) {
      if (premiseConsumed[pI]) { continue; }
      for (int cI = 0; cI < conclusionSpans.size(); ++cI) {
        if (conclusionConsumed[cI]) { continue; }
        if (premisePhrases.get(pI).endsWith(conclusionPhrases.get(cI)) || conclusionPhrases.get(cI).endsWith(premisePhrases.get(pI))) {
          alignments.add(new KeywordPair(ex.premise, premiseSpans.get(pI), ex.conclusion, conclusionSpans.get(cI)));
          premiseConsumed[pI] = true;
          conclusionConsumed[cI] = true;
          conclusionForPremise[pI] = cI;
          break;
        }
      }
    }

    // Pass 3: approximate match (starts with)
    for (int pI = 0; pI < premiseSpans.size(); ++pI) {
      if (premiseConsumed[pI]) { continue; }
      for (int cI = 0; cI < conclusionSpans.size(); ++cI) {
        if (conclusionConsumed[cI]) { continue; }
        if (premisePhrases.get(pI).startsWith(conclusionPhrases.get(cI)) || conclusionPhrases.get(cI).startsWith(premisePhrases.get(pI))) {
          alignments.add(new KeywordPair(ex.premise, premiseSpans.get(pI), ex.conclusion, conclusionSpans.get(cI)));
          premiseConsumed[pI] = true;
          conclusionConsumed[cI] = true;
          conclusionForPremise[pI] = cI;
          break;
        }
      }
    }

    // Pass 4: constrained POS match
    //noinspection StatementWithEmptyBody
    if (premiseConsumed.length == 0) {
      // noop
    } else if (premiseConsumed.length == 1) {
      // Case: one token keywords
      if (conclusionSpans.size() == 1 && conclusionForPremise[0] == -1 &&
          ex.premise.algorithms().modeInSpan(premiseSpans.get(0), Sentence::posTags).charAt(0) == ex.conclusion.algorithms().modeInSpan(conclusionSpans.get(0), Sentence::posTags).charAt(0)) {
        // ...and the POS tags match, and there's only one consequent. They must align.
        alignments.add(new KeywordPair(ex.premise, premiseSpans.get(0), ex.conclusion, conclusionSpans.get(0)));
        premiseConsumed[0] = true;
        conclusionConsumed[0] = true;
        conclusionForPremise[0] = 0;
      }
    } else {
      // Case: general alignment
      for (int i = 0; i < conclusionForPremise.length; ++i) {
        if (conclusionForPremise[i] < 0) {
          // find a candidate alignment for token 'i'
          int candidateAlignment = -1;
          if (i == 0 && conclusionForPremise[i + 1] > 0) {
            candidateAlignment = conclusionForPremise[i + 1] - 1;
          } else if (i == conclusionForPremise.length - 1 && conclusionForPremise[i - 1] >= 0 && conclusionForPremise[i - 1] < conclusionSpans.size() - 1) {
            candidateAlignment = conclusionForPremise[i - 1] + 1;
          } else if (i > 0 && i < conclusionForPremise.length - 1 &&
              conclusionForPremise[i - 1] >= 0 && conclusionForPremise[i + 1] >= 0 &&
              conclusionForPremise[i + 1] - conclusionForPremise[i - 1] == 2) {
            candidateAlignment = conclusionForPremise[i - 1] + 1;
          }
          // sanity check that alignment (e.g., with POS tags)
          if (candidateAlignment >= 0 &&
              ex.premise.algorithms().modeInSpan(premiseSpans.get(i), Sentence::posTags).charAt(0) == ex.conclusion.algorithms().modeInSpan(conclusionSpans.get(candidateAlignment), Sentence::posTags).charAt(0)) {
            // Add the alignment
            alignments.add(new KeywordPair(ex.premise, premiseSpans.get(i), ex.conclusion, conclusionSpans.get(candidateAlignment)));
            premiseConsumed[i] = true;
            conclusionConsumed[candidateAlignment] = true;
            conclusionForPremise[i] = candidateAlignment;
          }
        }
      }
    }

    // Finally: unaligned keywords
    for (int i = 0; i < premiseSpans.size(); ++i) {
      if (!premiseConsumed[i]) {
        alignments.add(new KeywordPair(ex.premise, premiseSpans.get(i), ex.conclusion, null));
        premiseConsumed[i] = true;
      }
    }
    for (int i = 0; i < conclusionSpans.size(); ++i) {
      if (!conclusionConsumed[i]) {
        alignments.add(new KeywordPair(ex.premise, null, ex.conclusion, conclusionSpans.get(i)));
        conclusionConsumed[i] = true;
      }
    }

    // Return
    return alignments;
  }

  /**
   * Read a dataset from a given source, backing off to a given (potentially null or empty) cache.
   *
   * @param source The source file to read the dataset from.
   * @param cache The cache file to use if it exists and is non-empty.
   * @param size The number of datums to read.
   *
   * @return A stream of datums; note that this is lazily loaded, and may be null near the end (e.g., for parallel computations).
   *
   * @throws IOException Throw by the underlying read mechanisms.
   */
  private static Stream<EntailmentPair> readDataset(File source, File cache, int size) throws IOException {
    // Read size
    if (size < 0) {
      size = 0;
      forceTrack("Counting lines");
      BufferedReader scanner = IOUtils.readerFromFile(source);
      while (scanner.readLine() != null) {
        size += 1;
      }
      log("dataset has size " + size);
      endTrack("Counting lines");
    }

    // Create stream
    if (cache != null && cache.exists()) {
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


  /**
   * Compute the BLEU score between two sentences.
   *
   * @param n The BLEU-X value to compute. Default is BLEU-4.
   * @param premiseWord The words in the premise.
   * @param conclusionWord The words in the conclusion.
   *
   * @return The BLEU score between the premise and the conclusion.
   */
  private static double bleu(int n, List<String> premiseWord, List<String> conclusionWord) {
    BleuMeasurer bleuScorer = new BleuMeasurer(n);
    bleuScorer.addSentence(premiseWord.toArray(new String[premiseWord.size()]), conclusionWord.toArray(new String[conclusionWord.size()]));
    double bleu = bleuScorer.bleu();
    if (Double.isNaN(bleu)) {
      bleu = 0.0;
    }
    return bleu;
  }


  /**
   * Featurize a given entailment pair.
   *
   * @param ex The example to featurize.
   *
   * @return A Counter containing the real-valued features for this example.
   */
  private static Counter<String> featurize(EntailmentPair ex) {
    ClassicCounter<String> feats = new ClassicCounter<>();

    // Lemma overlap
    if (FEATURE_TEMPLATES.contains(FeatureTemplate.OVERLAP)) {
      int intersect = CollectionUtils.intersect(new HashSet<>(ex.premise.lemmas()), new HashSet<>(ex.conclusion.lemmas())).size();
      feats.incrementCount("lemma_overlap", intersect);
      feats.incrementCount("lemma_overlap_percent", ((double) intersect) / ((double) Math.min(ex.premise.length(), ex.conclusion.length())));
    }

    // Relevant POS intersection
    if (FEATURE_TEMPLATES.contains(FeatureTemplate.POS_OVERLAP)) {
      for (char pos : new HashSet<Character>() {{
        add('V');
        add('N');
        add('J');
        add('R');
      }}) {
        Set<String> premiseVerbs = new HashSet<>();
        for (int i = 0; i < ex.premise.length(); ++i) {
          if (ex.premise.posTag(i).charAt(0) == pos) {
            premiseVerbs.add(ex.premise.lemma(i));
          }
        }
        Set<String> conclusionVerbs = new HashSet<>();
        for (int i = 0; i < ex.conclusion.length(); ++i) {
          if (ex.conclusion.posTag(i).charAt(0) == pos) {
            premiseVerbs.add(ex.conclusion.lemma(i));
          }
        }
        Set<String> intersectVerbs = CollectionUtils.intersect(premiseVerbs, conclusionVerbs);
        feats.incrementCount("" + pos + "_overlap_percent", ((double) intersectVerbs.size() / ((double) Math.min(ex.premise.length(), ex.conclusion.length()))));
        feats.incrementCount("" + pos + "_overlap: " + intersectVerbs.size());
      }
    }

    if (FEATURE_TEMPLATES.contains(FeatureTemplate.KEYWORD_OVERLAP)) {
      Set<KeywordPair> alignment = align(ex);
      long alignedCount = alignment.stream().filter(KeywordPair::isAligned).count();
      long alignedPerfectCount = alignment.stream().filter(x -> x.isAligned() && x.premiseChunk().equalsIgnoreCase(x.conclusionChunk())).count();
      double totalCount = alignment.size();
      feats.incrementCount("keyword_align_count:" + alignedCount);
      feats.incrementCount("keyword_align_percent:" + (((double) alignedCount) / totalCount));
      feats.incrementCount("keyword_perfect_align_count:" + alignedPerfectCount);
      feats.incrementCount("keyword_perfect_align_percent:" + (((double) alignedPerfectCount) / totalCount));
    }

    // BLEU score
    if (FEATURE_TEMPLATES.contains(FeatureTemplate.BLEU)) {
      feats.incrementCount("BLEU-1", bleu(1, ex.premise.lemmas(), ex.premise.lemmas()));
      feats.incrementCount("BLEU-2", bleu(2, ex.premise.lemmas(), ex.premise.lemmas()));
      feats.incrementCount("BLEU-3", bleu(3, ex.premise.lemmas(), ex.premise.lemmas()));
      feats.incrementCount("BLEU-4", bleu(4, ex.premise.lemmas(), ex.premise.lemmas()));
    }

    // Length differences
    if (FEATURE_TEMPLATES.contains(FeatureTemplate.LENGTH_DIFF)) {
      feats.incrementCount("length_diff:" + (ex.conclusion.length() - ex.premise.length()));
      feats.incrementCount("conclusion_longer?:" + (ex.conclusion.length() > ex.premise.length()));
      feats.incrementCount("conclusion_length:" + ex.conclusion.length());
    }

    // Unigram entailments
    if (!FEATURES_NOLEX && FEATURE_TEMPLATES.contains(FeatureTemplate.ENTAIL_UNIGRAM)) {
      for (int pI = 0; pI < ex.premise.length(); ++pI) {
        for (int cI = 0; cI < ex.conclusion.length(); ++cI) {
          if (ex.premise.posTag(pI).charAt(0) == ex.conclusion.posTag(cI).charAt(0)) {
            feats.incrementCount("lemma_entail:" + ex.premise.lemma(pI) + "_->_" + ex.conclusion.lemma(cI));
          }
        }
      }
    }

    // Bigram entailments
    if (!FEATURES_NOLEX && FEATURE_TEMPLATES.contains(FeatureTemplate.ENTAIL_BIGRAM)) {
      for (int pI = 0; pI <= ex.premise.length(); ++pI) {
        String lastPremise = (pI == 0 ? "^" : ex.premise.lemma(pI - 1));
        String premise = (pI == ex.premise.length() ? "$" : ex.premise.lemma(pI));
        for (int cI = 0; cI <= ex.conclusion.length(); ++cI) {
          String lastConclusion = (cI == 0 ? "^" : ex.conclusion.lemma(cI - 1));
          String conclusion = (cI == ex.conclusion.length() ? "$" : ex.conclusion.lemma(cI));
          if ((pI == ex.premise.length() && cI == ex.conclusion.length()) ||
              (pI < ex.premise.length() && cI < ex.conclusion.length() &&
                  ex.premise.posTag(pI).charAt(0) == ex.conclusion.posTag(cI).charAt(0))) {
            feats.incrementCount("lemma_entail:" + lastPremise + "_" + premise + "_->_" + lastConclusion + "_" + conclusion);
          }
        }
      }
    }

    // Keyword entailments
    if (!FEATURES_NOLEX && FEATURE_TEMPLATES.contains(FeatureTemplate.ENTAIL_KEYWORD)) {
      List<Span> premiseKeywords = ex.premise.algorithms().keyphraseSpans();
      List<Span> conclusionKeywords = ex.conclusion.algorithms().keyphraseSpans();
      for (Span p : premiseKeywords) {
        String premisePOS = ex.premise.algorithms().modeInSpan(p, Sentence::posTags);
        String premisePhrase = StringUtils.join(ex.premise.lemmas().subList(p.start(), p.end()), " ").toLowerCase();
        for (Span c : conclusionKeywords) {
          String conclusionPOS = ex.conclusion.algorithms().modeInSpan(c, Sentence::posTags);
          if (premisePOS.charAt(0) == conclusionPOS.charAt(0)) {
            String conclusionPhrase = StringUtils.join(ex.conclusion.lemmas().subList(c.start(), c.end()), " ").toLowerCase();
            feats.incrementCount("keyphrase_entail:" + premisePhrase + "_->_" + conclusionPhrase);
          }
        }
      }
    }

    // Consequent uni/bi-gram
    if (!FEATURES_NOLEX && FEATURE_TEMPLATES.contains(FeatureTemplate.CONCLUSION_NGRAM)) {
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


  /**
   * Featurize an entire dataset, caching the intermediate annotations in the process.
   *
   * @param data The dataset to featurize, as a (potentially lazy, necessarily parallel) stream.
   * @param cacheStream The cache stream to write the processed sentences to.
   *
   * @return A dataset with the featurized data.
   *
   * @throws IOException Thrown from the underlying write method to the cache.
   */
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
          if (cacheStream != null) {
            ex.serialize(cacheStream);
          }
        }
      }
    });
    if (cacheStream != null) {
      cacheStream.close();
    }
    return dataset;
  }


  /**
   * The entry point of the code.
   *
   * @param args The command line arguments, as filled by {@link edu.stanford.nlp.util.Execution}.
   *
   * @throws IOException
   * @throws ClassNotFoundException
   */
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    // Initialize the parameters
    Execution.fillOptions(TrainEntailment.class, args);
    RedwoodConfiguration.apply(StringUtils.argsToProperties(args));
    forceTrack("main");
    if (TRAIN_CACHE == null && TRAIN_CACHE_DO) {
      TRAIN_CACHE = new File(TRAIN_FILE.getPath() + (TRAIN_COUNT < 0 ? "" : ("." + TRAIN_COUNT)) + ".cache");
    }
    if (TEST_CACHE == null && TEST_CACHE_DO) {
      TEST_CACHE = new File(TEST_FILE.getPath() + ".cache");
    }
    File tmp = File.createTempFile("cache", ".ser.gz");
    tmp.deleteOnExit();

    // Read the test data
    forceTrack("Reading test data");
    Stream<EntailmentPair> testData = readDataset(TEST_FILE, TEST_CACHE, -1);
    GeneralDataset<Trilean, String> testDataset = featurize(testData, TEST_CACHE == null ? null : new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))));
    IOUtils.cp(tmp, TEST_CACHE);
    endTrack("Reading test data");

    // Train a model
    LinearClassifier<Trilean, String> classifier;
    if (RETRAIN || MODEL == null || !MODEL.exists()) {
      // Create the datasets
      forceTrack("Reading training data");
      Stream<EntailmentPair> trainData = readDataset(TRAIN_FILE, TRAIN_CACHE, TRAIN_COUNT);
      GeneralDataset<Trilean, String> trainDataset = featurize(trainData, TRAIN_CACHE == null ? null : new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))));
      IOUtils.cp(tmp, TRAIN_CACHE);
      endTrack("Reading training data");

      // Training
      // (create factory)
      forceTrack("Training the classifier");
      startTrack("creating factory");
      LinearClassifierFactory<Trilean, String> factory = new LinearClassifierFactory<>();
      factory.setSigma(TRAIN_SIGMA);
      log("sigma: " + TRAIN_SIGMA);
      trainDataset.applyFeatureCountThreshold(TRAIN_FEATURE_COUNT_THRESHOLD);
      log("feature threshold: " + TRAIN_FEATURE_COUNT_THRESHOLD);
      endTrack("creating factory");
      // (train classifier)
      log("training classifier:");
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
    log("");
    DecimalFormat df = new DecimalFormat("0.00%");
    double accuracy = classifier.evaluateAccuracy(testDataset);
    log("Accuracy: " + df.format(accuracy));
    log("");
    Pair<Double, Double> pr = classifier.evaluatePrecisionAndRecall(testDataset, Trilean.TRUE);
    log("P:        " + df.format(pr.first));
    log("R:        " + df.format(pr.second));
    log("F1:       " + df.format(2.0 * pr.first * pr.second / (pr.first + pr.second)));
    log("");
    endTrack("Evaluating the classifier");

    endTrack("main");
  }
}



