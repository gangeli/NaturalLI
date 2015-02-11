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
        "1\tall	2	op	0	anti-additive	2-3	multiplicative	3-5\n" +
        "2\tcat	3	nsubj	2	-	-	-	-\n" +
        "3\thave	0	root	3	-	-	-	-\n"+
        "4\ttail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats have tails", pipeline));
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats have tails.", pipeline));
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats, have tails.", pipeline));
  }

  @Test
  public void allCatsAreBlue() {
    assertEquals(
        "1\tall	2	op	0	anti-additive	2-3	multiplicative	3-5\n" +
        "2\tcat	4	nsubj	2	-	-	-	-\n" +
        "3\tbe	4	cop	3	-	-	-	-\n"+
        "4\tblue	0	root	0	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("all cats are blue", pipeline));
  }

  @Test
  public void someCatsPlayWithYarn() {
    assertEquals(
        "1\tsome	2	op	0	additive	2-3	additive	3-5\n" +
        "2\tcat	3	nsubj	2	-	-	-	-\n" +
        "3\tplay	0	root	11	-	-	-	-\n" +
        "4\tyarn	3	prep_with	2	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("some cats play with yarn", pipeline)
    );
  }

  @Test
  public void noCatsLikeDogs() {
    assertEquals(
        "1\tno	2	op	0	anti-additive	2-3	anti-additive	3-5\n" +
        "2\tcat	3	nsubj	2	-	-	-	-\n" +
        "3\tlike	0	root	4	-	-	-	-\n" +
        "4\tdog	3	dobj	2	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("no cat likes dogs", pipeline)
    );
  }

  @Test
  public void noManIsVeryBeautiful() {
    assertEquals(
        "1\tno	2	op	0	anti-additive	2-3	anti-additive	3-6\n" +
        "2\tman	5	nsubj	2	-	-	-	-\n" +
        "3\tbe	5	cop	3	-	-	-	-\n" +
        "4\tvery	5	advmod	4	-	-	-	-\n" +
        "5\tbeautiful	0	root	2	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("no man is very beautiful", pipeline)
    );
  }

  @Test
  public void collapseWords() {
    String expected =
        "1\tall	2	op	0	anti-additive	2-3	multiplicative	3-5\n" +
        "2\tblack cat	3	nsubj	2	-	-	-	-\n" +
        "3\thave	0	root	3	-	-	-	-\n"+
        "4\ttail	3	dobj	2	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all black cats have tails", pipeline));
  }

  @Test
  public void regression1() {
    String expected =
        "1\tno	2	op	0	anti-additive	2-3	anti-additive	3-10\n" +
        "2\tdog	3	nsubj	2	-	-	-	-\n" +
        "3\tchase	0	root	5	-	-	-	-\n"+
        "4\ta	5	det	0	-	-	-	-\n"+
        "5\tcat	3	dobj	2	-	-	-	-\n"+
        "6\tin	9	mark	0	-	-	-	-\n"+
        "7\torder	9	dep	2	-	-	-	-\n"+
        "8\tto	9	aux	0	-	-	-	-\n"+
        "9\tcatch it	3	advcl	2	-	-	-	-\n";  // TODO(gabor) Yes, this is strange...
    assertEquals(expected, ProcessQuery.annotateHumanReadable("No dog chased a cat in order to catch it", pipeline));
  }


  @Test
  public void apposEdge() {
    String expected =
        "1\tno	2	op	0	anti-additive	2-3	anti-additive	3-7\n" +
        "2\tdog	3	nsubj	2	-	-	-	-\n" +
        "3\tchase	0	root	5	-	-	-	-\n"+
        "4\tthe	5	det	0	-	-	-	-\n"+
        "5\tcat	3	dobj	2	-	-	-	-\n"+
        "6\tFelix	5	appos	0	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("No dogs chase the cat, Felix", pipeline));
  }

  @Test
  public void obamaBornInHawaii() {
    String expected =
        "1\tObama	3	nsubjpass	0	-	-	-	-\n"+
        "2\tbe	3	auxpass	3	-	-	-	-\n"+
        "3\tbear	0	root	4	-	-	-	-\n" +
        "4\tHawaii	3	prep_in	2	-	-	-	-	l\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("Obama was born in Hawaii", pipeline));
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
        ProcessQuery.annotateHumanReadable(line, pipeline);
      }
    }
  }


  @Ignore // TODO(gabor) I think this test is actually wrong?
  @Test
  public void collapseWordsNoClearRoot() {
    assertEquals(
        "1\tboth	2	op	0	nonmonotone	2-3	multiplicative	3-7\n" +
        "2\tcommissioner	3	nsubj	1	-	-	-	-\n" +
        "3\tuse to	0	root	0	-	-	-	-\n" +
        "4\tbe	6	cop	9	-	-	-	-\n" +
        "5\tlead	6	amod	20	-	-	-	-\n" +
        "6\tbusinessman	3	xcomp	2	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("Both commissioners used to be leading businessmen", pipeline)
    );
  }

  @Ignore
  @Test
  public void thereAreCatsWhoAreFriendly() {
    assertEquals(
        "1\tthere be	0	root	0	additive	2-3	additive	3-6\n" +
        "2\tcat	1	nsubj	2	-	-	-	-\n" +
        "3\twho	5	nsubj	9	-	-	-	-\n" +
        "4\tbe	5	cop	1	-	-	-	-\n" +
        "5\tfriendly	1	rcmod	1	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("There are cats who are friendly", pipeline)
    );
  }
}
