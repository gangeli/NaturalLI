package edu.stanford.nlp.natlog;

import edu.stanford.nlp.util.StringUtils;
import org.goobs.truth.Quantifier;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A test for monotonicity markings
 *
 * @author Gabor Angeli
 */
public class GaborMonoTest {
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
        case '-':
          expected.add(Monotonicity.NON);
          break;
      }
    }

    // Annotate sentence
    Monotonicity[] mono = GaborMono.getInstance().annotate(sentence.toString());

    // Checks
    assertEquals(spec + " [" + StringUtils.join(expected, " ") + "] versus [" + StringUtils.join(mono, " ") + "]",
        expected.size(), mono.length);
    for (int i = 0; i < Math.min(expected.size(), mono.length); ++i) {
      assertEquals("Disagreed on '" + spec.split("\\s+")[i] + "' in '" + spec + "'", expected.get(i), mono[i]);
    }
  }

  @Test
  public void singleBinaryQuantifierBulk() {
    validate("all^ catsv have^ tails^");

    for (Quantifier q : Quantifier.values()) {
      if (q.trigger == Quantifier.TriggerType.DONT_MARK || !q.isBinary()) { continue; }
      String sm;
      String om;
      switch (q.closestMeaning) {
        case FORALL:
          sm = "v"; om = "^";
          break;
        case EXISTS:
          sm = "^"; om = "^";
          break;
        case FEW:
          sm = "v"; om = "^";
          break;
        case MOST:
          sm = "-"; om = "^";
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

  @Test
  public void singleBinaryQuantifierExceptions() {
    validate("at^ most^ fivev catsv havev tailsv");
  }

  @Test
  public void singleUnaryQuantifierBulk() {
    validate("some^ cats^ do^ not^ havev tailsv");
    validate("some^ cats^ do^ n't^ havev tailsv");

    for (Quantifier q : Quantifier.values()) {
      if (q.trigger == Quantifier.TriggerType.DONT_MARK || q.isBinary()) { continue; }
      String m;
      switch (q.closestMeaning) {
        case FORALL:
          m = "v";
          break;
        case EXISTS:
          m = "^";
          break;
        case MOST:
          m = "*";
          break;
        case FEW:
          m = "v";
          break;
        case NONE:
          m = "v";
          break;
        default:
          throw new IllegalArgumentException();
      }
      validate("some^ cats^ do^ " + StringUtils.join(q.surfaceForm, "^ ") + "^ have" + m + " tails" + m);
      validate("some^ fluffy^ cats^ do^ " + StringUtils.join(q.surfaceForm, "^ ") + "^ have" + m + " furry" + m + " tails" + m);
    }
  }

  @Test
  public void multipleQuantifiers() {
    validate ("some^ cats^ have^ n't^ anyv tailsv");   // -> some cats haven't any {fuzzy tails}
    validate ("all^ catsv do^ n't^ havev tailsv");
    validate ("no^ catsv havev nov tail^");
  }

  @Test
  public void regressions() {
    // Special-case "few" but not "a few"
    validate ("few^ catsv are^ dogs^");
    validate ("a^ few^ cats^ have^ tails^");
    validate ("some^ cats^ have^ few^ tailsv");
    validate ("some^ cats^ have^ a^ few^ tails^");
  }

  @Test
  public void defaultMonotonicity() {
    validate("the^ seven^ blue^ cats^ all^ have^ tails^");
    validate ("cats- have^ tails^");
    validate ("cats- do^ n't^ havev tailsv");

  }

}
