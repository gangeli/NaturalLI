package edu.stanford.nlp.natlog;

import edu.stanford.nlp.util.StringUtils;
import junit.framework.TestCase;
import org.goobs.truth.Quantifier;

import java.util.Random;

/**
 * A test for monotonicity markings
 *
 * @author Gabor Angeli
 */
public class GaborMonoTest extends TestCase {

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

  public static String quantifiedSentence(int depth, Random r) {
    if (depth == 0) {
      return StringUtils.join(new String[]{noun(r), verb(r), adj(r), noun(r)});
    } else if (depth == 1) {
      return StringUtils.join(new String[]{quantifier(r), noun(r), verb(r), adj(r), noun(r)});
    }
    return null;
  }
}
