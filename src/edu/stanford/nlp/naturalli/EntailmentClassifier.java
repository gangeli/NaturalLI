package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.Trilean;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * An interface for classifying whether a sentence entails another sentence.
 * Trained from {@link edu.stanford.nlp.naturalli.TrainEntailment}.
 *
 * @author Gabor Angeli
 */
public class EntailmentClassifier {

  private final Classifier<Trilean, String> impl;

  public EntailmentClassifier(Classifier<Trilean, String> impl) {
    this.impl = impl;
  }

  @SuppressWarnings("unchecked")
  public EntailmentClassifier(File model) throws IOException, ClassNotFoundException {
    this((Classifier<Trilean, String>) IOUtils.readObjectFromFile(model));
  }


  public Trilean classify(Sentence premise, Sentence hypothesis) {
    return impl.classOf(new RVFDatum<>(TrainEntailment.featurize(new TrainEntailment.EntailmentPair(Trilean.UNKNOWN, premise, hypothesis))));
  }

  public Trilean classify(String premise, String hypothesis) {
    return classify(new Sentence(premise), new Sentence(hypothesis));
  }

  public double truthScore(Sentence premise, Sentence hypothesis) {
    return impl.scoresOf(new RVFDatum<>(TrainEntailment.featurize(new TrainEntailment.EntailmentPair(Trilean.UNKNOWN, premise, hypothesis)))).getCount(Trilean.TRUE);
  }

  public double truthScore(String premise, String hypothesis) {
    return truthScore(new Sentence(premise), new Sentence(hypothesis));
  }

  public int bestCandidate(String premise, List<String> candidates) {
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

  public double bestScore(List<String> premises, String hypothesis) {
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

  public double weightedAverageScore(List<String> premises, String hypothesis, Function<Integer, Double> decay) {
    double sum = 0.0;
    Sentence hypothesisSentence = new Sentence(hypothesis);
    for (int i = 0; i < premises.size(); ++i) {
      double score = truthScore(new Sentence(premises.get(i)), hypothesisSentence);
      sum += decay.apply(i) * score;
    }
    return sum / ((double) premises.size());
  }

}
