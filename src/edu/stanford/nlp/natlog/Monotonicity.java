package edu.stanford.nlp.natlog;

/**
* An enumeration of possible monotonicity types.
*
* @author Gabor Angeli
*/
public enum Monotonicity {
  UP,
  DOWN,
  NON;

  public static Monotonicity DEFAULT = UP;
  public static Monotonicity QUANTIFIERLESS = NON;

  public Monotonicity compose(Monotonicity other) {
    switch (this) {
      case UP:
        switch (other) {
          case UP:   return UP;
          case DOWN: return DOWN;
          case NON:  return NON;
          default: throw new IllegalStateException();
        }
      case DOWN:
        switch (other) {
          case UP:   return DOWN;
          case DOWN: return UP;
          case NON:  return NON;
          default: throw new IllegalStateException();
        }
      case NON: return NON;
      default: throw new IllegalStateException();
    }
  }
}
