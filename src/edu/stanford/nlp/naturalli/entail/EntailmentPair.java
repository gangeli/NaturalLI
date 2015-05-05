package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.Trilean;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Stream;

/**
 * A candidate entailment pair. This corresponds to an un-featurized datum.
 */
class EntailmentPair {
  public final Trilean truth;
  public final Sentence premise;
  public final Sentence conclusion;

  public EntailmentPair(Trilean truth, String premise, String conclusion) {
    this.truth = truth;
    this.premise = new Sentence(premise);
    this.conclusion = new Sentence(conclusion);
  }

  public EntailmentPair(Trilean truth, Sentence premise, Sentence conclusion) {
    this.truth = truth;
    this.premise = premise;
    this.conclusion = conclusion;
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public void serialize(final OutputStream cacheStream) {
    try {
      synchronized (cacheStream) {
        if (truth.isTrue()) {
          cacheStream.write(1);
        } else if (truth.isFalse()) {
          cacheStream.write(0);
        } else if (truth.isUnknown()) {
          cacheStream.write(2);
        } else {
          throw new IllegalStateException();
        }
        premise.serialize(cacheStream);
        conclusion.serialize(cacheStream);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Stream<EntailmentPair> deserialize(InputStream cacheStream) {
    return Stream.generate(() -> {
      try {
        synchronized (cacheStream) {
          // Read the truth byte
          int truthByte;
          try {
            truthByte = cacheStream.read();
          } catch (IOException e) {
            return null;  // Stream closed exception
          }
          // Convert the truth byte to a truth value
          Trilean truth;
          switch (truthByte) {
            case -1:
              cacheStream.close();
              return null;
            case 0:
              truth = Trilean.FALSE;
              break;
            case 1:
              truth = Trilean.TRUE;
              break;
            case 2:
              truth = Trilean.UNKNOWN;
              break;
            default:
              throw new IllegalStateException("Error parsing cache!");
          }
          // Read the sentences
          Sentence premise = Sentence.deserialize(cacheStream);
          Sentence conclusion = Sentence.deserialize(cacheStream);
          // Create the datum
          return new EntailmentPair(truth, premise, conclusion);
        }
      } catch(IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EntailmentPair)) return false;
    EntailmentPair that = (EntailmentPair) o;
    return conclusion.equals(that.conclusion) && premise.equals(that.premise) && truth.equals(that.truth);
  }

  @Override
  public int hashCode() {
    int result = truth.hashCode();
    result = 31 * result + premise.hashCode();
    result = 31 * result + conclusion.hashCode();
    return result;
  }
}
