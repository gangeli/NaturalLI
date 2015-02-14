package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Pointer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * A bridge to the C code, so that multiple bits of functionality can be consolidated in a single
 * runtime.
 *
 * @author Gabor Angeli
 */
public class CBridge {
  public static void main(String[] args) throws IOException {
    // Create pipeline
    StanfordCoreNLP premisePipeline = ProcessPremise.constructPipeline();
    StanfordCoreNLP queryPipeline   = ProcessQuery.constructPipeline();
    // Load static resources
    StaticResources.load();

    // Read input
    System.err.println(CBridge.class.getSimpleName() + " is ready for input");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (!"".equals(line)) {
        try {
          switch (line.charAt(0)) {
            case 'P':
              String premise = line.substring(1);
              System.err.println("Annotating premise: '" + premise + "'");
              premise = QRewrite.FOR_PREMISE.rewriteGloss(premise);
              for (SentenceFragment fragment : ProcessPremise.forwardEntailments(premise, premisePipeline)) {
                SemanticGraph tree = fragment.parseTree;
                Pointer<String> debug = new Pointer<>();
                String annotated = ProcessQuery.conllDump(tree, debug, false, false);
                System.out.println(annotated);
                debug.dereference().ifPresent(System.err::println);
              }
              break;
            case 'Q':
              System.err.println("Annotating query: '" + line.substring(1) + "'");
              Pointer<String> debug = new Pointer<>();
              String annotated = ProcessQuery.annotate(QRewrite.FOR_QUERY, line.substring(1), queryPipeline, debug, true);
              debug.dereference().ifPresent(System.err::println);
              System.out.println(annotated);
              break;
            default:
              throw new RuntimeException("Line must begin with 'P' or 'Q'!");
          }
          System.out.println();  // A third newline to signify stop
          // Flush the streams
          System.out.flush();
          System.err.flush();
        } catch (Throwable t) {
          t.printStackTrace();
          System.out.println();
          System.out.println();
          System.out.flush();
          System.err.flush();
        }
      }
    }
  }
}
