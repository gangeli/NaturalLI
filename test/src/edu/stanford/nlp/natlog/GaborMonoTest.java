package edu.stanford.nlp.natlog;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.StringUtils;
import org.goobs.truth.Quantifier;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * A test for monotonicity markings
 *
 * @author Gabor Angeli
 */
public class GaborMonoTest {
  private static final StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,parse");
  }});

  private static void validate(String spec) {
    // Construct sentence
    StringBuilder sentence = new StringBuilder();
    List<Monotonicity> expected = new ArrayList<>();
    for (String word : spec.split("\\s+")) {
      String gloss = word.substring(0, word.length() - 1);
      char mono = word.charAt(word.length() - 1);
      sentence.append(gloss).append(" ");
      switch (mono) {
        case '^':
          expected.add(Monotonicity.UP);
          break;
        case 'v':
          expected.add(Monotonicity.DOWN);
          break;
        case '*':
          expected.add(Monotonicity.NON);
          break;
      }
    }

    // Annotate sentence
    Annotation ann = new Annotation(sentence.toString());
    pipeline.annotate(ann);
    Monotonicity[] mono = GaborMono.getInstance().annotate(ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(TreeCoreAnnotations.TreeAnnotation.class));

    // Checks
    assertEquals(expected.size(), mono.length);
    for (int i = 0; i < Math.min(expected.size(), mono.length); ++i) {
      assertEquals("Disagreed on '" + spec.split("\\s+")[i] + "' in '" + spec + "'", expected.get(i), mono[i]);
    }
  }

  @Test
  public void noQuantifier() {
    validate("cats^ have^ tails^");
    validate("the^ seven^ blue^ cats^ all^ have^ tails^");
  }

  @Test
  public void singleQuantifier() {
    for (Quantifier q : Quantifier.values()) {
      String sm;
      String om;
      switch (q.closestMeaning) {
        case FORALL:
          sm = "v"; om = "^";
          break;
        case EXISTS:
          sm = "^"; om = "^";
          break;
        case MOST:
          sm = "*"; om = "^";
          break;
        case NONE:
          sm = "v"; om = "v";
          break;
        default:
          throw new IllegalArgumentException();
      }
      validate(StringUtils.join(q.surfaceForm, "^ ") + "^ cats" + sm + " have" + om + " tails" + om);
      validate(StringUtils.join(q.surfaceForm, "^ ") + "^ fluffy" + sm + " cats" + sm + " have" + om + " long" + om + " tails" + om);
    }
  }

}
