package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.*;

import java.util.Properties;

import static org.junit.Assert.*;

/**
 * A collection of regressions for Preprocess.
 *
 * @author Gabor Angeli
 */
public class PreprocessITest {
  private static final StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,parse");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
  }});

  static {
    Preprocess.PRODUCTION = false;
  }

  @Test
  public void allCatsHaveTails() {
    String expected =
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "cat	3	nsubj	1	-	-	-	-\n" +
        "have	0	root	2	-	-	-	-\n"+
        "tail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, Preprocess.annotate(pipeline, StaticResources.INDEXER, "all cats have tails"));
    assertEquals(expected, Preprocess.annotate(pipeline, StaticResources.INDEXER, "all cats have tails."));
    assertEquals(expected, Preprocess.annotate(pipeline, StaticResources.INDEXER, "all cats, have tails."));
  }

  @Test
  public void allCatsAreBlue() {
    assertEquals(
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "cat	4	nsubj	1	-	-	-	-\n" +
        "be	4	cop	9	-	-	-	-\n"+
        "blue	0	root	3	-	-	-	-\n",
        Preprocess.annotate(pipeline, StaticResources.INDEXER, "all cats are blue"));
  }

  @Test
  public void someCatsPlayWithYarn() {
    assertEquals(
        "some	2	det	0	additive	2-3	additive	3-6\n" +
        "cat	3	nsubj	1	-	-	-	-\n" +
        "play	0	root	21	-	-	-	-\n" +
        "with	3	prep	0	-	-	-	-\n" +
        "yarn	4	pobj	2	-	-	-	-\n",
        Preprocess.annotate(pipeline, StaticResources.INDEXER, "some cats play with yarn")
    );
  }

  @Test
  public void noCatsLikeDogs() {
    assertEquals(
        "no	2	neg	0	anti-additive	2-3	anti-additive	3-5\n" +
        "cat	3	nsubj	1	-	-	-	-\n" +
        "like	0	root	4	-	-	-	-\n" +
        "dog	3	dobj	1	-	-	-	-\n",
        Preprocess.annotate(pipeline, StaticResources.INDEXER, "no cat likes dogs")
    );
  }

  @Test
  public void noManIsVeryBeautiful() {
    assertEquals(
        "no	2	neg	0	anti-additive	2-3	anti-additive	3-6\n" +
        "man	5	nsubj	2	-	-	-	-\n" +
        "be	5	cop	9	-	-	-	-\n" +
        "very	5	advmod	1	-	-	-	-\n" +
        "beautiful	0	root	1	-	-	-	-\n",
        Preprocess.annotate(pipeline, StaticResources.INDEXER, "no man is very beautiful")
    );
  }

  @Ignore
  @Test
  public void thereAreCatsWhoAreFriendly() {
    assertEquals(
        "there be	0	root	0	additive	2-3	additive	3-6\n" +
        "cat	1	nsubj	2	-	-	-	-\n" +
        "who	5	nsubj	9	-	-	-	-\n" +
        "be	5	cop	1	-	-	-	-\n" +
        "friendly	1	rcmod	1	-	-	-	-\n",
        Preprocess.annotate(pipeline, StaticResources.INDEXER, "There are cats who are friendly")
    );
  }

  @Test
  public void collapseWords() {
    String expected =
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "black cat	3	nsubj	1	-	-	-	-\n" +
        "have	0	root	2	-	-	-	-\n"+
        "tail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, Preprocess.annotate(pipeline, StaticResources.INDEXER, "all black cats have tails"));
  }

  @Test
  public void collapseWordsNoClearRoot() {
    assertEquals(
        "both	2	det	0	nonmonotone	2-3	multiplicative	3-7\n" +
        "commissioner	3	nsubj	1	-	-	-	-\n" +
        "use to	0	root	0	-	-	-	-\n" +
        "be	6	cop	9	-	-	-	-\n" +
        "lead	6	amod	20	-	-	-	-\n" +
        "businessman	3	xcomp	2	-	-	-	-\n",
        Preprocess.annotate(pipeline, StaticResources.INDEXER, "Both commissioners used to be leading businessmen")
    );
  }

}
