package edu.stanford.nlp.naturalli;

/**
 * A silly little class to denote a quantifier scope.
 *
 * @author Gabor Angeli
 */
public class QuantifierSpec {
  public final int quantifierBegin;
  public final int quantifierEnd;
  public final int quantifierHead;
  public final int subjectBegin;
  public final int subjectEnd;
  public final int objectBegin;
  public final int objectEnd;

  public QuantifierSpec(int quantifierBegin, int quantifierEnd, int subjectBegin, int subjectEnd, int objectBegin, int objectEnd) {
    this.quantifierBegin = quantifierBegin;
    this.quantifierEnd = quantifierEnd;
    this.quantifierHead = quantifierEnd - 1;
    this.subjectBegin = subjectBegin;
    this.subjectEnd = subjectEnd;
    this.objectBegin = objectBegin;
    this.objectEnd = objectEnd;
  }

  public boolean isBinary() {
    return objectEnd > objectBegin;
  }

  public int quantifierLength() {
    return quantifierEnd - quantifierBegin;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QuantifierSpec)) return false;
    QuantifierSpec that = (QuantifierSpec) o;
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

  public static QuantifierSpec merge(QuantifierSpec x, QuantifierSpec y) {
    assert (x.quantifierBegin == y.quantifierBegin);
    assert (x.quantifierEnd == y.quantifierEnd);
    return new QuantifierSpec(
        Math.min(x.quantifierBegin, y.quantifierBegin),
        Math.min(x.quantifierEnd, y.quantifierEnd),
        Math.min(x.subjectBegin, y.subjectBegin),
        Math.max(x.subjectEnd, y.subjectEnd),
        Math.min(x.objectBegin, y.objectBegin),
        Math.max(x.objectEnd, y.objectEnd));
  }
}
