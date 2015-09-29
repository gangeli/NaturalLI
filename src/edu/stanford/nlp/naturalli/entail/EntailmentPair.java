package edu.stanford.nlp.naturalli.entail;

import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.wordAlignment.Data;
import edu.berkeley.nlp.wordAlignment.EMWordAligner;
import edu.berkeley.nlp.wordAlignment.Evaluator;
import edu.berkeley.nlp.wordAlignment.Main;
import edu.berkeley.nlp.wordAlignment.distortion.TreeWalkModel;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Trilean;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
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

  /**
   * An alignment from each conclusion token to a premise token
   */
  public final int[] conclusionToPremiseAlignment;

  /**
   * The phrase table for the entailment pair, if it is defined.
   * This must be initialized with a call to {@link EntailmentPair#candidatePhrases(int)}.
   */
  public final List<Pair<String, String>> phraseTable = new ArrayList<>();

  public EntailmentPair(Trilean truth, String premise, String conclusion, Optional<String> focus, Optional<Double> luceneScore) {
    this(truth, new Sentence(premise), new Sentence(conclusion), focus, luceneScore);
  }

  public EntailmentPair(Trilean truth, Sentence premise, Sentence conclusion, Optional<String> focus, Optional<Double> luceneScore) {
    this.truth = truth;
    this.premise = premise;
    this.conclusion = conclusion;
    this.conclusionToPremiseAlignment = new int[conclusion.length()];
    Arrays.fill(conclusionToPremiseAlignment, -1);
    this.focus = focus;
    this.luceneScore = luceneScore;
  }

  /**
   * All phrase table candidates from this alignment pair.
   *
   * @param maxLength The maximum length of a phrase.
   *
   * @return A collection of Span pairs for every pair of spans to add to the phrase table.
   */
  private List<Pair<Span, Span>> candidatePhraseSpans(int maxLength) {
    // Compute the premise -> conclusion alignments
    List<Set<Integer>> premiseToConclusionAlignment = new ArrayList<>(premise.length());
    for (int i = 0; i < premise.length(); ++i) {
      premiseToConclusionAlignment.add(new HashSet<>());
    }
    for (int i = 0; i < conclusion.length(); ++i) {
      if (conclusionToPremiseAlignment[i] >= 0) {
        premiseToConclusionAlignment.get(conclusionToPremiseAlignment[i]).add(i);
      }
    }

    // Compute the phrases
    List<Pair<Span, Span>> phrases = new ArrayList<>(32);
    for (int length = 2; length <= maxLength; ++length) {
      NEXT_SPAN: for (int start = 0; start <= conclusion.length() - length; ++start) {
        int end = start + length;
        assert end <= conclusion.length();

        // Compute the initial candidate span
        int min = 999;    // premise start candidate
        int max = -999;   // premise end candidate
        for (int i = start; i < end; ++i) {
          if (conclusionToPremiseAlignment[i] >= 0) {
            min = conclusionToPremiseAlignment[i] < min ? conclusionToPremiseAlignment[i] : min;
            max = conclusionToPremiseAlignment[i] >= max ? conclusionToPremiseAlignment[i] + 1 : max;
          }
        }

        // Check candidate span viability
        for (int premiseI = min; premiseI < max; ++premiseI) {
          Set<Integer> conclusionsForPremise = premiseToConclusionAlignment.get(premiseI);
          for (int conclusionI : conclusionsForPremise) {
            if (conclusionI < start || conclusionI >= end) {
              continue NEXT_SPAN;  // Alignment is not viable
            }
          }
        }

        // Try to extend the alignment over null tokens while possible
        int extremeMin = min;
        int extremeMax = max;
        while (extremeMin > 0 && premiseToConclusionAlignment.get(extremeMin - 1).isEmpty()) {
          extremeMin -= 1;
        }
        while (extremeMax < premiseToConclusionAlignment.size() && premiseToConclusionAlignment.get(extremeMax).isEmpty()) {
          extremeMax += 1;
        }

        // Add the alignment
        for (int premiseStart = extremeMin; premiseStart <= min; ++premiseStart) {
          for (int premiseEnd = extremeMax; premiseEnd >= max; --premiseEnd) {
            phrases.add(Pair.makePair(new Span(premiseStart, premiseEnd), new Span(start, end)));
          }
        }
      }
    }

    return phrases;
  }

  /**
   * Collect candidate phrase pairs, as lemma strings.
   *
   * @see EntailmentPair#candidatePhraseSpans(int)
   */
  private List<Pair<String, String>> candidatePhrases(int maxLength) {
    this.phraseTable.clear();
    this.phraseTable.addAll(candidatePhraseSpans(maxLength).stream().map(spanPair -> Pair.makePair(
        StringUtils.join(premise.lemmas().subList(spanPair.first.start(), spanPair.first.end()), " "),
        StringUtils.join(conclusion.lemmas().subList(spanPair.second.start(), spanPair.second.end()), " ")
    )).collect(Collectors.toList()));
    return this.phraseTable;
  }

  /**
   * Prune the phrase table so that only entries in the given key set are retained.
   *
   * @param validEntries The set of valid phrase table mappings to keep.
   */
  private void prunePhraseTable(Set<Pair<String, String>> validEntries) {
    Iterator<Pair<String, String>> iterator = this.phraseTable.iterator();
    while (iterator.hasNext()) {
      if (!validEntries.contains(iterator.next())) {
        iterator.remove();
      }
    }
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


  /**
   * Align a given set of entailment pairs, using the Berkeley aligner.
   * This sets the alignment on the entailment pairs in place.
   *
   * @param maxPhraseLength The maximum length of phrases to extract. Set to 1 to disable.
   * @param minPhraseCount The minimum number of times a phrase must be seen to be considered viable.
   * @param dataset The dataset to align.
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  @SafeVarargs
  public static void align(int maxPhraseLength, int minPhraseCount, List<EntailmentPair>... dataset) {
    try {
      // Set up the filesystem
      File outputDir = File.createTempFile("berkeleyaligner_output", ".dir");
      outputDir.delete();
      File trainDir = File.createTempFile("berkeleyaligner_train", ".dir");
      trainDir.delete();
      trainDir.mkdir();
      File configFile = File.createTempFile("berkeleyaligner", ".config");
      // Make sure we clean up
      configFile.deleteOnExit();
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override public void run() {
          for (File f : IOUtils.iterFilesRecursive(outputDir)) {
            f.delete();
          }
          outputDir.delete();
          for (File f : IOUtils.iterFilesRecursive(trainDir)) {
            f.delete();
          }
          trainDir.delete();
        }
      });

      // Write the config file
      IOUtils.writeStringToFile(
          IOUtils.slurpReader(
              IOUtils.getBufferedReaderFromClasspathOrFileSystem("edu/stanford/nlp/naturalli/entail/berkeley_aligner.conf")
            )
            .replace("TRAIN_DIR", trainDir.getPath())
            .replace("EXEC_DIR", outputDir.getPath()),
          configFile.getPath(),
          "UTF-8");

      // Prepare the data
      PrintWriter premises = new PrintWriter(new File(trainDir.getPath() + File.separator + "corpus.premise"));
      PrintWriter conclusions = new PrintWriter(new File(trainDir.getPath() + File.separator + "corpus.conclusion"));
      for (List<EntailmentPair> alignmentSuite : dataset) {
        for (EntailmentPair entailPair : alignmentSuite) {
          premises.println(StringUtils.join(entailPair.premise.lemmas(), " "));
          conclusions.println(StringUtils.join(entailPair.conclusion.lemmas(), " "));
        }
      }
      premises.close();
      conclusions.close();

      // Run the aligner
      // With apologies to everyone's better sensibilities...
      String[] args = new String[]{ "++" + configFile.getPath() };
      Main main = new Main();
      Data data = new Data();
      Field dataField = Main.class.getDeclaredField("data");
      dataField.setAccessible(true);
      dataField.set(null, data);
      Logger.setGlobalLogger(new Logger.FigLogger());
      //noinspection RedundantArrayCreation
      fig.exec.Execution.init(args, new Object[]{main, data, EMWordAligner.class, Evaluator.class, TreeWalkModel.class});
      main.run();

      // Read the results
      String[] alignments = IOUtils.slurpFile(outputDir.getPath() + File.separator + "training.align").split("\n");
      int alignI = 0;
      for (List<EntailmentPair> alignmentSuite : dataset) {
        for (EntailmentPair entailPair : alignmentSuite) {
          // Register the alignment
          for (String alignmentElement : alignments[alignI].split(" ")) {
            String[] premiseToConclusion = alignmentElement.split("-");
            int premiseI = Integer.parseInt(premiseToConclusion[0]);
            int conclusionI = Integer.parseInt(premiseToConclusion[1]);
            entailPair.conclusionToPremiseAlignment[conclusionI] = premiseI;
          }
          // Update the alignment counter
          alignI += 1;
        }
      }

      // Construct the candidate phrase table
      Counter<Pair<String, String>> jointCounts = new ClassicCounter<>();
      Counter<String> premiseCounts = new ClassicCounter<>();
      Counter<String> conclusionCounts = new ClassicCounter<>();
      for (List<EntailmentPair> alignmentSuite : dataset) {
        for (EntailmentPair entailPair : alignmentSuite) {
          List<Pair<String, String>> phrases = entailPair.candidatePhrases(maxPhraseLength);
          for (Pair<String, String> phrase : phrases) {
            jointCounts.incrementCount(phrase);
            premiseCounts.incrementCount(phrase.first);
            conclusionCounts.incrementCount(phrase.second);
          }
        }
      }

      // Score the phrase table
      Counter<Pair<String, String>> phraseScores = new ClassicCounter<>();
      for (Pair<String, String> candidatePhrase : jointCounts.keySet()) {
        if (jointCounts.getCount(candidatePhrase) >= minPhraseCount) {
          double cAB = Math.log(jointCounts.getCount(candidatePhrase));
          double cA = Math.log(premiseCounts.getCount(candidatePhrase));
          double cB = Math.log(conclusionCounts.getCount(candidatePhrase));
          double pmiSquared = cAB - cA - cB;
          if (pmiSquared > 0.0) {
            phraseScores.setCount(candidatePhrase, pmiSquared);
          }
        }
      }

      // Prune the alignments
      for (List<EntailmentPair> alignmentSuite : dataset) {
        for (EntailmentPair entailPair : alignmentSuite) {
          entailPair.prunePhraseTable(phraseScores.keySet());
        }
      }


    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
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
