package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.*;

import java.io.BufferedReader;
import java.io.IOException;

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
        "cat	3	nsubj	2	-	-	-	-\n" +
        "have	0	root	3	-	-	-	-\n"+
        "tail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats have tails", pipeline));
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats have tails.", pipeline));
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats, have tails.", pipeline));
  }

  @Test
  public void allCatsAreBlue() {
    assertEquals(
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "cat	4	nsubj	2	-	-	-	-\n" +
        "be	4	cop	3	-	-	-	-\n"+
        "blue	0	root	2	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("all cats are blue", pipeline));
  }

  @Test
  public void someCatsPlayWithYarn() {
    assertEquals(
        "some	2	det	0	additive	2-3	additive	3-5\n" +
        "cat	3	nsubj	2	-	-	-	-\n" +
        "play	0	root	11	-	-	-	-\n" +
        "yarn	3	prep_with	3	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("some cats play with yarn", pipeline)
    );
  }

  @Test
  public void noCatsLikeDogs() {
    assertEquals(
        "no	2	det	0	anti-additive	2-3	anti-additive	3-5\n" +  // TODO(gabor) should be neg; this is a parse error
        "cat	3	nsubj	2	-	-	-	-\n" +
        "like	0	root	5	-	-	-	-\n" +
        "dog	3	dobj	2	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("no cat likes dogs", pipeline)
    );
  }

  @Test
  public void noManIsVeryBeautiful() {
    assertEquals(
        "no	2	det	0	anti-additive	2-3	anti-additive	3-6\n" +  // TODO(gabor) this should be neg; this is a parse error
            "man	5	nsubj	2	-	-	-	-\n" +
            "be	5	cop	3	-	-	-	-\n" +
            "very	5	advmod	4	-	-	-	-\n" +
            "beautiful	0	root	2	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("no man is very beautiful", pipeline)
    );
  }

  @Test
  public void collapseWords() {
    String expected =
        "all	2	det	0	anti-additive	2-3	multiplicative	3-5\n" +
        "black cat	3	nsubj	2	-	-	-	-\n" +
        "have	0	root	3	-	-	-	-\n"+
        "tail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all black cats have tails", pipeline));
  }

  @Test
  public void regression1() {
    String expected =
        "no	2	det	0	anti-additive	2-3	anti-additive	3-10\n" +
        "dog	3	nsubj	2	-	-	-	-\n" +
        "chase	0	root	5	-	-	-	-\n"+
        "a	5	det	0	-	-	-	-\n"+
        "cat	3	dobj	2	-	-	-	-\n"+
        "in	9	mark	3	-	-	-	-\n"+
        "order	9	dep	2	-	-	-	-\n"+
        "to	9	aux	0	-	-	-	-\n"+
        "catch it	3	advcl	2	-	-	-	-\n";  // TODO(gabor) Yes, this is strange...
    assertEquals(expected, ProcessQuery.annotateHumanReadable("No dog chased a cat in order to catch it", pipeline));
  }

  /**
   * Run through FraCaS, and make sure none of the sentences crash the querier
   */
  @Test
  public void fracasCrashTest() throws IOException {
    BufferedReader reader =IOUtils.getBufferedReaderFromClasspathOrFileSystem("test/data/perfcase_fracas_all.examples");
    String line;
    while ( (line = reader.readLine()) != null ) {
      line = line.trim();
      if (!line.startsWith("#") && !"".equals(line)) {
        line = line.replace("TRUE: ", "").replace("FALSE: ", "").replace("UNK: ", "").trim();
        System.err.println(line);
        ProcessQuery.annotateHumanReadable(line, pipeline);
      }
    }
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
        ProcessQuery.annotateHumanReadable("Both commissioners used to be leading businessmen", pipeline)
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
        ProcessQuery.annotateHumanReadable("There are cats who are friendly", pipeline)
    );
  }
}
