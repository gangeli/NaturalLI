package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 *   Takes as input a document, and produces as output the same document, but with all mentions replaced
 *   with their canonical mention. This makes for boring reading, but much easier computation.
 *   This script is best used when processing the AI2 Q/A corpus.
 * </p>
 *
 * <p>
 *   This script uses constituency parses (which is very slow). This is mitigated somewhat by using the shift
 *   reduce parser.
 * </p>
 *
 * @author Gabor Angeli
 */
public class ExpandCoreference {

  private static class Document {
    public final StringBuilder text;
    public final List<String> ids;

    public Annotation ann;

    public Document(StringBuilder text, List<String> ids) {
      this.text = text;
      this.ids = ids;
    }

    public void register(String id, String text) {
      if (id != null) {
        ids.add(id);
      }
      this.text.append(text).append("\n");
    }
  }

  private static Pattern docid = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)\\.([0-9]+\\.[0-9]+\\.[0-9]+)");

  private static String getDocId(String rawID) {
    Matcher matcher = docid.matcher(rawID);
    if (matcher.matches()) {
      return matcher.group(1);
    } else {
      return null;
    }
  }


  public static void main(String[] args) throws IOException {
    long startTime = System.currentTimeMillis();
    StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties() {{
      setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, ner, dcoref");
      setProperty("parse.model", "edu/stanford/nlp/models/srparser/englishSR.ser.gz");
      setProperty("ssplit.newlineIsSentenceBreak", "always");
    }});
    System.err.println("Done loading CoreNLP: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));


    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    StringBuilder text = new StringBuilder();
    Document currentDocument = new Document(new StringBuilder(), new ArrayList<>());
    String currentDocId = null;
    List<Document> documents = new ArrayList<>();
    String line;
    int linesread = 0;
    while ( (line = reader.readLine()) != null) {
      if (line.contains("\t")) {
        String[] fields = line.split("\t");
        if (fields.length <= 1 || fields[1].trim().equals("")) {
          continue;
        }
        if (fields.length != 2) {
          throw new IllegalStateException("not two fields: " + line);
        }
        String docid = getDocId(fields[0]);
        if (currentDocId == null) {
          currentDocId = docid;
        }
        if (!currentDocId.equals(docid)) {
          documents.add(currentDocument);
          currentDocument = new Document(new StringBuilder(), new ArrayList<>());
        }
        currentDocument.register(fields[0], fields[1]);
      } else {
        currentDocument.register(null, line);
      }
      linesread += 1;
      if (linesread % 10000 == 0) {
        System.err.println("  Read " + (linesread / 1000) + "k lines (" + (documents.size() + 1) + " documents)");
      }
    }
    documents.add(currentDocument);

    System.err.println("Annotating...");
    int numDocsAnnotated = 0;
    for (Document doc : documents) {
      doc.ann = new Annotation(doc.text.toString());
      pipeline.annotate(doc.ann);
      numDocsAnnotated += 1;
      if (numDocsAnnotated % 1000 == 0) {
        System.err.println("  Annotated " + (numDocsAnnotated / 1000) + "k documents: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
      }
    }
    System.err.println("Annotation DONE: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
//    Annotation doc = new Annotation("George Bush went to China where he visited its prime minister.");

    System.err.println("Writing output...");
    for (Document document : documents) {
      Annotation doc = document.ann;
      List<String> ids = document.ids;
      Map<Integer, CorefChain> corefChains = doc.get(CorefCoreAnnotations.CorefChainAnnotation.class);
      List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
      for (CorefChain chain : corefChains.values()) {
        CorefChain.CorefMention canonical = chain.getRepresentativeMention();
        for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
          if (mention.endIndex - mention.startIndex == 1) {
            CoreLabel token = sentences.get(mention.sentNum - 1).get(CoreAnnotations.TokensAnnotation.class).get(mention.startIndex - 1);
            token.setWord(canonical.mentionSpan);
            token.setValue(canonical.mentionSpan);
            token.setLemma(canonical.mentionSpan);
          }
        }
      }

      Iterator<String> iter = ids.isEmpty() ? null : ids.iterator();
      for (CoreMap sentence : sentences) {
        if (iter != null) {
          System.out.print(iter.next());
          System.out.print("\t");
        }
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
          System.out.print(token.word());
          System.out.print(" ");
        }
        System.out.println();
      }
    }
    System.err.println("DONE: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
  }
}
