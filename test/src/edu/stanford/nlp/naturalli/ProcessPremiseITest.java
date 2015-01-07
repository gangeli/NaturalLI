package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * A simple search class to test the premise creator. This is not intended to be an exhaustive search -- see the CoreNLP
 * tests for that.
 *
 * @author Gabor Angeli
 */
public class ProcessPremiseITest {

  private static final StanfordCoreNLP pipeline = ProcessPremise.constructPipeline();

  @Test
  public void regressionTest1() throws IOException {
    for (SentenceFragment fragment : ProcessPremise.forwardEntailments("Some Italian men are great tenors.", pipeline)) {
      ProcessQuery.conllDump(fragment.parseTree);
    }
  }

  @Test
  public void regressionTest2() throws IOException {
    for (SentenceFragment fragment : ProcessPremise.forwardEntailments("At least three tenors will take part in the concert.", pipeline)) {
      ProcessQuery.conllDump(fragment.parseTree);
    }
  }

  /**
   * Run through FraCaS, and make sure none of the sentences crash the premise generator
   */
  @Test
  public void fracasCrashTest() throws IOException {
    BufferedReader reader = IOUtils.getBufferedReaderFromClasspathOrFileSystem("test/data/perfcase_fracas_all.examples");
    String line;
    while ( (line = reader.readLine()) != null ) {
      line = line.trim();
      if (!line.startsWith("#") && !"".equals(line)) {
        line = line.replace("TRUE: ", "").replace("FALSE: ", "").replace("UNK: ", "").trim();
        System.err.println(line);
        for (SentenceFragment fragment : ProcessPremise.forwardEntailments(line, pipeline)) {
          ProcessQuery.conllDump(fragment.parseTree);
        }
      }
    }
  }
}
