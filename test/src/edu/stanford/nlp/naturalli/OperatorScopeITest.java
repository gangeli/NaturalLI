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
 * A test for the {@link NaturalLogicAnnotator} setting the right
 * {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.OperatorAnnotation}s.
 *
 * @author Gabor Angeli
 */
public class OperatorScopeITest {

  private static final StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,parse");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
  }});

  static {
    pipeline.addAnnotator(new NaturalLogicAnnotator());
  }

  @SuppressWarnings("unchecked")
  private Optional<OperatorSpec>[] annotate(String text) {
    Annotation ann = new Annotation(text);
    pipeline.annotate(ann);
    List<CoreLabel> tokens = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class);
    Optional<OperatorSpec>[] scopes = new Optional[tokens.size()];
    Arrays.fill(scopes, Optional.empty());
    for (int i = 0; i < tokens.size(); ++i) {
      if (tokens.get(i).containsKey(NaturalLogicAnnotations.OperatorAnnotation.class)) {
        scopes[i] = Optional.of(tokens.get(i).get(NaturalLogicAnnotations.OperatorAnnotation.class));
      }
    }
    return scopes;
  }

  private void checkScope(int subjBegin, int subjEnd, int objBegin, int objEnd, Optional<OperatorSpec> guess) {
    assertTrue("No quantifier found", guess.isPresent());
    assertEquals("Bad subject begin " + guess.get(), subjBegin, guess.get().subjectBegin);
    assertEquals("Bad subject end " + guess.get(), subjEnd, guess.get().subjectEnd);
    assertEquals("Bad object begin " + guess.get(), objBegin, guess.get().objectBegin);
    assertEquals("Bad object end " + guess.get(), objEnd, guess.get().objectEnd);
  }

  private void checkScope(int subjBegin, int subjEnd, Optional<OperatorSpec> guess) {
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
    Optional<OperatorSpec>[] scopes = annotate(StringUtils.join(cleanSentence, " "));
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
    checkScope(1, 2, 2, 6, annotate("All cats have spoken to Fido.")[0]);
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
  public void all_of_X_verb_Y() {
    checkScope(1, 4, 4, 6, annotate("All of the cats hate dogs.")[0]);
    checkScope(1, 6, 6, 9, annotate("Each of the other 99 companies owns one computer.")[0]);
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
  public void PER_has_predicate_prep() {
    checkScope(0, 1, 1, 5, annotate("Felix has spoken to Fido.")[0]);
  }

  @Test
  public void PER_is_nn() {
    checkScope(0, 1, 1, 4, annotate("Felix is a cat.")[0]);
  }

  @Test
  public void PER_is_jj() {
    checkScope(0, 1, 1, 3, annotate("Felix is cute.")[0]);
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
  public void binary_no() {
    checkScope(1, 2, 2, 4, annotate("no cats chase dogs")[0]);
  }

  @Test
  public void unary_not() {
    Optional<OperatorSpec>[] quantifiers = annotate("some cats don't like dogs");
    checkScope(1, 2, 2, 6, quantifiers[0]); // some
    checkScope(4, 6, quantifiers[3]); // no
    assertFalse(quantifiers[3].get().isBinary());  // is unary no
  }

  @Test
  public void num_X_verb_Y() {
    checkScope(1, 2, 2, 4, annotate("Three cats eat mice.")[0]);
    checkScope(1, 2, 2, 4, annotate("3 cats have tails.")[0]);
  }

  @Test
  public void at_least_num_X_verb_Y() {
    checkScope(3, 4, 4, 6, annotate("at least Three cats eat mice.")[2]);
    checkScope(3, 4, 4, 6, annotate("at least 3 cats have tails.")[2]);
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

    checkScope("{ Each } [ Canadian resident ] [ can travel freely within Europe ]");
    checkScope("{ Each } [ European ] [ can travel freely within Europe ]");
    checkScope("{ Each } [ European ] [ has the right to live in Europe ]");
    checkScope("{ Each } [ Italian tenor ] [ wants to be great ]");
    checkScope("{ Each } [ department ] [ has a dedicated line ]");
    checkScope("{ Each } [ of the other 99 companies ] [ owns one computer ]");
    checkScope("{ Each } [ resident of the North American continent ] [ can travel freely within Europe ]");
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

    checkScope("{ At least a few } [ committee members ] [ are from Scandinavia ]");
    checkScope("{ At least a few } [ committee members ] [ are from Sweden ]");
    checkScope("{ At least a few } [ female committee members ] [ are from Scandinavia ]");
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

    checkScope("[ { Bill } ] [ bought a car ]");
    checkScope("[ { Bill } ] [ has spoken to Mary ]");
    checkScope("[ { Bill } ] [ is going to ]");
    checkScope("[ { Bill } ] [ knows why John had his paper accepted ]");
    checkScope("[ { Bill } ] [ owns a blue car ]");
    checkScope("[ { Bill } ] [ owns a blue one ]");
    checkScope("[ { Bill } ] [ owns a car ]");
    checkScope("[ { Bill } ] [ owns a fast car ]");
    checkScope("[ { Bill } ] [ owns a fast one ]");
    checkScope("[ { Bill } ] [ owns a fast red car ]");
    checkScope("[ { Bill } ] [ owns a red car ]");
    checkScope("[ { Bill } ] [ owns a slow one ]");
    checkScope("[ { Bill } ] [ owns a slow red car ]");
    checkScope("[ { Bill } ] [ said Mary wrote a report ]");
    checkScope("[ { Bill } ] [ said Peter wrote a report ]");
    checkScope("[ { Bill } ] [ spoke to Mary ]");
    checkScope("[ { Bill } ] [ spoke to Mary at five o'clock ]");
    checkScope("[ { Bill } ] [ spoke to Mary at four o'clock ]");
    checkScope("[ { Bill } ] [ spoke to Mary on Monday ]");
    checkScope("[ { Bill } ] [ spoke to everyone that John did ]");
    checkScope("[ { Bill } ] [ suggested to Frank's boss that they should go to the meeting together, and Carl to Alan's wife ]");
    checkScope("[ { Bill } ] [ went to Berlin by car ]");
    checkScope("[ { Bill } ] [ went to Berlin by train ]");
    checkScope("[ { Bill } ] [ went to Paris by train ]");
    checkScope("[ { Bill } ] [ will speak to Mary ]");
    checkScope("[ { Bill } ] [ wrote a report ]");

    checkScope("[ { Dumbo } ] [ is a four-legged animal ]");
    checkScope("[ { Dumbo } ] [ is a large animal ]");
    checkScope("[ { Dumbo } ] [ is a small animal ]");
    checkScope("[ { Dumbo } ] [ is a small elephant ]");
    checkScope("[ { Dumbo } ] [ is four-legged ]");
    checkScope("[ { Dumbo } ] [ is larger than Mickey ]");
  }

  @Test
  public void fracasSentencesWithNumberQuantifiers() {
    checkScope("{ At least three } [ commissioners ] [ spend a lot of time at home ]");
    checkScope("{ At least three } [ commissioners ] [ spend time at home ]");
    checkScope("{ At least three } [ female commissioners ] [ spend time at home ]");
    checkScope("{ At least three } [ male commissioners ] [ spend time at home ]");
    checkScope("{ At least three } [ tenors ] [ will take part in the concert ]");
    checkScope("{ At most ten } [ commissioners ] [ spend a lot of time at home ]");
    checkScope("{ At most ten } [ commissioners ] [ spend time at home ]");
    checkScope("{ At most ten } [ female commissioners ] [ spend time at home ]");
  }

  @Test
  public void fracasSentencesWithMost() {
    checkScope("{ Both } [ commissioners ] [ used to be businessmen ]");
    checkScope("{ Both } [ commissioners ] [ used to be leading businessmen ]");
    checkScope("{ Both } [ leading tenors ] [ are excellent ]");
    checkScope("{ Both } [ leading tenors ] [ are indispensable ]");
  }


}
