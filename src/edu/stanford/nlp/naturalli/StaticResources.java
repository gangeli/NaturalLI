package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;

import java.util.*;
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

  /**
   * A set of Semgrex patterns which indicate that the target can move in the meronymy relation.
   */
  public static Set<SemgrexPattern> MERONYM_TRIGGERS = Collections.unmodifiableSet(new HashSet<SemgrexPattern>() {{
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:arrive} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:say} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:visit} > dobj {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:be} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:bear} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:bear} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:tell} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:win} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_with ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:arrest} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_from ({lemma:report} > prep_from {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_with ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_in ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_from ({lemma:report} > prep_from {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:report} > prep_from {}=place)"));
    add(SemgrexPattern.compile("{}=person < dobj ({lemma:meet} > rcmod {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:win} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:be} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < dobj ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:live} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:launch} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:hold} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:grow} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:disappear} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:attend} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:serve} > rcmod {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:write} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:be} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:warn} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:visit} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:visit} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:vanish} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < dobj ({lemma:tell} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:spend} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:spend} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:serve} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:serve} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:remain} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:release} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_in ({lemma:reach} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:preach} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:open} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:mob} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < dobj ({lemma:meet} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:live} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_by ({lemma:kill} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:kill} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:kidnap} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:jail} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:be} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:be} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:imprison} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_in ({lemma:hospitalize} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:hospitalise} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:hide} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:hold} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:hold} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:grow} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:give} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < agent ({lemma:find} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:fight} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:eliminate} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:educate} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < pobj ({lemma:die} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:die} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:cremate} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:capture} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < partmod ({lemma:bury} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:bury} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < prep_in ({lemma:bear} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:beat} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:apprehend} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:admit} > prep_in {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:host} > prep_from {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:triumph} > prep_at {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:torture} > prep_at {}=place)"));
    add(SemgrexPattern.compile("{}=person < nsubj ({lemma:start} > prep_at {}=place)"));
    add(SemgrexPattern.compile("{}=person < rcmod ({lemma:injure} > prep_at {}=place)"));
  }});

}
