package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Trilean;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static edu.stanford.nlp.util.logging.Redwood.Util.log;

/**
 * An interface for classifying whether a sentence entails another sentence.
 *
 * @author Gabor Angeli
 */
public interface EntailmentClassifier {

  public Trilean classify(Sentence premise, Sentence hypothesis, Optional<String> focus, Optional<Double> luceneScore);

  public double truthScore(Sentence premise, Sentence hypothesis, Optional<String> focus, Optional<Double> luceneScore);

  public Object serialize();


  public default Trilean classify(String premise, String hypothesis, Optional<String> focus, Optional<Double> luceneScore) {
    return classify(new Sentence(premise), new Sentence(hypothesis), focus, luceneScore);
  }

  public default double truthScore(String premise, String hypothesis, Optional<String> focus, Optional<Double> luceneScore) {
    return truthScore(new Sentence(premise), new Sentence(hypothesis), focus, luceneScore);
  }

  public default Pair<Sentence, Double> bestScore(List<String> premises, String hypothesis,
                                                  Optional<String> focus, Optional<List<Double>> luceneScores,
                                                  Function<Integer, Double> decay) {
    double max = Double.NEGATIVE_INFINITY;
    Sentence argmax = null;
    Sentence hypothesisSentence = new Sentence(hypothesis);
    for (int i = 0; i < premises.size(); ++i) {
      Sentence premiseSentence = new Sentence(premises.get(i));
      // Score the entailment pair
      double score = truthScore(premiseSentence, hypothesisSentence, focus, luceneScores.isPresent() ? Optional.of(luceneScores.get().get(i)) : Optional.empty());
      // Discount the score if the focus is not present
      if (focus.isPresent() &&
          !focus.get().contains(" ") &&
          !premiseSentence.text().toLowerCase().replaceAll("\\s+", " ").contains(focus.get().toLowerCase().replaceAll("\\s+", " "))) {
        score *= 0.25;
      }
      score *= decay.apply(i);
      // Check score
      assert !Double.isNaN(score);
      assert Double.isFinite(score);
      // Incorporate it into the max calculation
      if (score > max) {
        max = score;
        argmax = premiseSentence;
      }
    }
    return Pair.makePair(argmax, max);
  }

  public default Pair<Sentence, Double> bestScore(List<String> premises, String hypothesis,
                                                  Optional<String> focus, Optional<List<Double>> luceneScores) {
    return bestScore(premises, hypothesis, focus, luceneScores, i -> 1.0);
  }

  public default Pair<Sentence, Double> aveScore(List<String> premises, String hypothesis,
                                                 Optional<String> focus, Optional<List<Double>> luceneScores,
                                                 Function<Integer, Double> decay) {
    double sum = 0.0;
    double max = Double.NEGATIVE_INFINITY;
    Sentence argmax = null;
    Sentence hypothesisSentence = new Sentence(hypothesis);
    for (int i = 0; i < premises.size(); ++i) {
      Sentence premiseSentence = new Sentence(premises.get(i));
      // Score the entailment pair
      double score = truthScore(premiseSentence, hypothesisSentence, focus, luceneScores.isPresent() ? Optional.of(luceneScores.get().get(i)) : Optional.empty());
      // Discount the score if the focus is not present
      if (focus.isPresent() &&
          !focus.get().contains(" ") &&
          !premiseSentence.text().toLowerCase().replaceAll("\\s+", " ").contains(focus.get().toLowerCase().replaceAll("\\s+", " "))) {
        score *= 0.25;
      }
      score *= decay.apply(i);
      // Check score
      assert !Double.isNaN(score);
      assert Double.isFinite(score);
      // Incorporate it into the max calculation
      if (score > max) {
        max = score;
        argmax = premiseSentence;
      }
      // Compute the sum
      sum += score;
    }
    return Pair.makePair(argmax, sum / ((double) premises.size()));
  }




  public default void save(File file) throws IOException {
    IOUtils.writeObjectToFile(Pair.makePair(this.getClass(), serialize()), file);
  }

  public static EntailmentClassifier load(File file) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Pair<Class<? extends EntailmentClassifier>, Object> spec = IOUtils.readObjectFromFile(file);
    log("loading a classifier of type " + spec.first.getSimpleName());
    Method loader = spec.first.getMethod("deserialize", Object.class);
    return (EntailmentClassifier) loader.invoke(null, spec.second);
  }


  public static EntailmentClassifier loadNoErrors(File file) {
    try {
      return load(file);
    } catch (IOException e) {
      throw new RuntimeIOException("Could not read model file", e);
    } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException("Class has no deserialize(Object) method, or it was not invokable", e);
    } catch (ClassCastException e) {
      throw new RuntimeException("Object could not be deserialized; wrong type", e);
    }
  }
}
