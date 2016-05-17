package edu.stanford.nlp.naturalli;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.Lazy;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
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

  @ArgumentParser.Option(name="vocab_file", gloss="The location of the vocabulary file")
  private static String vocabFile = System.getenv("VOCAB_FILE") == null ? "etc/vocab.tab.gz" : System.getenv("VOCAB_FILE");

  @ArgumentParser.Option(name="sense_file", gloss="The location of the sense mapping file")
  private static String senseFile = System.getenv("VOCAB_FILE") == null ? "etc/sense.tab.gz" : System.getenv("SENSE_FILE");

  @ArgumentParser.Option(name="wordnet_file", gloss="The location of the WordNet synset mapping file")
  private static String wordnetFile = System.getenv("WORDNET_FILE") == null ? "etc/wordnet.tab.gz" : System.getenv("WORDNET_FILE");

  private static Lazy<Map<String, Integer>> PHRASE_INDEXER = Lazy.of( () ->new HashMap<String, Integer>() {{
    long startTime = System.currentTimeMillis();
    System.err.print("Reading phrase indexer...");
    for (String line : IOUtils.readLines(vocabFile)) {
      String[] fields = line.split("\t");
      put(fields[1], Integer.parseInt(fields[0]));
    }
    System.err.println("done. [" + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + "]");
  }});

  private static Lazy<Map<String, Integer>> LOWERCASE_PHRASE_INDEXER = Lazy.of( () -> new HashMap<String, Integer>() {{
    long startTime = System.currentTimeMillis();
    System.err.print("Reading lowercase phrase indexer...");
    for (String line : IOUtils.readLines(vocabFile)) {
      String[] fields = line.split("\t");
      put(fields[1].toLowerCase(), Integer.parseInt(fields[0]));
    }
    System.err.println("done. [" + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + "]");
  }});

  public static Function<String, Integer> INDEXER = (String gloss) -> {
    boolean isLower = true;
    for (int i = 0; i < gloss.length(); ++i) {
      if (Character.isUpperCase(gloss.charAt(i))) {
        isLower = false;
      }
    }
    if (isLower) {
      Integer index = PHRASE_INDEXER.get().get(gloss);
      return index == null ? -1 : index;
    } else {
      String lower = gloss.toLowerCase();
      Integer index = LOWERCASE_PHRASE_INDEXER.get().get(lower);
      if (index != null) {
        Integer betterIndex = PHRASE_INDEXER.get().get(gloss);
        if (betterIndex != null) {
          return betterIndex;
        }
        betterIndex = PHRASE_INDEXER.get().get(lower);
        if (betterIndex != null) {
          return betterIndex;
        }
        return index;
      }
      return -1;
    }
  };

  public static Lazy<Map<Integer, String>> SURFACE_FORM = Lazy.of( () ->new HashMap<Integer, String>() {{
    long startTime = System.currentTimeMillis();
    System.err.print("Reading phrase indexer (index->gloss)...");
    for (String line : IOUtils.readLines(vocabFile)) {
      String[] fields = line.split("\t");
      put(Integer.parseInt(fields[0]), fields[1]);
    }
    System.err.println("done. [" + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + "]");
  }});

  public static Lazy<Map<Integer, Map<String, Integer>>> SENSE_INDEXER = Lazy.of(() -> Collections.unmodifiableMap(new HashMap<Integer, Map<String, Integer>>() {{
    long startTime = System.currentTimeMillis();
    System.err.print("Reading sense indexer...");
    for (String line : IOUtils.readLines(senseFile)) {
      String[] fields = line.split("\t");
      int word = Integer.parseInt(fields[0]);
      Map<String, Integer> definitions = this.get(word);
      if (definitions == null) {
        definitions = new HashMap<>();
        put(word, definitions);
      }
      definitions.put(fields[2], Integer.parseInt(fields[1]));
    }
    System.err.println("done. [" + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + "]");
  }}));

  public static Lazy<Map<Pair<String, Integer>, Synset[]>> SYNSETS = new Lazy<Map<Pair<String, Integer>, Synset[]>>() {
    @Override
    protected Map<Pair<String, Integer>, Synset[]> compute() {
      long startTime = System.currentTimeMillis();
      System.err.print("Reading synsets...");
      String file = wordnetFile;
      Map<Pair<String, Integer>, Synset[]> map = new HashMap<>();
      try {
        map = IOUtils.readObjectFromURLOrClasspathOrFileSystem(file);
      } catch (IOException e) {
        System.err.print("[no saved model; re-computing]...{");
        Interner<MinimalSynset> interner = new Interner<>();
        WordNetDatabase wordnet = WordNetDatabase.getFileInstance();
        int count = 0;
        int incr = PHRASE_INDEXER.get().size() / 100;
        for (String entry : PHRASE_INDEXER.get().keySet()) {
          if ( (++count % incr) == 0) {
            System.err.print("-");
          }
          for (SynsetType type : SynsetType.ALL_TYPES) {
            Synset[] synsets = wordnet.getSynsets(entry, type);
            if (synsets != null && synsets.length > 0) {
              for (int i = 0; i < synsets.length; ++i) {
                synsets[i] = interner.intern(new MinimalSynset(synsets[i]));
              }
              map.put(Pair.makePair(entry, type.getCode()), synsets);
            }
          }
          Synset[] allSynsets = wordnet.getSynsets(entry);
          if (allSynsets != null) {
            for (int i = 0; i < allSynsets.length; ++i) {
              allSynsets[i] = interner.intern(new MinimalSynset(allSynsets[i]));
            }
            map.put(Pair.makePair(entry, null), allSynsets);
          }
        }
        System.err.print("}...");
        try {
          IOUtils.writeObjectToFile(map, file);
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
      System.err.println("done. [" + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + "]");
      return map;
    }
  };

  static {
  }

  /**
   * A set of Semgrex patterns which indicate that the target can move in the meronymy relation.
   */
  public static Map<String, Set<SemgrexPattern>> MERONYM_TRIGGERS = Collections.unmodifiableMap(new HashMap<String,Set<SemgrexPattern>>() {{
    if (!containsKey("arrive")) { put("arrive", new HashSet<>()); }
    get("arrive").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:arrive} > /nmod:in/ {}=place)"));
    if (!containsKey("say")) { put("say", new HashSet<>()); }
    get("say").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:say} > /nmod:in/ {}=place)"));
    if (!containsKey("visit")) { put("visit", new HashSet<>()); }
    get("visit").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:visit} > dobj {}=place)"));
    if (!containsKey("be")) { put("be", new HashSet<>()); }
    get("be").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:be} > /nmod:in/ {}=place)"));
    if (!containsKey("bear")) { put("bear", new HashSet<>()); }
    get("bear").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:bear} > /nmod:in/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("bear")) { put("bear", new HashSet<>()); }
    get("bear").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:bear} > /nmod:in/ {}=place)"));
    if (!containsKey("tell")) { put("tell", new HashSet<>()); }
    get("tell").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:tell} > /nmod:in/ {}=place)"));
    if (!containsKey("win")) { put("win", new HashSet<>()); }
    get("win").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:win} > /nmod:in/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < /nmod:with/ ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("arrest")) { put("arrest", new HashSet<>()); }
    get("arrest").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:arrest} > /nmod:in/ {}=place)"));
    if (!containsKey("report")) { put("report", new HashSet<>()); }
    get("report").add(SemgrexPattern.compile("{}=person < /nmod:from/ ({lemma:report} > /nmod:from/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < /nmod:with/ ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < /nmod:in/ ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("report")) { put("report", new HashSet<>()); }
    get("report").add(SemgrexPattern.compile("{}=person < /nmod:from/ ({lemma:report} > /nmod:from/ {}=place)"));
    if (!containsKey("report")) { put("report", new HashSet<>()); }
    get("report").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:report} > /nmod:from/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < dobj ({lemma:meet} > rcmod {}=place)"));
    if (!containsKey("win")) { put("win", new HashSet<>()); }
    get("win").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:win} > /nmod:in/ {}=place)"));
    if (!containsKey("be")) { put("be", new HashSet<>()); }
    get("be").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:be} > /nmod:in/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < dobj ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("live")) { put("live", new HashSet<>()); }
    get("live").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:live} > /nmod:in/ {}=place)"));
    if (!containsKey("launch")) { put("launch", new HashSet<>()); }
    get("launch").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:launch} > /nmod:in/ {}=place)"));
    if (!containsKey("hold")) { put("hold", new HashSet<>()); }
    get("hold").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:hold} > /nmod:in/ {}=place)"));
    if (!containsKey("grow")) { put("grow", new HashSet<>()); }
    get("grow").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:grow} > /nmod:in/ {}=place)"));
    if (!containsKey("disappear")) { put("disappear", new HashSet<>()); }
    get("disappear").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:disappear} > /nmod:in/ {}=place)"));
    if (!containsKey("attend")) { put("attend", new HashSet<>()); }
    get("attend").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:attend} > /nmod:in/ {}=place)"));
    if (!containsKey("serve")) { put("serve", new HashSet<>()); }
    get("serve").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:serve} > rcmod {}=place)"));
    if (!containsKey("write")) { put("write", new HashSet<>()); }
    get("write").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:write} > /nmod:in/ {}=place)"));
    if (!containsKey("be")) { put("be", new HashSet<>()); }
    get("be").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:be} > /nmod:in/ {}=place)"));
    if (!containsKey("warn")) { put("warn", new HashSet<>()); }
    get("warn").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:warn} > /nmod:in/ {}=place)"));
    if (!containsKey("visit")) { put("visit", new HashSet<>()); }
    get("visit").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:visit} > /nmod:in/ {}=place)"));
    if (!containsKey("visit")) { put("visit", new HashSet<>()); }
    get("visit").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:visit} > /nmod:in/ {}=place)"));
    if (!containsKey("vanish")) { put("vanish", new HashSet<>()); }
    get("vanish").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:vanish} > /nmod:in/ {}=place)"));
    if (!containsKey("tell")) { put("tell", new HashSet<>()); }
    get("tell").add(SemgrexPattern.compile("{}=person < dobj ({lemma:tell} > /nmod:in/ {}=place)"));
    if (!containsKey("spend")) { put("spend", new HashSet<>()); }
    get("spend").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:spend} > /nmod:in/ {}=place)"));
    if (!containsKey("spend")) { put("spend", new HashSet<>()); }
    get("spend").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:spend} > /nmod:in/ {}=place)"));
    if (!containsKey("serve")) { put("serve", new HashSet<>()); }
    get("serve").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:serve} > /nmod:in/ {}=place)"));
    if (!containsKey("serve")) { put("serve", new HashSet<>()); }
    get("serve").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:serve} > /nmod:in/ {}=place)"));
    if (!containsKey("remain")) { put("remain", new HashSet<>()); }
    get("remain").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:remain} > /nmod:in/ {}=place)"));
    if (!containsKey("release")) { put("release", new HashSet<>()); }
    get("release").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:release} > /nmod:in/ {}=place)"));
    if (!containsKey("reach")) { put("reach", new HashSet<>()); }
    get("reach").add(SemgrexPattern.compile("{}=person < /nmod:in/ ({lemma:reach} > /nmod:in/ {}=place)"));
    if (!containsKey("preach")) { put("preach", new HashSet<>()); }
    get("preach").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:preach} > /nmod:in/ {}=place)"));
    if (!containsKey("open")) { put("open", new HashSet<>()); }
    get("open").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:open} > /nmod:in/ {}=place)"));
    if (!containsKey("mob")) { put("mob", new HashSet<>()); }
    get("mob").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:mob} > /nmod:in/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("meet")) { put("meet", new HashSet<>()); }
    get("meet").add(SemgrexPattern.compile("{}=person < dobj ({lemma:meet} > /nmod:in/ {}=place)"));
    if (!containsKey("live")) { put("live", new HashSet<>()); }
    get("live").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:live} > /nmod:in/ {}=place)"));
    if (!containsKey("kill")) { put("kill", new HashSet<>()); }
    get("kill").add(SemgrexPattern.compile("{}=person < /nmod:by/ ({lemma:kill} > /nmod:in/ {}=place)"));
    if (!containsKey("kill")) { put("kill", new HashSet<>()); }
    get("kill").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:kill} > /nmod:in/ {}=place)"));
    if (!containsKey("kidnap")) { put("kidnap", new HashSet<>()); }
    get("kidnap").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:kidnap} > /nmod:in/ {}=place)"));
    if (!containsKey("jail")) { put("jail", new HashSet<>()); }
    get("jail").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:jail} > /nmod:in/ {}=place)"));
    if (!containsKey("be")) { put("be", new HashSet<>()); }
    get("be").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:be} > /nmod:in/ {}=place)"));
    if (!containsKey("be")) { put("be", new HashSet<>()); }
    get("be").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:be} > /nmod:in/ {}=place)"));
    if (!containsKey("imprison")) { put("imprison", new HashSet<>()); }
    get("imprison").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:imprison} > /nmod:in/ {}=place)"));
    if (!containsKey("hospitalize")) { put("hospitalize", new HashSet<>()); }
    get("hospitalize").add(SemgrexPattern.compile("{}=person < /nmod:in/ ({lemma:hospitalize} > /nmod:in/ {}=place)"));
    if (!containsKey("hospitalise")) { put("hospitalise", new HashSet<>()); }
    get("hospitalise").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:hospitalise} > /nmod:in/ {}=place)"));
    if (!containsKey("hide")) { put("hide", new HashSet<>()); }
    get("hide").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:hide} > /nmod:in/ {}=place)"));
    if (!containsKey("hold")) { put("hold", new HashSet<>()); }
    get("hold").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:hold} > /nmod:in/ {}=place)"));
    if (!containsKey("hold")) { put("hold", new HashSet<>()); }
    get("hold").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:hold} > /nmod:in/ {}=place)"));
    if (!containsKey("grow")) { put("grow", new HashSet<>()); }
    get("grow").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:grow} > /nmod:in/ {}=place)"));
    if (!containsKey("give")) { put("give", new HashSet<>()); }
    get("give").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:give} > /nmod:in/ {}=place)"));
    if (!containsKey("find")) { put("find", new HashSet<>()); }
    get("find").add(SemgrexPattern.compile("{}=person < agent ({lemma:find} > /nmod:in/ {}=place)"));
    if (!containsKey("fight")) { put("fight", new HashSet<>()); }
    get("fight").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:fight} > /nmod:in/ {}=place)"));
    if (!containsKey("eliminate")) { put("eliminate", new HashSet<>()); }
    get("eliminate").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:eliminate} > /nmod:in/ {}=place)"));
    if (!containsKey("educate")) { put("educate", new HashSet<>()); }
    get("educate").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:educate} > /nmod:in/ {}=place)"));
    if (!containsKey("die")) { put("die", new HashSet<>()); }
    get("die").add(SemgrexPattern.compile("{}=person < pobj ({lemma:die} > /nmod:in/ {}=place)"));
    if (!containsKey("die")) { put("die", new HashSet<>()); }
    get("die").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:die} > /nmod:in/ {}=place)"));
    if (!containsKey("cremate")) { put("cremate", new HashSet<>()); }
    get("cremate").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:cremate} > /nmod:in/ {}=place)"));
    if (!containsKey("capture")) { put("capture", new HashSet<>()); }
    get("capture").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:capture} > /nmod:in/ {}=place)"));
    if (!containsKey("bury")) { put("bury", new HashSet<>()); }
    get("bury").add(SemgrexPattern.compile("{}=person < partmod ({lemma:bury} > /nmod:in/ {}=place)"));
    if (!containsKey("bury")) { put("bury", new HashSet<>()); }
    get("bury").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:bury} > /nmod:in/ {}=place)"));
    if (!containsKey("bear")) { put("bear", new HashSet<>()); }
    if (!containsKey("bear")) { put("bear", new HashSet<>()); }
    get("bear").add(SemgrexPattern.compile("{}=person < /nmod:in/ ({lemma:bear} > /nmod:in/ {}=place)"));
    if (!containsKey("beat")) { put("beat", new HashSet<>()); }
    get("beat").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:beat} > /nmod:in/ {}=place)"));
    if (!containsKey("apprehend")) { put("apprehend", new HashSet<>()); }
    get("apprehend").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:apprehend} > /nmod:in/ {}=place)"));
    if (!containsKey("admit")) { put("admit", new HashSet<>()); }
    get("admit").add(SemgrexPattern.compile("{}=person < nsubjpass ({lemma:admit} > /nmod:in/ {}=place)"));
    if (!containsKey("host")) { put("host", new HashSet<>()); }
    get("host").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:host} > /nmod:from/ {}=place)"));
    if (!containsKey("triumph")) { put("triumph", new HashSet<>()); }
    get("triumph").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:triumph} > /nmod:at/ {}=place)"));
    if (!containsKey("torture")) { put("torture", new HashSet<>()); }
    get("torture").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:torture} > /nmod:at/ {}=place)"));
    if (!containsKey("start")) { put("start", new HashSet<>()); }
    get("start").add(SemgrexPattern.compile("{}=person < nsubj ({lemma:start} > /nmod:at/ {}=place)"));
    if (!containsKey("injure")) { put("injure", new HashSet<>()); }
    get("injure").add(SemgrexPattern.compile("{}=person < rcmod ({lemma:injure} > /nmod:at/ {}=place)"));
  }});

  /**
   * Load the static resources (this really just amounts to loading the class via the classloader).
   */
  public static void load() { }

  /**
   * Test out loading the static resources. This function doesn't actually do anything interesting
   */
  public static void main(String[] args) {
    System.err.println("Static resources loaded.");
  }
}
