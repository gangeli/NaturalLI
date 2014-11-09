package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.function.Function;

/**
 * The entry point for annotating a sentence with its dependency parse and quantifiers.
 *
 * @author Gabor Angeli
 */
public class Preprocess {

  public static boolean PRODUCTION = false;


  public static String annotate(StanfordCoreNLP pipeline, Function<String, Integer> indexer, String line) {
    Annotation ann = new Annotation(line);
    pipeline.annotate(ann);
    SemanticGraph dependencies = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    DependencyTree<String> rawTree = new DependencyTree<>(dependencies, ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class));
    DependencyTree<Word> tree = rawTree.process(indexer);
    //noinspection PointlessBooleanExpression
    return tree.toString(!PRODUCTION);
  }

  public static StanfordCoreNLP constructPipeline() {
    Properties props = new Properties() {{
      setProperty("annotators", "tokenize,ssplit,pos,lemma,parse");
      setProperty("ssplit.isOneSentence", "true");
      setProperty("tokenize.class", "PTBTokenizer");
      setProperty("tokenize.language", "en");
      setProperty("naturalli.doPolarity", "false");
    }};
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.addAnnotator(new NaturalLogicAnnotator("naturalli", props));
    return pipeline;
  }

  public static void main(String[] args) throws IOException {
    // Set production flag
    if (args.length > 0) {
      PRODUCTION = Boolean.parseBoolean(args[0]);
    }

    // Create pipeline
    StanfordCoreNLP pipeline = constructPipeline();


    // Read input
    System.err.println("Preprocess ready for input");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.trim().equals("")) {
        System.err.println("Annotating '" + line + "'");
        String annotated = annotate(pipeline, StaticResources.INDEXER, line);
        System.err.println(annotated);
        System.out.println(annotated);
        System.out.flush();
      }
    }
  }
}

