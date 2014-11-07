package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.StringUtils;
import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * A test for the {@link edu.stanford.nlp.naturalli.QuantifierScopeAnnotator}.
 *
 * @author Gabor Angeli
 */
public class QuantifierScopeITest {

  private static final StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,parse");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
  }});

  static {
    pipeline.addAnnotator(new QuantifierScopeAnnotator());
  }

  @SuppressWarnings("unchecked")
  private Optional<QuantifierSpec>[] annotate(String text) {
    Annotation ann = new Annotation(text);
    pipeline.annotate(ann);
    List<CoreLabel> tokens = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class);
    Optional<QuantifierSpec>[] scopes = new Optional[tokens.size()];
    Arrays.fill(scopes, Optional.empty());
    for (int i = 0; i < tokens.size(); ++i) {
      if (tokens.get(i).containsKey(QuantifierScopeAnnotator.QuantifierAnnotation.class)) {
        scopes[i] = Optional.of(tokens.get(i).get(QuantifierScopeAnnotator.QuantifierAnnotation.class));
      }
    }
    return scopes;
  }

  private void checkScope(int subjBegin, int subjEnd, int objBegin, int objEnd, Optional<QuantifierSpec> guess) {
    assertTrue("No quantifier found", guess.isPresent());
    assertEquals("Bad subject begin " + guess.get(), subjBegin, guess.get().subjectBegin);
    assertEquals("Bad subject end " + guess.get(), subjEnd, guess.get().subjectEnd);
    assertEquals("Bad object begin " + guess.get(), objBegin, guess.get().objectBegin);
    assertEquals("Bad object end " + guess.get(), objEnd, guess.get().objectEnd);
  }

  private void checkScope(int subjBegin, int subjEnd, Optional<QuantifierSpec> guess) {
    assertTrue("No quantifier found", guess.isPresent());
    assertEquals("Bad subject begin " + guess.get(), subjBegin, guess.get().subjectBegin);
    assertEquals("Bad subject end " + guess.get(), subjEnd, guess.get().subjectEnd);
    assertEquals("Two-place quantifier matched", subjEnd, guess.get().objectBegin);
    assertEquals("Two place quantifier matched", subjEnd, guess.get().objectEnd);
  }

  @SuppressWarnings({"UnusedDeclaration", "UnusedAssignment"})
  private void checkScope(String spec) {
    String[] terms = spec.split("\\s+");
    int quantStart = -1;
    int quantEnd = -1;
    int subjBegin = -1;
    int subjEnd = -1;
    int objBegin = -1;
    int objEnd = -1;
    boolean seenSubj = false;
    int tokenIndex = 0;
    List<String> cleanSentence = new ArrayList<>();
    for (String term : terms) {
      switch (term) {
        case "{":
          quantStart = tokenIndex;
          break;
        case "}":
          quantEnd = tokenIndex;
          break;
        case "[":
          if (!seenSubj) {
            subjBegin = tokenIndex;
          } else {
            objBegin = tokenIndex;
          }
          break;
        case "]":
          if (!seenSubj) {
            subjEnd = tokenIndex;
            seenSubj = true;
          } else {
            objEnd = tokenIndex;
          }
          break;
        default:
          cleanSentence.add(term);
          tokenIndex += 1;
          break;
      }
    }
    Optional<QuantifierSpec>[] scopes = annotate(StringUtils.join(cleanSentence, " "));
    System.err.println("Checking [@ " + (quantEnd - 1) + "]:  " + spec);
    if (objBegin >= 0 && objEnd >= 0) {
      checkScope(subjBegin, subjEnd, objBegin, scopes.length, scopes[quantEnd - 1]);
    } else {
      checkScope(subjBegin, subjEnd, scopes[quantEnd - 1]);
    }
  }

  @Test
  public void annotatorRuns() {
    annotate("All green cats have tails.");
  }

  @Test
  public void all_X_verb_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats eat mice.")[0]);
    checkScope(1, 2, 2, 4, annotate("All cats have tails.")[0]);
  }

  @Test
  public void all_X_want_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats want milk.")[0]);
  }

  @Test
  public void all_X_verb_prep_Y() {
    checkScope(1, 2, 2, 5, annotate("All cats are in boxes.")[0]);
    checkScope(1, 2, 2, 5, annotate("All cats voted for Roosevelt.")[0]);
    checkScope(1, 5, 5, 8, annotate("All cats who like dogs voted for Teddy.")[0]);
  }

  @Test
  public void all_X_be_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats are cute")[0]);
  }

  @Test
  public void all_X_can_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats can purr")[0]);
  }

  @Test
  public void all_X_relclause_verb_Y() {
    checkScope(1, 5, 5, 7, annotate("All cats who like dogs eat fish.")[0]);
  }

  @Test
  public void PER_predicate() {
    checkScope(0, 1, 1, 4, annotate("Felix likes cat food.")[0]);
  }

  @Test
  public void PER_has_predicate() {
    checkScope(0, 1, 1, 5, annotate("Felix has liked cat food.")[0]);
  }

  @Test
  public void PER_predicate_prep() {
    checkScope(0, 1, 1, 7, annotate("Jack paid the bank for 10 years")[0]);
  }

  @Test
  public void few_x_verb_y() {
    checkScope(1, 2, 2, 4, annotate("all cats chase dogs")[0]);
  }

  @Test
  public void a_few_x_verb_y() {
    checkScope(2, 3, 3, 5, annotate("a few cats chase dogs")[1]);
    assertFalse(annotate("a few cats chase dogs")[0].isPresent());
  }

  @Test
  public void fracasSentencesWithAll() {
    checkScope("{ All } [ APCOM managers ] [ have company cars ]");
    checkScope("{ All } [ Canadian residents ] [ can travel freely within Europe ]");
    checkScope("{ All } [ Europeans ] [ are people ]");
    checkScope("{ All } [ Europeans ] [ can travel freely within Europe ]");
    checkScope("{ All } [ Europeans ] [ have the right to live in Europe ]");
    checkScope("{ All } [ Italian men ] [ want to be a great tenor ]");
    checkScope("{ All } [ committee members ] [ are people ]");
    checkScope("{ All } [ competent legal authorities ] [ are competent law lecturers ]");
    checkScope("{ All } [ elephants ] [ are large animals ]");
    checkScope("{ All } [ fat legal authorities ] [ are fat law lecturers ]");
    checkScope("{ All } [ law lecturers ] [ are legal authorities ]");
    checkScope("{ All } [ legal authorities ] [ are law lecturers ]");
    checkScope("{ All } [ mice ] [ are small animals ]");
    checkScope("{ All } [ people who are from Portugal ] [ are from southern Europe ]");
    checkScope("{ All } [ people who are from Sweden ] [ are from Scandinavia ]");
    checkScope("{ All } [ people who are resident in Europe ] [ can travel freely within Europe ]");
    checkScope("{ All } [ residents of major western countries ] [ are residents of western countries ]");
    checkScope("{ All } [ residents of member states ] [ are individuals ]");
    checkScope("{ All } [ residents of the North American continent ] [ can travel freely within Europe ]");
    checkScope("{ All } [ the people who were at the meeting ] [ voted for a new chairman ]");
  }

  @Test
  public void fracasSentencesWithSome() {
    checkScope("{ A } [ Scandinavian ] [ won a Nobel prize ]");
    checkScope("{ A } [ Swede ] [ won a Nobel prize ]");
    checkScope("{ A } [ company director ] [ awarded himself a large payrise ]");
    checkScope("{ A } [ company director ] [ has awarded and been awarded a payrise ]");
    checkScope("{ A } [ lawyer ] [ signed every report ]");

    checkScope("{ An } [ Irishman ] [ won a Nobel prize ]");
    checkScope("{ An } [ Irishman ] [ won the Nobel prize for literature ]");
    checkScope("{ An } [ Italian ] [ became the world's greatest tenor ]");

    checkScope("{ A few } [ committee members ] [ are from Scandinavia ]");
    checkScope("{ A few } [ committee members ] [ are from Sweden ]");
    checkScope("{ A few } [ female committee members ] [ are from Scandinavia ]");
    checkScope("{ A few } [ great tenors ] [ sing popular music ]");
  }

  @Test
  public void fracasSentencesWithProperNouns() {
    checkScope("[ { APCOM } ] [ has a more important customer than ITEL ]");
    checkScope("[ { APCOM } ] [ has a more important customer than ITEL has ]");
    checkScope("[ { APCOM } ] [ has a more important customer than ITEL is ]");
    checkScope("[ { APCOM } ] [ has been paying mortgage interest for a total of 15 years or more ]");
    checkScope("[ { APCOM } ] [ lost some orders ]");
    checkScope("[ { APCOM } ] [ lost ten orders ]");
    checkScope("[ { APCOM } ] [ signed the contract Friday, 13th ]");
    checkScope("[ { APCOM } ] [ sold exactly 2500 computers ]");
    checkScope("[ { APCOM } ] [ won some orders ]");
    checkScope("[ { APCOM } ] [ won ten orders ]");
  }


}
