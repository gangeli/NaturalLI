package edu.stanford.nlp.naturalli;

/**
 * A silly little class to denote a quantifier scope.
 *
 * @author Gabor Angeli
 */
public class QuantifierScope {
  public final int subjectBegin;
  public final int subjectEnd;
  public final int objectBegin;
  public final int objectEnd;

  public QuantifierScope(int subjectBegin, int subjectEnd) {
    this.subjectBegin = subjectBegin;
    this.subjectEnd = subjectEnd;
    this.objectBegin = subjectEnd;
    this.objectEnd = subjectEnd;
  }

  public QuantifierScope(int subjectBegin, int subjectEnd, int objectBegin, int objectEnd) {
    this.subjectBegin = subjectBegin;
    this.subjectEnd = subjectEnd;
    this.objectBegin = objectBegin;
    this.objectEnd = objectEnd;
  }

  public boolean isBinary() {
    return objectEnd > objectBegin;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QuantifierScope)) return false;
    QuantifierScope that = (QuantifierScope) o;
    return objectBegin == that.objectBegin && objectEnd == that.objectEnd && subjectBegin == that.subjectBegin && subjectEnd == that.subjectEnd;

  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = subjectBegin;
    result = 31 * result + subjectEnd;
    result = 31 * result + objectBegin;
    result = 31 * result + objectEnd;
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "QuantifierScope{" +
        "subjectBegin=" + subjectBegin +
        ", subjectEnd=" + subjectEnd +
        ", objectBegin=" + objectBegin +
        ", objectEnd=" + objectEnd +
        '}';
  }
}
