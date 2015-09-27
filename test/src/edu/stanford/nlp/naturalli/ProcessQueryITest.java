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
        "1\tall	2	op	0	q	anti-additive	2-3	multiplicative	3-5\n" +
        "2\tcat	3	nsubj	2	n	-	-	-	-\n" +
        "3\thave	0	root	3	v	-	-	-	-\n"+
        "4\ttail	3	dobj	2	n	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats have tails", pipeline));
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats have tails.", pipeline));
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all cats, have tails.", pipeline));
  }

  @Test
  public void allCatsAreBlue() {
    assertEquals(
        "1\tall	2	op	0	q	anti-additive	2-3	multiplicative	3-5\n" +
        "2\tcat	4	nsubj	2	n	-	-	-	-\n" +
        "3\tbe	4	cop	3	v	-	-	-	-\n"+
        "4\tblue	0	root	0	j	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("all cats are blue", pipeline));
  }

  @Test
  public void someCatsPlayWithYarn() {
    assertEquals(
        "1\tsome	2	op	0	q	additive	2-3	additive	3-6\n" +
        "2\tcat	3	nsubj	2	n	-	-	-	-\n" +
        "3\tplay	0	root	11	v	-	-	-	-\n" +
        "4	with	5	case	0	i	-	-	-	-\n" +
        "5	yarn	3	nmod:with	2	n	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("some cats play with yarn", pipeline)
    );
  }

  @Test
  public void noCatsLikeDogs() {
    assertEquals(
        "1\tno	2	op	0	q	anti-additive	2-3	anti-additive	3-5\n" +
        "2\tcat	3	nsubj	2	n	-	-	-	-\n" +
        "3\tlike	0	root	4	v	-	-	-	-\n" +
        "4\tdog	3	dobj	2	n	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("no cat likes dogs", pipeline)
    );
  }

  @Test
  public void noManIsVeryBeautiful() {
    assertEquals(
        "1\tno	2	op	0	q	anti-additive	2-3	anti-additive	3-6\n" +
        "2\tman	5	nsubj	2	n	-	-	-	-\n" +
        "3\tbe	5	cop	3	v	-	-	-	-\n" +
        "4\tvery	5	advmod	4	r	-	-	-	-\n" +
        "5\tbeautiful	0	root	2	j	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("no man is very beautiful", pipeline)
    );
  }

  @Test
  public void collapseWords() {
    String expected =
        "1\tall	2	op	0	q	anti-additive	2-3	multiplicative	3-5\n" +
        "2\tblack cat	3	nsubj	2	n	-	-	-	-\n" +
        "3\thave	0	root	3	v	-	-	-	-\n"+
        "4\ttail	3	dobj	2	n	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("all black cats have tails", pipeline));
  }

  @Test
  public void regression1() {
    String expected =
        "1\tno	2	op	0	q	anti-additive	2-3	anti-additive	3-9\n" +
        "2\tdog	3	nsubj	2	n	-	-	-	-\n" +
        "3\tchase	0	root	5	v	-	-	-	-\n"+
        "4\ta	5	det	0	d	-	-	-	-\n"+
        "5\tcat	3	dobj	2	n	-	-	-	-\n"+
        "6	in order	8	mark	0	n	-	-	-	-\n" +
        "7	to	8	mark	0	t	-	-	-	-\n" +
        "8	catch it	3	advcl	2	v	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("No dog chased a cat in order to catch it", pipeline));
  }


  @Test
  public void apposEdge() {
    String expected =
        "1\tno	2	op	0	q	anti-additive	2-3	anti-additive	3-7\n" +
        "2\tdog	3	nsubj	2	n	-	-	-	-\n" +
        "3\tchase	0	root	5	v	-	-	-	-\n"+
        "4\tthe	5	det	0	d	-	-	-	-\n"+
        "5\tcat	3	dobj	2	n	-	-	-	-\n"+
        "6\tFelix	5	appos	0	n	-	-	-	-\n";
    assertEquals(expected, ProcessQuery.annotateHumanReadable("No dogs chase the cat, Felix", pipeline));
  }

  @Test
  public void obamaBornInHawaii() {
    String expected =
        "1\tObama	3	nsubjpass	0	n	-	-	-	-\n"+
        "2\tbe	3	auxpass	3	v	-	-	-	-\n"+
        "3\tbear	0	root	4	v	-	-	-	-\n" +
        "4	in	5	case	0	i	-	-	-	-\n" +
        "5	Hawaii	3	nmod:in	2	n	-	-	-	-	l\n";
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
        "1\tboth	2	op	0	q	nonmonotone	2-3	multiplicative	3-7\n" +
        "2\tcommissioner	3	n	nsubj	1	-	-	-	-\n" +
        "3\tuse to	0	root	0	v	-	-	-	-\n" +
        "4\tbe	6	cop	9	v	-	-	-	-\n" +
        "5\tlead	6	amod	20	j	-	-	-	-\n" +
        "6\tbusinessman	3	xcomp	2	n	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("Both commissioners used to be leading businessmen", pipeline)
    );
  }

  @Ignore
  @Test
  public void thereAreCatsWhoAreFriendly() {
    assertEquals(
        "1\tthere be	0	root	0	q	additive	2-3	additive	3-6\n" +
        "2\tcat	1	nsubj	2	n	-	-	-	-\n" +
        "3\twho	5	nsubj	9	n	-	-	-	-\n" +
        "4\tbe	5	cop	1	v	-	-	-	-\n" +
        "5\tfriendly	1	rcmod	1	n	-	-	-	-\n",
        ProcessQuery.annotateHumanReadable("There are cats who are friendly", pipeline)
    );
  }
}
