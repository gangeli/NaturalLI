package org.goobs.truth;

import edu.stanford.nlp.Sentence;
import edu.stanford.nlp.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A collection of quantifiers. This is the exhaustive list of quantifiers our system knows about.
 *
 * @author Gabor Angeli
 */
public enum Quantifier {
  ALL("all",                        LogicalQuantifier.FORALL, TriggerType.DEFAULT),
  ANY("any",                        LogicalQuantifier.FORALL, TriggerType.DEFAULT),
  BOTH("both",                      LogicalQuantifier.FORALL, TriggerType.DEFAULT),
  EACH("each",                      LogicalQuantifier.FORALL, TriggerType.DEFAULT),
  EVERY("every",                    LogicalQuantifier.FORALL, TriggerType.DEFAULT),
  THE_LOT_OF("the lot of",          LogicalQuantifier.FORALL, TriggerType.DONT_MARK),
  ALL_OF("all of",                  LogicalQuantifier.FORALL, TriggerType.DONT_MARK),
  FOR_ALL("for all",                LogicalQuantifier.FORALL, TriggerType.DONT_MARK),
  FOR_EVERY("for every",            LogicalQuantifier.FORALL, TriggerType.DONT_MARK),
  FOR_EACH("for each",              LogicalQuantifier.FORALL, TriggerType.DONT_MARK),

  MOST("most",                      LogicalQuantifier.MOST, TriggerType.DEFAULT),
  ENOUGH("enough",                  LogicalQuantifier.MOST, TriggerType.DEFAULT),
  SEVERAL("several",                LogicalQuantifier.MOST, TriggerType.DEFAULT),
  MORE_THAN("more than",            LogicalQuantifier.MOST, TriggerType.DONT_MARK),
  A_LOT_OF("a lot of",              LogicalQuantifier.MOST, TriggerType.DONT_MARK),
  LOTS_OF("lot of",                 LogicalQuantifier.MOST, TriggerType.DONT_MARK),
  PLENTY_OF("plenty of",            LogicalQuantifier.MOST, TriggerType.DONT_MARK),
  HEAPS_OF("heap of",               LogicalQuantifier.MOST, TriggerType.DONT_MARK),
  A_LOAD_OF("a load of",            LogicalQuantifier.MOST, TriggerType.DONT_MARK),
  LOADS_OF("load of",               LogicalQuantifier.MOST, TriggerType.DONT_MARK),
  TONS_OF("ton of",                 LogicalQuantifier.MOST, TriggerType.DONT_MARK),

  SOME("some",                      LogicalQuantifier.EXISTS, TriggerType.DEFAULT),
  EITHER("either",                  LogicalQuantifier.EXISTS, TriggerType.DEFAULT),
  A("a",                            LogicalQuantifier.EXISTS, TriggerType.DEFAULT),
  THE("the",                        LogicalQuantifier.EXISTS, TriggerType.DEFAULT),
  LESS_THAN("less than",            LogicalQuantifier.EXISTS, TriggerType.DONT_MARK),
  SOME_OF("some of",                LogicalQuantifier.EXISTS, TriggerType.DONT_MARK),
  A_FEW("a few",                    LogicalQuantifier.EXISTS, TriggerType.DONT_MARK),
  THERE_BE("there be",              LogicalQuantifier.EXISTS, TriggerType.DONT_MARK),
  THERE_EXIST("there exist",        LogicalQuantifier.EXISTS, TriggerType.DONT_MARK),
  THERE_BE_SOME("there be some",    LogicalQuantifier.EXISTS, TriggerType.DONT_MARK),
  THERE_BE_FEW("there be few",      LogicalQuantifier.EXISTS, TriggerType.DONT_MARK),

  NO("no",                          LogicalQuantifier.NONE, TriggerType.NO),
  NOT("not",                        LogicalQuantifier.NONE, TriggerType.UNARY_NOT),
  NT("n't",                         LogicalQuantifier.NONE, TriggerType.UNARY_NOT),
  WITHOUT("without",                LogicalQuantifier.NONE, TriggerType.UNARY_NOT_IN),
  NEITHER("neither",                LogicalQuantifier.NONE, TriggerType.DEFAULT),
  FEW("few",                        LogicalQuantifier.NONE, TriggerType.DEFAULT),
  NONE_OF("none of",                LogicalQuantifier.NONE, TriggerType.DONT_MARK),
  AT_MOST("at most",                LogicalQuantifier.NONE, TriggerType.DONT_MARK),
  ;

  /** The closest pure logical meaning of the quantifier */
  public static enum LogicalQuantifier {
    FORALL(3),
    MOST(2),
    EXISTS(1),
    NONE(-3);

    public final int partialOrder;

    LogicalQuantifier(int partialOrder) {
      this.partialOrder = partialOrder;
    }
  }

  /** The trigger type for recognizing this quantifier in a constituency parse */
  public static enum TriggerType {
    UNARY_NOT,
    UNARY_NOT_IN,
    NO,
    DEFAULT,
    DONT_MARK,
  }

  public final String[] literalSurfaceForm;
  public final String[] surfaceForm;
  public final LogicalQuantifier closestMeaning;
  public final TriggerType trigger;

  Quantifier(String surfaceForm, LogicalQuantifier closestMeaning, TriggerType trigger) {
    this.literalSurfaceForm = surfaceForm.split("\\s+");
    this.surfaceForm = new Sentence(this.literalSurfaceForm).lemma();
    this.closestMeaning = closestMeaning;
    this.trigger = trigger;
  }

  /** If true, this quantifier behaves as a binary quantifier (e.g., 'all') rather than a unary one (e.g., 'not') */
  public boolean isBinary() {
    return trigger != TriggerType.UNARY_NOT && trigger != TriggerType.UNARY_NOT_IN;
  }


  public static final Set<String> quantifierGlosses = Collections.unmodifiableSet(new HashSet<String>(){{
    for (Quantifier q : values()) {
      add(StringUtils.join(q.surfaceForm, " "));
      add(StringUtils.join(q.literalSurfaceForm, " "));
    }
  }});

  public static Quantifier get(String gloss) {
    for (Quantifier q : values()) {
      if (StringUtils.join(q.surfaceForm, " ").equals(gloss)) {
        return q;
      }
    }
    throw new NoSuchElementException(gloss);
  }
}
