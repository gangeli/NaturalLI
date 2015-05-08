package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.Trilean;

import java.io.*;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A candidate entailment pair. This corresponds to an un-featurized datum.
 */
class EntailmentPair {
  public final Trilean truth;
  public final Sentence premise;
  public final Sentence conclusion;
  public final Optional<String> focus;
  public final Optional<Double> luceneScore;

  public EntailmentPair(Trilean truth, String premise, String conclusion, Optional<String> focus, Optional<Double> luceneScore) {
    this.truth = truth;
    this.premise = new Sentence(premise);
    this.conclusion = new Sentence(conclusion);
    this.focus = focus;
    this.luceneScore = luceneScore;
  }

  public EntailmentPair(Trilean truth, Sentence premise, Sentence conclusion, Optional<String> focus, Optional<Double> luceneScore) {
    this.truth = truth;
    this.premise = premise;
    this.conclusion = conclusion;
    this.focus = focus;
    this.luceneScore = luceneScore;
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public void serialize(final OutputStream cacheStream) {
    try {
      synchronized (cacheStream) {
        DataOutputStream out = new DataOutputStream(cacheStream);
        if (truth.isTrue()) {
          out.write(1);
        } else if (truth.isFalse()) {
          out.write(0);
        } else if (truth.isUnknown()) {
          out.write(2);
        } else {
          throw new IllegalStateException();
        }
        premise.serialize(out);
        conclusion.serialize(out);
        out.writeUTF(focus.orElse(""));
        out.writeDouble(luceneScore.orElse(-1.0));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Stream<EntailmentPair> deserialize(InputStream cacheStream) {
    return Stream.generate(() -> {
      try {
        synchronized (cacheStream) {
          DataInputStream in = new DataInputStream(cacheStream);
          // Read the truth byte
          int truthByte;
          try {
            truthByte = in.read();
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
          Sentence premise = Sentence.deserialize(in);
          Sentence conclusion = Sentence.deserialize(in);
          // Read the optionals
          String focus = in.readUTF();
          double score = in.readDouble();
          // Create the datum
          return new EntailmentPair(truth, premise, conclusion,
              "".equals(focus) ? Optional.empty() : Optional.of(focus),
              score < 0 ? Optional.empty() : Optional.of(score));
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
