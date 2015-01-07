package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * A collection of regressions for ProcessQuery.
 *
 * @author Gabor Angeli
 */
public class ProcessQueryITest {
  private static final StanfordCoreNLP pipeline = ProcessQuery.constructPipeline();

  @Test
  public void allCatsHaveTails() {
    String expected =
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "cat	3	nsubj	1	-	-	-	-\n" +
        "have	0	root	2	-	-	-	-\n"+
        "tail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotate("all cats have tails", pipeline));
    assertEquals(expected, ProcessQuery.annotate("all cats have tails.", pipeline));
    assertEquals(expected, ProcessQuery.annotate("all cats, have tails.", pipeline));
  }

  @Test
  public void allCatsAreBlue() {
    assertEquals(
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "cat	4	nsubj	1	-	-	-	-\n" +
        "be	4	cop	9	-	-	-	-\n"+
        "blue	0	root	3	-	-	-	-\n",
        ProcessQuery.annotate("all cats are blue", pipeline));
  }

  @Test
  public void someCatsPlayWithYarn() {
    assertEquals(
        "some	2	det	0	additive	2-3	additive	3-5\n" +
        "cat	3	nsubj	1	-	-	-	-\n" +
        "play	0	root	21	-	-	-	-\n" +
        "yarn	3	prep_with	2	-	-	-	-\n",
        ProcessQuery.annotate("some cats play with yarn", pipeline)
    );
  }

  @Test
  public void noCatsLikeDogs() {
    assertEquals(
        "no	2	det	0	anti-additive	2-3	anti-additive	3-5\n" +  // TODO(gabor) should be neg; this is a parse error
        "cat	3	nsubj	1	-	-	-	-\n" +
        "like	0	root	4	-	-	-	-\n" +
        "dog	3	dobj	1	-	-	-	-\n",
        ProcessQuery.annotate("no cat likes dogs", pipeline)
    );
  }

  @Test
  public void noManIsVeryBeautiful() {
    assertEquals(
        "no	2	det	0	anti-additive	2-3	anti-additive	3-6\n" +  // TODO(gabor) this should be neg; this is a parse error
            "man	5	nsubj	2	-	-	-	-\n" +
            "be	5	cop	9	-	-	-	-\n" +
            "very	5	advmod	1	-	-	-	-\n" +
            "beautiful	0	root	1	-	-	-	-\n",
        ProcessQuery.annotate("no man is very beautiful", pipeline)
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
        ProcessQuery.annotate("There are cats who are friendly", pipeline)
    );
  }

  @Test
  public void collapseWords() {
    String expected =
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "black cat	3	nsubj	1	-	-	-	-\n" +
        "have	0	root	2	-	-	-	-\n"+
        "tail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotate("all black cats have tails", pipeline));
  }

  @Ignore // TODO(gabor) I think this test is actually wrong?
  @Test
  public void collapseWordsNoClearRoot() {
    assertEquals(
        "both	2	det	0	nonmonotone	2-3	multiplicative	3-7\n" +
            "commissioner	3	nsubj	1	-	-	-	-\n" +
            "use to	0	root	0	-	-	-	-\n" +
            "be	6	cop	9	-	-	-	-\n" +
            "lead	6	amod	20	-	-	-	-\n" +
            "businessman	3	xcomp	2	-	-	-	-\n",
        ProcessQuery.annotate("Both commissioners used to be leading businessmen", pipeline)
    );
  }

}
