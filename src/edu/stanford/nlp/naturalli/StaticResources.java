package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Static resources; e.g., indexers.
 *
 * TODO(gabor) handle numbers
 *
 * @author Gabor Angeli
 */
public class StaticResources {

  private static Map<String, Integer> PHRASE_INDEXER = Collections.unmodifiableMap(new HashMap<String, Integer>() {{
    System.err.print("Reading phrase indexer...");
    for (String line : IOUtils.readLines(System.getenv("VOCAB_FILE") == null ? "etc/vocab.tab.gz" : System.getenv("VOCAB_FILE"))) {
      String[] fields = line.split("\t");
      put(fields[1], Integer.parseInt(fields[0]));
    }
    System.err.println("done.");
  }});

  public static Function<String, Integer> INDEXER = (String gloss) -> {
    Integer index = PHRASE_INDEXER.get(gloss);
    if (index != null) { return index; }
    index = PHRASE_INDEXER.get(gloss.toLowerCase());
    if (index != null) { return index; }
    return -1;
  };

  public static Map<Integer, Map<String, Integer>> SENSE_INDEXER = Collections.unmodifiableMap(new HashMap<Integer, Map<String, Integer>>(){{
    System.err.print("Reading sense indexer...");
    for (String line : IOUtils.readLines(System.getenv("SENSE_FILE") == null ? "etc/sense.tab.gz" : System.getenv("SENSE_FILE"))) {
      String[] fields = line.split("\t");
      int word = Integer.parseInt(fields[0]);
      Map<String, Integer> definitions = this.get(word);
      if (definitions == null) {
        definitions = new HashMap<>();
        put(word, definitions);
      }
      definitions.put(fields[2], Integer.parseInt(fields[1]));
    }
    System.err.println("done.");
  }});

}
