package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.StreamGobbler;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Properties;

/**
 * Create a knowledge base from a TSV file containing raw sentences.
 *
 * The format of the TSV file should be:
 *
 * <ol>
 *   <li>An id for the sentence.</li>
 *   <li>The sentence itself.</li>
 * </ol>
 *
 * @author Gabor Angeli
 */
public class CreateKnowledgeBase {

  @ArgumentParser.Option(name="hash_tree", gloss="The executable path for hash_tree")
  private static String hashTreeExe = "src/hash_tree";

  @ArgumentParser.Option(name="corpus", gloss="The stream (or file) to read the corpus from")
  private static InputStream corpus = System.in;

  private static BufferedReader hasherOut;
  private static Writer hasherIn;

  /**
   *
   * @param fragment
   * @return
   */
  private static synchronized BigInteger hashEntailment(SemanticGraph fragment) {
    // Get the c processor
    if (hasherOut == null || hasherIn == null) {
      try {
        ProcessBuilder hasherBuilder = new ProcessBuilder(hashTreeExe);
        Process hasher = hasherBuilder.start();
        // Gobble errors
        Writer errWriter = new BufferedWriter(new OutputStreamWriter(System.err));
        StreamGobbler errGobbler = new StreamGobbler(hasher.getErrorStream(), errWriter);
        errGobbler.start();

        hasherOut = new BufferedReader(new InputStreamReader(hasher.getInputStream()));
        hasherIn = new OutputStreamWriter(new BufferedOutputStream(hasher.getOutputStream()));

      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    try {
      String dump = ProcessQuery.conllDump(fragment, false, false);
      // Write tree
      hasherIn.write(dump);
      hasherIn.write('\n');
      hasherIn.flush();
      //Read hash
      return new BigInteger(hasherOut.readLine());
    } catch (Exception e) {
      System.err.println("Exception from hasher: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
      return new BigInteger("-1");
    }
  }

  /**
   *
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    ArgumentParser.fillOptions(CreateKnowledgeBase.class, args);
    long startTime = System.currentTimeMillis();

    // Create a StanfordCoreNLP pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
    props.setProperty("openie.splitter.threshold", "0.10");
    props.setProperty("openie.optimze_for", "GENERAL");
    props.setProperty("openie.ignoreaffinity", "false");
    props.setProperty("openie.max_entailments_per_clause", "1000");
    props.setProperty("openie.triple.strict", "false");
    props.setProperty("enforceRequirements", "false");
//    props.setProperty("openie.splitter.model", "/home/gabor/tmp/clauseSplitterModel_all.ser.gz");
//    props.setProperty("openie.affinity_models", "/home/gabor/workspace/naturalli/etc/");
    props.setProperty("ssplit.isOneSentence", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // Process the input
    BufferedReader reader = new BufferedReader(new InputStreamReader(corpus));
    String line;
    int linesread = 0;
    while ( (line = reader.readLine()) != null) {
      // Variables we'll need
      String id = "?";  // The default ID
      Annotation doc;

      // Read the sentence
      if (line.trim().equals("")) { continue; }
      if (line.contains("\t")) {
        String[] fields = line.split("\t");
        if (fields.length == 1) { continue; }
        if (fields.length != 2) {
          throw new IllegalArgumentException("Malformed line: " + line);
        }
        if (fields[1].trim().equals("")) { continue; }
        id = fields[0].trim();
        doc = new Annotation(fields[1].trim());
      } else {
        doc = new Annotation(line.trim());
      }

      // Annotate the sentence
      pipeline.annotate(doc);
      CoreMap sentence = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      Collection<SentenceFragment> fragments = sentence.get(NaturalLogicAnnotations.EntailedSentencesAnnotation.class);
      if (fragments != null) {
        for (SentenceFragment fragment : fragments) {
          // Add the hash
          BigInteger hash = hashEntailment(fragment.parseTree);
          if (!hash.equals(new BigInteger("-1"))) {
            System.out.println(id + '\t' + hash.toString());
          }
        }
      }

      // Debug
      linesread += 1;
      if (linesread % 1000 == 0) {
        System.err.println("  Read " + (linesread / 1000) + "k lines: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
      }
    }



  }
}
