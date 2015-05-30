package edu.stanford.nlp.naturalli.entail;

import com.google.gson.Gson;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.naturalli.ProcessPremise;
import edu.stanford.nlp.naturalli.ProcessQuery;
import edu.stanford.nlp.naturalli.QRewrite;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static edu.stanford.nlp.util.logging.Redwood.Util.err;
import static edu.stanford.nlp.util.logging.Redwood.Util.log;

/**
 * TODO(gabor) JavaDoc
 *
 * @author Gabor Angeli
 */
public class FeaturesFromNaturalLI {
  private static final Lazy<StanfordCoreNLP> pipeline = Lazy.of(() -> {
    Properties props = new Properties() {{
      setProperty("annotators", "tokenize,ssplit,pos,ing,lemma,depparse,natlog,qrewrite");
      setProperty("customAnnotatorClass.ing","edu.stanford.nlp.naturalli.VerbIngTranslator");
      setProperty("customAnnotatorClass.qrewrite","edu.stanford.nlp.naturalli.ProcessPremise$QRewriteAnnotator");
      setProperty("depparse.extradependencies", "ref_only_collapsed");
      setProperty("tokenize.class", "PTBTokenizer");
      setProperty("tokenize.language", "en");
      setProperty("naturalli.doPolarity", "false");
    }};
    return new StanfordCoreNLP(props, false);
  });

  private static final File cache = new File("logs/naturalli_alignments.cache");
//  private static final File cache = new File("tmp/foo");

  private static class Features {
    public double count_unalignable_premise;
    public double count_unalignable_conclusion;
    public double count_aligned;
    public double count_alignable;
    public double count_inexact;
    public double bias;
  }

  private static class Datum {
    public boolean truth;
    public String premise;
    public String conclusion;
    public Features features;
  }

  /** Convert a plain-text string to a CoNLL parse tree that can be fed into NaturalLI */
  private static String toParseTree(String text) {
    Pointer<String> debug = new Pointer<>();
    try {
      return ProcessQuery.annotate(QRewrite.FOR_PREMISE, text, pipeline.get(), debug, true);
    } catch (AssertionError e) {
      err("Assertion error when processing sentence: " + text);
      return "cats\t0\troot\t0";
    }
  }


  private static void trainClassifier(BufferedReader reader) throws IOException {
    String line;
    Gson gson = new Gson();
    RVFDataset<Boolean, String> dataset = new RVFDataset<>();
    while ( (line = reader.readLine()) != null) {
      Datum d = gson.fromJson(line, Datum.class);
      RVFDatum<Boolean, String> datum = new RVFDatum<>(new ClassicCounter<String>(){{
        setCount(NaturalLIClassifier.COUNT_UNALIGNABLE_PREMISE, d.features.count_unalignable_premise);
        setCount(NaturalLIClassifier.COUNT_UNALIGNABLE_CONCLUSION, d.features.count_unalignable_conclusion);
        setCount(NaturalLIClassifier.COUNT_ALIGNABLE, d.features.count_alignable);
        setCount(NaturalLIClassifier.COUNT_ALIGNED, d.features.count_aligned);
        setCount(NaturalLIClassifier.COUNT_INEXACT, d.features.count_inexact);
        setCount("bias", d.features.bias);
      }},
      d.truth);
      dataset.add(datum);
    }

    LinearClassifierFactory factory = new LinearClassifierFactory();
    LinearClassifier classifier = factory.trainClassifier(dataset);
    Map<Boolean, Counter<String>> weights = ((LinearClassifier<Boolean, String>) classifier).weightsAsMapOfCounters();
    DecimalFormat df = new DecimalFormat("0.000000000");
    for (Pair<String, Double> entry : Counters.toSortedListWithCounts(weights.get(true)).subList(0, Math.min(100, weights.get(true).size()))) {
      log(((entry.second < 0) ? "" : " ") + df.format(entry.second) + "    " + entry.first);
    }
    log("Majority: " + df.format(Counters.max(dataset.numDatumsPerLabel()) / dataset.size()));
    log("Training accuracy: " + classifier.evaluateAccuracy(dataset));
  }

  private static Pair<BufferedReader, OutputStreamWriter> mkNaturalli(Writer errWriter) throws IOException {
    ProcessBuilder searcherBuilder = new ProcessBuilder("src/naturalli_search");  // TODO awful
    final Process searcher = searcherBuilder.start();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        searcher.destroy();
      }

    });
    // Gobble naturalli.stderr to real stderr
    StreamGobbler errGobbler = new StreamGobbler(searcher.getErrorStream(), errWriter);
    errGobbler.start();

    BufferedReader fromNaturalLI = new BufferedReader(new InputStreamReader(searcher.getInputStream()));
    OutputStreamWriter toNaturalLI = new OutputStreamWriter(new BufferedOutputStream(searcher.getOutputStream()));
    return Pair.makePair(fromNaturalLI, toNaturalLI);
  }

  public static void main(String[] args) throws IOException {

    if (cache.exists()) {
      trainClassifier(new BufferedReader(new FileReader(cache)));
      return;
    }



    EntailmentFeaturizer featurizer = new EntailmentFeaturizer(args);
    ClassifierTrainer trainer = new ClassifierTrainer(featurizer);
    Execution.fillOptions(new Object[]{trainer, featurizer}, args);


    final Writer errWriter = new OutputStreamWriter(System.err);
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


    Pair<BufferedReader, OutputStreamWriter> naturalli = mkNaturalli(errWriter);
    Pointer<BufferedReader> fromNaturalli = new Pointer(naturalli.first);
    Pointer<OutputStreamWriter> toNaturalli = new Pointer(naturalli.second);

    Stream<EntailmentPair> trainData = trainer.readFlatDataset(trainer.TRAIN_FILE, trainer.TRAIN_CACHE, trainer.TRAIN_COUNT);
    Gson gson = new Gson();
    FileWriter w = new FileWriter(cache);
    AtomicInteger i = new AtomicInteger(0);
    trainData.forEach(pair -> {
      if (pair.premise.words().size() < 40 && pair.conclusion.words().size() < 40) {

      try {
        toNaturalli.dereference().get().write(toParseTree(pair.premise.text()));
        toNaturalli.dereference().get().write("\n");
        toNaturalli.dereference().get().write(toParseTree(pair.conclusion.text()));
        toNaturalli.dereference().get().write("\n");
        toNaturalli.dereference().get().flush();
        String json = fromNaturalli.dereference().get().readLine();
        Features feats = gson.fromJson(json, Features.class);
        Datum d = new Datum();
        d.truth = pair.truth.toBoolean(false);
        d.premise = pair.premise.text();
        d.conclusion = pair.conclusion.text();
        d.features = feats;
        w.write(gson.toJson(d));
        w.write("\n");
        w.flush();
        if (i.incrementAndGet() % 100 == 0) {
          log("Processed " + i.get() + " examples");
        }
      } catch (Throwable e) {
        e.printStackTrace();
        if (e instanceof IOException) {
          try {
            err("Caught exception; re-creating NaturalLI:");
            Pair<BufferedReader, OutputStreamWriter> newNaturalli = mkNaturalli(errWriter);
            fromNaturalli.set(newNaturalli.first);
            toNaturalli.set(newNaturalli.second);
          } catch (IOException e1) {
            e1.printStackTrace();
          }
        }
      }
      }
    });
    w.close();

  }
}
