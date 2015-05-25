package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.annotation.Annotator;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

/**
 * Make all *ing words verbs. I'm sick of approximate matches on these.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unchecked")
public class VerbIngTranslator extends SentenceAnnotator {

  public VerbIngTranslator(String name, Properties props) {

  }

  @Override
  protected int nThreads() {
    return -1;
  }

  @Override
  protected long maxTime() {
    return -1;
  }

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
      if (token.word().toLowerCase().endsWith("ing")) {
        token.setTag("VBG");
      }
    }
  }

  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    // do nothing
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.EMPTY_SET;
  }

  @Override
  public Set<Requirement> requires() {
    return Collections.EMPTY_SET;
  }
}
