package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.Trilean;

/**
 * A distantly supervised entailment classifier.
 * At least one of the shortenings of the premise should entail the conclusion.
 *
 * @author Gabor Angeli
 *
 * TODO(gabor) implement me
 */
public class DistantEntailmentClassifier implements EntailmentClassifier {
  @Override
  public Trilean classify(Sentence premise, Sentence hypothesis) {
    return null;
  }

  @Override
  public double truthScore(Sentence premise, Sentence hypothesis) {
    return 0;
  }

  @Override
  public Object serialize() {
    return null;
  }

  public static void train(ClassifierTrainer trainer) {
  }
}
