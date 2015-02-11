package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.StreamGobbler;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/**
 * A script that takes a block of text as input, one sentence per line, and dumps a table of hashed
 * facts and a table of sentences linked to those facts.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("FieldCanBeLocal")
public class HashCorpus {

  @Execution.Option(name="hash_tree.exe", gloss="The location of the hash_tree utility")
  private static String hashTreeExe = "/home/gabor/workspace/naturalli/src/hash_tree";

  @Execution.Option(name="in", gloss="Input to read from")
  private static InputStream in = System.in;
  @Execution.Option(name="err", gloss="Output to debug to")
  private static PrintStream err = System.err;

  @Execution.Option(name="mod", gloss="The number of parallel jobs")
  private static int mod = 1;
  @Execution.Option(name="offset", gloss="The index of this job, in [0, mod)")
  private static int offset = 0;

  @Execution.Option(name="out.hashes", gloss="The file to write the hashed facts to", required=true)
  private static PrintStream hashOutput = null;
  @Execution.Option(name="out.sentences", gloss="The file to write the indexed sentences to", required=true)
  private static PrintStream sentenceOutput = null;

  private static BufferedReader hasherOut;
  private static Writer hasherIn;

  private static synchronized BigInteger hashEntailment(SemanticGraph fragment) {
    // Get the java processor

    // Get the c processor
    if (hasherOut == null || hasherIn == null) {
      try {
        ProcessBuilder hasherBuilder = new ProcessBuilder(hashTreeExe);
        Process hasher = hasherBuilder.start();
        // Gobble errors
        Writer errWriter = new BufferedWriter(new OutputStreamWriter(err));
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
    } catch (IOException | NullPointerException e) {
      System.err.println("Exception from hasher: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
      return new BigInteger("-1");
    }
  }

  public static void main(String[] args) throws IOException {
    Execution.fillOptions(new Class[]{HashCorpus.class, StaticResources.class}, args);

    StanfordCoreNLP pipeline = ProcessPremise.constructPipeline("depparse");

    BufferedReader stdin = new BufferedReader(new InputStreamReader(in));
    String line;
    int linesProcessed = 0;
    long startTime = System.currentTimeMillis();
    int sentenceIndex = offset;

    while ((line = stdin.readLine()) != null) {
      if (line.trim().equals("")) { continue; }
      // Write the sentence info
      sentenceOutput.println(sentenceIndex + "\t" + line.replace("\t", " "));

      // Write hash(es)
      Set<BigInteger> hashes = new HashSet<>();
      for (SentenceFragment entailment : ProcessPremise.forwardEntailments(line, pipeline)) {
        BigInteger hash = hashEntailment(entailment.parseTree);
        if (hash.compareTo(new BigInteger("0")) > 0) {
          hashes.add(hash);
        }
      }
      for (BigInteger hash : hashes) {
        hashOutput.println(hash + "\t" + sentenceIndex);
      }

      // Update sentence index
      sentenceIndex += mod;

      // Debug
      linesProcessed += 1;
      if (linesProcessed % 1000 == 0) {
        long currTime = System.currentTimeMillis();
        long sentPerSec = linesProcessed / ( (currTime - startTime)  / 1000 );
        err.println("[" + Redwood.formatTimeDifference(currTime - startTime) + "] Processed " + linesProcessed + " sentences {" + sentPerSec + " sentences / second}... ");
      }
    }

    sentenceOutput.close();
    hashOutput.close();
  }
}
