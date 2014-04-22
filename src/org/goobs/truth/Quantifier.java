package org.goobs.truth;

import edu.stanford.nlp.Sentence;
import edu.stanford.nlp.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A collection of quantifiers. This is the exhaustive list of quantifiers our system knows about.
 *
 * @author Gabor Angeli
 */
public enum Quantifier {
  THE_LOT_OF("the lot of",          LogicalQuantifier.FORALL),
  ALL_OF("all of",                  LogicalQuantifier.FORALL),
  FOR_ALL("for all",                LogicalQuantifier.FORALL),
  FOR_EVERY("for every",            LogicalQuantifier.FORALL),
  FOR_EACH("for each",              LogicalQuantifier.FORALL),
  EVERY("every",                    LogicalQuantifier.FORALL),
  ALL("all",                        LogicalQuantifier.FORALL),
  ANY("any",                        LogicalQuantifier.FORALL),
  BOTH("both",                      LogicalQuantifier.FORALL),
  EACH("each",                      LogicalQuantifier.FORALL),

  MORE_THAN_N1("more than num_1",   LogicalQuantifier.MOST),
  MORE_THAN_N2("more than num_2",   LogicalQuantifier.MOST),
  MORE_THAN_N3("more than num_3",   LogicalQuantifier.MOST),
  MORE_THAN_N4("more than num_4",   LogicalQuantifier.MOST),
  MORE_THAN_N5("more than num_5",   LogicalQuantifier.MOST),
  MORE_THAN_N6("more than num_6",   LogicalQuantifier.MOST),
  MORE_THAN_N7("more than num_7",   LogicalQuantifier.MOST),
  MORE_THAN_N8("more than num_8",   LogicalQuantifier.MOST),
  MORE_THAN_N9("more than num_9",   LogicalQuantifier.MOST),
  A_LOT_OF("a lot of",              LogicalQuantifier.MOST),
  LOTS_OF("lot of",                 LogicalQuantifier.MOST),
  PLENTY_OF("plenty of",            LogicalQuantifier.MOST),
  HEAPS_OF("heap of",               LogicalQuantifier.MOST),
  A_LOAD_OF("a load of",            LogicalQuantifier.MOST),
  LOADS_OF("load of",               LogicalQuantifier.MOST),
  TONS_OF("ton of",                 LogicalQuantifier.MOST),
  MOST("most",                      LogicalQuantifier.MOST),
  ENOUGH("enough",                  LogicalQuantifier.MOST),
  SEVERAL("several",                LogicalQuantifier.MOST),

  LESS_THAN_N1("less than num_1",   LogicalQuantifier.EXISTS),
  LESS_THAN_N2("less than num_2",   LogicalQuantifier.EXISTS),
  LESS_THAN_N3("less than num_3",   LogicalQuantifier.EXISTS),
  LESS_THAN_N4("less than num_4",   LogicalQuantifier.EXISTS),
  LESS_THAN_N5("less than num_5",   LogicalQuantifier.EXISTS),
  LESS_THAN_N6("less than num_6",   LogicalQuantifier.EXISTS),
  LESS_THAN_N7("less than num_7",   LogicalQuantifier.EXISTS),
  LESS_THAN_N8("less than num_8",   LogicalQuantifier.EXISTS),
  LESS_THAN_N9("less than num_9",   LogicalQuantifier.EXISTS),
  SOME_OF("some of",                LogicalQuantifier.EXISTS),
  SOME("some",                      LogicalQuantifier.EXISTS),
  A_FEW("a few",                    LogicalQuantifier.EXISTS),
  THERE_BE("there be",              LogicalQuantifier.EXISTS),
  THERE_EXIST("there exist",        LogicalQuantifier.EXISTS),
  FEW("few",                        LogicalQuantifier.EXISTS),
  EITHER("either",                  LogicalQuantifier.EXISTS),
  A("a",                            LogicalQuantifier.EXISTS),
  THE("the",                          LogicalQuantifier.EXISTS),

  NO("no",                          LogicalQuantifier.NONE),
  NONE_OF("none of",                LogicalQuantifier.NONE),
  NEITHER("neither",                LogicalQuantifier.NONE),
  ;

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

  public final String[] surfaceForm;
  public final LogicalQuantifier closestMeaning;



  Quantifier(String surfaceForm, LogicalQuantifier closestMeaning) {
    this.surfaceForm = new Sentence(surfaceForm.split("\\s+")).lemma();
    this.closestMeaning = closestMeaning;
  }


  public static final Set<String> quantifierGlosses = Collections.unmodifiableSet(new HashSet<String>(){{
    for (Quantifier q : values()) {
      add(StringUtils.join(q.surfaceForm, " "));
    }
  }});
}
