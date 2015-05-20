package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.SentenceAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
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

  public static Collection<SentenceFragment> forwardEntailments(String input, StanfordCoreNLP pipeline) {
    // Run CoreNLP
    Annotation ann = new Annotation(input);
    pipeline.annotate(ann);
    // Collect the entailments
    List<SentenceFragment> entailments = new ArrayList<>();
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
//      entailments.add(new SentenceFragment(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class), false));
      entailments.addAll(sentence.get(NaturalLogicAnnotations.EntailedSentencesAnnotation.class).stream().collect(Collectors.toList()));
    }
    // In case nothing was produced
    if (entailments.isEmpty()) {
      entailments.add(new SentenceFragment(ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class), true, false));
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

  @SuppressWarnings({"unchecked", "UnusedDeclaration"})
  public static class QRewriteAnnotator extends SentenceAnnotator {
    public QRewriteAnnotator(String name, Properties props) {}
    @Override
    protected void doOneSentence(Annotation annotation, CoreMap sentence) {
      SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
      Util.cleanTree(tree);
      sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
          QRewrite.FOR_PREMISE.rewriteDependencies(tree));
    }
    @Override
    protected int nThreads() { return 1; }
    @Override
    protected long maxTime() { return -1; }
    @Override
    protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) { }
    @Override
    public Set<Requirement> requirementsSatisfied() { return Collections.EMPTY_SET; }
    @Override
    public Set<Requirement> requires() { return Collections.EMPTY_SET; }
  }

  public static StanfordCoreNLP constructPipeline() {
    return constructPipeline("parse");
  }

  public static StanfordCoreNLP constructPipeline(String parser) {
    Properties props = new Properties() {{
      setProperty("annotators", "tokenize,ssplit,pos,lemma,"+parser+",natlog,qrewrite,openie");
      setProperty("customAnnotatorClass.qrewrite","edu.stanford.nlp.naturalli.ProcessPremise$QRewriteAnnotator");
      setProperty("depparse.extradependencies", "ref_only_collapsed");
      setProperty("tokenize.class", "PTBTokenizer");
      setProperty("tokenize.language", "en");
      setProperty("naturalli.doPolarity", "false");
    }};
    return new StanfordCoreNLP(props, false);
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
        line = QRewrite.FOR_PREMISE.rewriteGloss(line);
        for (SentenceFragment fragment : forwardEntailments(line.trim(), pipeline)) {
          SemanticGraph tree = fragment.parseTree;
          tree = QRewrite.FOR_PREMISE.rewriteDependencies(tree);
          System.out.println(ProcessQuery.conllDump(tree));
          System.out.flush();
        }
      }
    }
  }

}
