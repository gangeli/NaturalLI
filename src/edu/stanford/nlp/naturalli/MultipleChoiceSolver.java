package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.log;

/**
 * Run a multiple-choice answering system on a test file formatted like the input file to
 * {@link edu.stanford.nlp.naturalli.TrainEntailment}. That is, the IR step should already have
 * been done elsewhere (see, e.g., in etc/aristo).
 *
 * This is similar to the run_ir_baseline.py interface.
 *
 * @author Gabor Angeli
 */
public class MultipleChoiceSolver {
  @Execution.Option(name="model", gloss="The file to load/save the model to/from.")
  public static File MODEL = new File("tmp/model.ser.gz");

  @Execution.Option(name="data", gloss="The file to evaluate on")
  public static File DATA_FILE = new File("tmp/aristo_all.tab");


  private static class MultipleChoiceQuestion {
    public int answer = -1;
    public List<String> hypotheses = new ArrayList<>();
    public List<List<String>> premises = new ArrayList<>();

    public MultipleChoiceQuestion() {
    }

    public void addRow(boolean truth, String premise, String hypothesis) {
      int i = this.hypotheses.indexOf(hypothesis);
      if (i < 0) {
        i = this.hypotheses.size();
        this.hypotheses.add(hypothesis);
        this.premises.add(new ArrayList<>());
      }
      this.premises.get(i).add(premise);
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
      if (currentQuestion == null) { throw new IllegalStateException("You f***ed up logic. Fix it."); }
      currentQuestion.addRow(Boolean.parseBoolean(fields[1].toLowerCase()), fields[2], fields[3]);
      lastId = id;
    }

    if (currentQuestion != null && currentQuestion.hypotheses.size() > 0) {
      examples.add(currentQuestion);
    }

    return examples;
  }


  private static int predict(EntailmentClassifier classifier, MultipleChoiceQuestion question) {
    int argmax = -1;
    double max = Double.NEGATIVE_INFINITY;
    for (int qI = 0; qI < question.size(); ++qI) {
//      double score = classifier.bestScore(question.premises.get(qI), question.hypotheses.get(qI));
      double numPremises = (double) question.premises.get(qI).size();
      double score = classifier.weightedAverageScore(
          question.premises.get(qI),
          question.hypotheses.get(qI),
          i -> Math.exp(-5.0 * i / numPremises)
          );
      if (score > max) {
        max = score;
        argmax = qI;
      }
    }
    if (argmax == -1) {
      throw new IllegalStateException("No predictions for question: " + question);
    }
    return argmax;
  }


  private static double accuracy(EntailmentClassifier classifier, Collection<MultipleChoiceQuestion> questions) {
    int correct = 0;
    for (MultipleChoiceQuestion question : questions) {
      if (predict(classifier, question) == question.answer) {
        correct += 1;
      }
    }

    return ((double) correct) / ((double) questions.size());
  }


  public static void main(String[] args) throws IOException, ClassNotFoundException {
    EntailmentClassifier classifier = new EntailmentClassifier(MODEL);
    List<MultipleChoiceQuestion> dataset = readDataset(DATA_FILE);
    log("Accuracy: " + new DecimalFormat("0.00%").format(accuracy(classifier, dataset)));
  }
}
