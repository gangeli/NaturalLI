package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.util.Pair;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A collection of quantifiers. This is the exhaustive list of quantifiers our system knows about.
 *
 * @author Gabor Angeli
 */
public enum Quantifier {
  ALL("all", "anti-additive", "multiplicative"),
  NO("no", "anti-additive", "anti-additive"),

  UNARY_NO("no", "anti-additive"),
  // TODO(gabor)
  ;

  public static final Set<String> GLOSSES = Collections.unmodifiableSet(new HashSet<String>() {{
    for (Quantifier q : Quantifier.values()) {
      add(q.surfaceForm);
    }
  }});

  public final String surfaceForm;
  public final Monotonicity subjMono;
  public final MonotonicityType subjType;
  public final Monotonicity objMono;
  public final MonotonicityType objType;

  Quantifier(String surfaceForm, String subjMono, String objMono) {
    this.surfaceForm = surfaceForm;
    Pair<Monotonicity, MonotonicityType> subj = monoFromString(subjMono);
    this.subjMono = subj.first;
    this.subjType = subj.second;
    Pair<Monotonicity, MonotonicityType> obj = monoFromString(objMono);
    this.objMono = obj.first;
    this.objType = obj.second;
  }

  Quantifier(String surfaceForm, String subjMono) {
    this.surfaceForm = surfaceForm;
    Pair<Monotonicity, MonotonicityType> subj = monoFromString(subjMono);
    this.subjMono = subj.first;
    this.subjType = subj.second;
    this.objMono = Monotonicity.INVALID;
    this.objType = MonotonicityType.NONE;
  }

  public boolean isUnary() {
    return objMono == Monotonicity.INVALID;
  }

  public static Pair<Monotonicity, MonotonicityType> monoFromString(String mono) {
    switch (mono) {
      case "nonmonotone": return Pair.makePair(Monotonicity.NONMONOTONE, MonotonicityType.NONE);
      case "additive": return Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.ADDITIVE);
      case "multiplicative": return Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.MULTIPLICATIVE);
      case "additive-multiplicative": return Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.BOTH);
      case "anti-additive": return Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.ADDITIVE);
      case "anti-multiplicative": return Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.MULTIPLICATIVE);
      case "anti-additive-multiplicative": return Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.BOTH);
      default: throw new IllegalArgumentException("Unknown monotonicity: " + mono);
    }
  }

  public static String monotonicitySignature(Monotonicity mono, MonotonicityType type) {
    switch (mono) {
      case MONOTONE:
        switch (type) {
          case NONE: return "nonmonotone";
          case ADDITIVE: return "additive";
          case MULTIPLICATIVE: return "multiplicative";
          case BOTH: return "additive-multiplicative";
        }
      case ANTITONE:
        switch (type) {
          case NONE: return "nonmonotone";
          case ADDITIVE: return "anti-additive";
          case MULTIPLICATIVE: return "anti-multiplicative";
          case BOTH: return "anti-additive-multiplicative";
        }
      case NONMONOTONE: return "nonmonotone";
    }
    throw new IllegalStateException("Unhandled case: " + mono + " and " + type);
  }
}
