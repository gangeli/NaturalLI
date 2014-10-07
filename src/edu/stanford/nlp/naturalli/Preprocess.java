package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;

/**
 * The entry point for annotating a sentence with its dependency parse and quantifiers.
 *
 * @author Gabor Angeli
 */
public class Preprocess {



  public static void annotate(StanfordCoreNLP pipeline, Function<String, Integer> indexer, String line) {
    Annotation ann = new Annotation(line);
    pipeline.annotate(ann);
    SemanticGraph dependencies = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    DependencyTree<String> rawTree = new DependencyTree<>(dependencies, ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class));
    System.out.println(rawTree);
    DependencyTree<Word> tree = rawTree.process(indexer);
    System.out.println(tree);
  }

  public static void main(String[] args) throws IOException {
    // Create pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
      setProperty("annotators", "tokenize,ssplit,pos,lemma,parse");
      setProperty("ssplit.isOneSentence", "true");
      setProperty("tokenize.class", "PTBTokenizer");
      setProperty("tokenize.language", "en");
    }});

    // Read dictionary
    Function<String, Integer> indexer = (word) -> {
      switch (word) {
        case "all": return 13;
        case "house": return 42;
        case "cat": return 43;
        case "have": return 44;
        case "tail": return 45;
        case "house cat": return 50;
        default: return -1;
      }
    };

    // Read input
    annotate(pipeline, indexer, "all house cats have tails");
//    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
//    String line = null;
//    while ((line = reader.readLine()) != null) {
//      annotate(pipeline, line);
//    }
  }
}

