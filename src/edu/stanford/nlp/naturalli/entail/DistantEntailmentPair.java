package edu.stanford.nlp.naturalli.entail;

import edu.stanford.nlp.naturalli.ProcessPremise;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.Trilean;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
* A distantly supervised entailment pair.
*
* @author Gabor Angeli
*/
class DistantEntailmentPair {
  public final Trilean truth;
  public final Collection<Sentence> premises;
  public final Sentence originalPremise;
  public final Sentence conclusion;
  public final Optional<String> focus;
  public final Optional<Double> luceneScore;

  public DistantEntailmentPair(Trilean truth, Collection<Sentence> premises, Sentence conclusion, Optional<String> focus, Optional<Double> luceneScore) {
    this.truth = truth;
    this.premises = premises;
    this.conclusion = conclusion;
    this.focus = focus;
    this.luceneScore = luceneScore;
    Sentence longestSentence = null;
    int longestLength = -1;
    for (Sentence s : premises) {
      if (s.length() > longestLength) {
        longestSentence = s;
        longestLength = s.length();
      }
    }
    if (longestSentence == null) {
      throw new IllegalArgumentException("No premises for example!");
    }
    this.originalPremise = longestSentence;
  }

  public DistantEntailmentPair(Trilean truth, String premise, String conclusion, Optional<String> focus, Optional<Double> luceneScore, StanfordCoreNLP pipeline) {
    this(
        truth,
        ProcessPremise.forwardEntailments(premise, pipeline).stream().map(Sentence::new).limit(255).collect(Collectors.toList()),
        new Sentence(conclusion),
        focus, luceneScore);
  }

  public List<EntailmentPair> latentFactors() {
    return premises.stream().map(premise -> new EntailmentPair(truth, premise, conclusion, focus, luceneScore)).collect(Collectors.toList());
  }

  public EntailmentPair asEntailmentPair() {
    return new EntailmentPair(truth, originalPremise, conclusion, focus, luceneScore);
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public void serialize(final OutputStream cacheStream) {
    try {
      synchronized (cacheStream) {
        DataOutputStream out = new DataOutputStream(cacheStream);
        if (truth.isTrue()) {
          cacheStream.write(1);
        } else if (truth.isFalse()) {
          cacheStream.write(0);
        } else if (truth.isUnknown()) {
          cacheStream.write(2);
        } else {
          throw new IllegalStateException();
        }
        cacheStream.write(premises.size());
        for (Sentence premise : premises) {
          premise.serialize(cacheStream);
        }
        conclusion.serialize(cacheStream);
        out.writeUTF(focus.orElse(""));
        out.writeDouble(luceneScore.orElse(-1.0));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Stream<DistantEntailmentPair> deserialize(InputStream cacheStream) {
    return Stream.generate(() -> {
      try {
        synchronized (cacheStream) {
          DataInputStream in = new DataInputStream(cacheStream);
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
          int numPremises = cacheStream.read();
          List<Sentence> premises = new ArrayList<>();
          for (int i = 0; i < numPremises; ++i) {
            premises.add(Sentence.deserialize(cacheStream));
          }
          Sentence conclusion = Sentence.deserialize(cacheStream);
          // Read the optionals
          String focus = in.readUTF();
          double score = in.readDouble();
          // Create the datum
          return new DistantEntailmentPair(truth, premises, conclusion,
              "".equals(focus) ? Optional.empty() : Optional.of(focus),
              score < 0 ? Optional.empty() : Optional.of(score));
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }


}
