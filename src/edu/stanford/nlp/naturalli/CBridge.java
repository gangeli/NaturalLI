package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
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
    StaticResources.load();

    // Read input
    System.err.println(CBridge.class.getSimpleName() + " is ready for input");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (!"".equals(line)) {
        switch (line.charAt(0)) {
          case 'P':
            System.err.println("Annotating premise: '" + line.substring(1) + "'");
            for (SentenceFragment fragment : ProcessPremise.forwardEntailments(line.substring(1), premisePipeline)) {
              Pointer<String> debug = new Pointer<>();
              String annotated = ProcessQuery.conllDump(fragment.parseTree, debug, false, false);
              System.out.println(annotated);
              debug.dereference().ifPresent(System.err::println);
            }
            break;
          case 'Q':
            System.err.println("Annotating query: '" + line.substring(1) + "'");
            Pointer<String> debug = new Pointer<>();
            String annotated = ProcessQuery.annotate(line.substring(1), queryPipeline, debug, true);
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
      }
    }
  }
}
