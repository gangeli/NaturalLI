package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * Run a multiple-choice answering system on a test file formatted like the input file to
 * {@link ClassifierTrainer}. That is, the IR step should already have
 * been done elsewhere (see, e.g., in etc/aristo).
 *
 * This is similar to the run_ir_baseline.py interface.
 *
 * @author Gabor Angeli
 */
public class MultipleChoiceSolver {
  @Execution.Option(name="model", gloss="The file to load/save the model to/from.")
  public static File MODEL = new File("logs/last_suite/0/model.ser.gz");

  @Execution.Option(name="data", gloss="The file to evaluate on")
  public static File DATA_FILE = new File("etc/aristo/eval_test_barrons.tab");

  private static final DecimalFormat percent = new DecimalFormat("0.000%");


  private static class MultipleChoiceQuestion {
    public int answer = -1;
    public List<String> hypotheses = new ArrayList<>();
    public List<List<String>> premises = new ArrayList<>();
    public List<String> focuses = new ArrayList<>();
    public List<List<Double>> luceneScores = new ArrayList<>();

    public MultipleChoiceQuestion() {
    }

    public void addRow(boolean truth, String premise, String hypothesis, String focus, double luceneScore) {
      int i = this.hypotheses.indexOf(hypothesis);
      if (i < 0) {
        i = this.hypotheses.size();
        this.hypotheses.add(hypothesis);
        this.focuses.add(focus);
        this.premises.add(new ArrayList<>());
        this.luceneScores.add(new ArrayList<>());
      }
      this.premises.get(i).add(premise);
      this.luceneScores.get(i).add(luceneScore);
      if (truth) {
        if (answer >= 0 && answer != i) {
          throw new IllegalArgumentException("Multiple answers for question!");
        }
        answer = i;
      }
    }

    public int size() {
      return hypotheses.size();
    }
  }

  public static class TestState {
    public int numRandomGuesses = 0;
    public int numAmbiguousGuesses = 0;
    public int numLuckyGuesses = 0;
  }

  private static List<MultipleChoiceQuestion> readDataset(File dataFile) throws IOException {
    List<MultipleChoiceQuestion> examples = new ArrayList<>();
    MultipleChoiceQuestion currentQuestion = null;
    int lastId = -1;

    BufferedReader reader = IOUtils.readerFromFile(dataFile);
    String line;
    while ( (line = reader.readLine()) != null) {
      String[] fields = line.split("\t");
      int id = Integer.parseInt(fields[0]);
      if (id != lastId) {
        if (lastId >= 0) {
          examples.add(currentQuestion);
        }
        currentQuestion = new MultipleChoiceQuestion();
      }
      if (currentQuestion == null) { throw new IllegalStateException("You f***ed up logic."); }
      currentQuestion.addRow(Boolean.parseBoolean(fields[1].toLowerCase()), fields[2], fields[3], fields[4], Double.parseDouble(fields[5]));
      lastId = id;
    }

    if (currentQuestion != null && currentQuestion.hypotheses.size() > 0) {
      examples.add(currentQuestion);
    }

    return examples;
  }


  private static int predict(EntailmentClassifier classifier, MultipleChoiceQuestion question, Optional<TestState> testState) {
    startTrack(question.hypotheses.get(question.answer));
    int argmax = -1;
    double max = Double.NEGATIVE_INFINITY;
    double min = Double.POSITIVE_INFINITY;
    List<Double> scores = new ArrayList<>();
    for (int qI = 0; qI < question.size(); ++qI) {
      Pair<Sentence, Double> support = classifier.bestScore(
          question.premises.get(qI),
          question.hypotheses.get(qI),
          Optional.of(question.focuses.get(qI)),
          Optional.of(question.luceneScores.get(qI))
      );
      double score = support.second;
      scores.add(score);

      log(new DecimalFormat("0.0000").format(score) + ": " + question.hypotheses.get(qI) + "  (because '" + support.first + ")");

      if (score > max) {
        max = score;
        argmax = qI;
      }
      if (score < min) {
        min = score;
      }
    }

    // Error check scores
    if (argmax == -1) {
      throw new IllegalStateException("No predictions for question: " + question);
    }
    if (testState.isPresent()) {
      final double maxScore = max;
      if (scores.stream().filter(x -> Math.abs(x - maxScore) < 1e-5).count() > 1) {
        if (question.answer == argmax) {
          testState.get().numLuckyGuesses += 1;
        }
        testState.get().numAmbiguousGuesses += 1;
      }
      if (Math.abs(max - min) < 1e-10) {
        err("All hypotheses have the same score!");
        testState.get().numRandomGuesses += 1;
      }
    }

    endTrack(question.hypotheses.get(question.answer));
    return argmax;
  }


  private static double accuracy(EntailmentClassifier classifier, Collection<MultipleChoiceQuestion> questions) {
    int correct = 0;
    TestState state = new TestState();
    List<MultipleChoiceQuestion> missed = new ArrayList<>();
    for (MultipleChoiceQuestion question : questions) {
      if (predict(classifier, question, Optional.of(state)) == question.answer) {
        correct += 1;
      } else {
        missed.add(question);
      }
    }

    startTrack("Missed questions");
    for (MultipleChoiceQuestion q : missed) {
      log(q.hypotheses.get(q.answer));
    }
    endTrack("Missed questions");
    log("Random guesses: " + state.numRandomGuesses + "  (" + percent.format(((double) state.numRandomGuesses) / ((double) questions.size())) + ")");
    log("Guesses:        " + state.numAmbiguousGuesses + "  (" + percent.format(((double) state.numAmbiguousGuesses) / ((double) questions.size())) + ")");
    log("Lucky Guesses:  " + state.numLuckyGuesses + "  (" + percent.format(((double) state.numLuckyGuesses) / ((double) questions.size())) + ")");

    return ((double) correct) / ((double) questions.size());
  }


  public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    RedwoodConfiguration.current().capture(System.err).apply();
    RedwoodConfiguration.apply(StringUtils.argsToProperties(args));
    Execution.fillOptions(new Class[]{NaturalLIClassifier.class, MultipleChoiceSolver.class}, args);
    EntailmentClassifier classifier = EntailmentClassifier.load(MODEL);
    List<MultipleChoiceQuestion> dataset = readDataset(DATA_FILE);



    double accuracy = accuracy(classifier, dataset);
    log("Accuracy: " + percent.format(accuracy));

    /*
    forceTrack("Running evaluation");
    List<Double> weights = new ArrayList<>();
    List<Double> accuracies = new ArrayList<>();
    for (double i = 0.00; i < 10.0; i += 0.05) {
      NaturalLIClassifier.ALIGNMENT_WEIGHT = i;
      double accuracy = accuracy(classifier, dataset);
      log("Accuracy: " + percent.format(accuracy));
      weights.add(i);
      accuracies.add(accuracy);
    }
    endTrack("Running evaluation");

    DecimalFormat df = new DecimalFormat("0.0000");
    double max = Double.NEGATIVE_INFINITY;
    double argmax = Double.NaN;
    for (int i = 0; i < weights.size(); ++i) {
      log(df.format(weights.get(i)) + "\t" + percent.format(accuracies.get(i)));
      if (accuracies.get(i) > max) {
        max = accuracies.get(i);
        argmax = weights.get(i);
      }
    }
    log("Best alignment @ " + argmax + " with score " + max);
    */


  }
}
