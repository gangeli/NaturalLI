package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A helper to find the forward entailments for a given input query, and dump all of the entailed
 * sentences.
 *
 * @author Gabor Angeli
 */
public class ProcessPremise {

  protected static Collection<SentenceFragment> forwardEntailments(String input, StanfordCoreNLP pipeline) {
    // Run CoreNLP
    Annotation ann = new Annotation(input);
    pipeline.annotate(ann);
    // Collect the entailments
    List<SentenceFragment> entailments = new ArrayList<>();
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      Util.cleanTree(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
      entailments.add(new SentenceFragment(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class), false));
      entailments.addAll(sentence.get(NaturalLogicAnnotations.EntailedSentencesAnnotation.class).stream().collect(Collectors.toList()));
    }
    // Clean the entailments
    Iterator<SentenceFragment> iter = entailments.iterator();
    while (iter.hasNext()) {
      if (iter.next().words.isEmpty()) {
        iter.remove();
      }
    }
    // Return
    return entailments;
  }

  protected static StanfordCoreNLP constructPipeline() {
    Properties props = new Properties() {{
      setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
      setProperty("depparse.extradependencies", "ref_only_collapsed");
      setProperty("tokenize.class", "PTBTokenizer");
      setProperty("tokenize.language", "en");
      setProperty("naturalli.doPolarity", "false");
    }};
    return new StanfordCoreNLP(props);
  }

  public static void main(String[] args) throws IOException {
    // Create pipeline
    StanfordCoreNLP pipeline = constructPipeline();

    // Read input
    System.err.println(ProcessPremise.class.getSimpleName() + " is ready for input");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.trim().equals("")) {
        System.err.println("Annotating '" + line + "'");
        for (SentenceFragment fragment : forwardEntailments(line.trim(), pipeline)) {
          System.out.println(ProcessQuery.conllDump(fragment.parseTree));
          System.out.flush();
        }
      }
    }
  }

}
