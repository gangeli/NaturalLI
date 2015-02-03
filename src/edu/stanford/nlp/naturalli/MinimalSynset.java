package edu.stanford.nlp.naturalli;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetException;
import edu.smu.tspell.wordnet.WordSense;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A minimal little Synset implementation, sufficient for the things I want to do with it
 * and easy to serialize.
 *
 * @author Gabor Angeli
 */
public class MinimalSynset implements Synset, Serializable {
  private static final long serialVersionUID = 42L;

  private final byte synsetType;
  private final String[] wordForms;
  private final String defn;
  private String[] splitDefn;
  private Map<String, Integer> indexOf = new HashMap<>();

  public MinimalSynset(byte synsetType, String[] wordForms, String defn) {
    this.synsetType = synsetType;
    this.wordForms = wordForms;
    this.defn = defn;
    this.splitDefn = defn.split("\\s+");
    for (int i = 0; i < wordForms.length; ++i) {
      indexOf.put(wordForms[i], i);
    }
  }

  public MinimalSynset(Synset synset) {
    this((byte) synset.getType().getCode(), synset.getWordForms(), synset.getDefinition());

  }

  @Override
  public SynsetType getType() {
    for (SynsetType cand : SynsetType.ALL_TYPES) {
      if (cand.getCode() == (int) synsetType) {
        return cand;
      }
    }
    throw new IllegalStateException();
  }

  @Override
  public String[] getWordForms() {
    return wordForms;
  }

  public int indexOf(String gloss) {
    if (indexOf == null) {
      indexOf = new HashMap<>();
      for (int i = 0; i < wordForms.length; ++i) {
        indexOf.put(wordForms[i], i);
      }
    }
    return indexOf.getOrDefault(gloss, wordForms.length);
  }

  @Override
  public WordSense[] getAntonyms(String s) throws WordNetException {
    throw new IllegalStateException("Method not implemented");
  }

  @Override
  public WordSense[] getDerivationallyRelatedForms(String s) throws WordNetException {
    throw new IllegalStateException("Method not implemented");
  }

  @Override
  public int getTagCount(String s) {
    throw new IllegalStateException("Method not implemented");
  }

  @Override
  public String getDefinition() {
    return defn;
  }

  public String[] splitDefinition() {
    if (splitDefn == null) {
      splitDefn = defn.split("\\s+");
    }
    return splitDefn;
  }

  @Override
  public String[] getUsageExamples() {
    throw new IllegalStateException("Method not implemented");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MinimalSynset)) return false;
    MinimalSynset that = (MinimalSynset) o;
    return synsetType == that.synsetType && defn.equals(that.defn) && Arrays.equals(wordForms, that.wordForms);
  }

  @Override
  public int hashCode() {
    int result = (int) synsetType;
    result = 31 * result + Arrays.hashCode(wordForms);
    result = 31 * result + defn.hashCode();
    return result;
  }
}
