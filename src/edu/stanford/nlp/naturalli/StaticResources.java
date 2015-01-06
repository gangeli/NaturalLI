package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

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
    for (String line : IOUtils.readLines("etc/words.tab.gz")) {
      String[] fields = line.split("\t");
      put(fields[1], Integer.parseInt(fields[0]));
    }
  }});

  public static Function<String, Integer> INDEXER = (String gloss) -> {
    Integer index = PHRASE_INDEXER.get(gloss);
    if (index != null) { return index; }
    index = PHRASE_INDEXER.get(gloss.toLowerCase());
    if (index != null) { return index; }
    return -1;
  };

  public static Map<Pair<Integer, String>, Integer> SENSE_INDEXER = Collections.unmodifiableMap(new HashMap<Pair<Integer, String>, Integer>(){{
    for (String line : IOUtils.readLines("etc/word_senses.tab.gz")) {
      String[] fields = line.split("\t");
      put(Pair.makePair(Integer.parseInt(fields[0]), fields[2]), Integer.parseInt(fields[1]));
    }
  }});

}
