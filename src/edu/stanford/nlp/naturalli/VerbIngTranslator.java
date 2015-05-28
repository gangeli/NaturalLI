package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Make all *ing words verbs (unless they're clearly adjectives).
 * I'm sick of approximate matches on these.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings({"unchecked", "UnusedParameters", "UnusedDeclaration"})
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
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (int i = 0; i < tokens.size(); ++i) {
      if (tokens.get(i).word().endsWith("ing")) {
        if (i == tokens.size() - 1 || !tokens.get(i + 1).tag().startsWith("N")) {
          tokens.get(i).setTag("VBG");
        }
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
