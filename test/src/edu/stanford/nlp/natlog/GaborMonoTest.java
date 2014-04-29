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
import java.util.Random;

import static org.junit.Assert.*;

/**
 * A test for monotonicity markings
 *
 * @author Gabor Angeli
 */
public class GaborMonoTest {

  public static final String[] nouns = new String[]{ "cat", "dog", "animal", "tail", "fur", "legs" };
  public static final String[] adjectives = new String[]{ "furry", "large", "small", "cute" };
  public static final String[] verbs = new String[]{ "has", "plays with", "is covered in", "is" };

  private static String noun(Random rand) {
    return nouns[rand.nextInt(nouns.length)];
  }
  private static String adj(Random rand) {
    return adjectives[rand.nextInt(adjectives.length)];
  }
  private static String verb(Random rand) {
    return verbs[rand.nextInt(verbs.length)];
  }
  private static String quantifier(Random rand) {
    return StringUtils.join(Quantifier.values()[rand.nextInt(Quantifier.values().length)].surfaceForm, " ");
  }

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
      assertEquals(expected.get(i), mono[i]);
    }
  }

  @Test
  public void noQuantifier() {
    validate("cats^ have^ tails^");
    validate("the^ seven^ blue^ cats^ all^ have^ tails^");
  }

}
