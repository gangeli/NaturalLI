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
import java.util.function.Function;

/**
 * An interface for classifying whether a sentence entails another sentence.
 *
 * @author Gabor Angeli
 */
public interface EntailmentClassifier {

  public Trilean classify(Sentence premise, Sentence hypothesis);

  public double truthScore(Sentence premise, Sentence hypothesis);

  public Object serialize();


  public default Trilean classify(String premise, String hypothesis) {
    return classify(new Sentence(premise), new Sentence(hypothesis));
  }

  public default double truthScore(String premise, String hypothesis) {
    return truthScore(new Sentence(premise), new Sentence(hypothesis));
  }

  public default double bestScore(List<String> premises, String hypothesis) {
    double max = Double.NEGATIVE_INFINITY;
    Sentence hypothesisSentence = new Sentence(hypothesis);
    for (int i = 0; i < premises.size(); ++i) {
      double score = truthScore(new Sentence(premises.get(i)), hypothesisSentence);
      if (score > max) {
        max = score;
      }
    }
    return max;
  }

  public default int bestCandidate(String premise, List<String> candidates) {
    int argmax = -1;
    double max = Double.NEGATIVE_INFINITY;
    Sentence premiseSentence = new Sentence(premise);
    for (int i = 0; i < candidates.size(); ++i) {
      double score = truthScore(premiseSentence, new Sentence(candidates.get(i)));
      if (score > max) {
        max = score;
        argmax = i;
      }
    }
    return argmax;
  }


  public default double weightedAverageScore(List<String> premises, String hypothesis, Function<Integer, Double> decay) {
    double sum = 0.0;
    Sentence hypothesisSentence = new Sentence(hypothesis);
    for (int i = 0; i < premises.size(); ++i) {
      double score = truthScore(new Sentence(premises.get(i)), hypothesisSentence);
      sum += decay.apply(i) * score;
    }
    return sum / ((double) premises.size());
  }



  public default void save(File file) throws IOException {
    IOUtils.writeObjectToFile(Pair.makePair(this.getClass(), serialize()), file);
  }

  public static EntailmentClassifier load(File file) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Pair<Class<? extends EntailmentClassifier>, Object> spec = IOUtils.readObjectFromFile(file);
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
