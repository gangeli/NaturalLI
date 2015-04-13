package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
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
 *   The format of the input to this script is as follows:
 * </p>
 *
 * <ul>
 *   <li>Each line of input should be a sentence. By default, each sentence is a document</li>
 *   <li>A line can be an id followed by a tab followed by a sentence.</li>
 *   <li>This id should be seven numbers, separated by '.' or '-'. The first four numbers define the document, the last three
 *       are a specifier within the document.</li>
 * </ul>
 *
 * <p>
 *   The script outputs lines with the following (tab separated):
 * </p>
 *
 * <ul>
 *   <li>The input id, if it was provided, and '?.?.?.?.?.?.?' if it could not be determined.</li>
 *   <li>The tokenized sentence, with coreference expanded to the best of the script's ability.</li>
 * </ul>
 *
 * @author Gabor Angeli
 */
public class ExpandCoreference {

  private static class Document {
    public final String id;
    public final StringBuilder text;
    public final List<String> ids;

    public Annotation ann;

    public Document(String id, StringBuilder text, List<String> ids) {
      this.id = id;
      this.text = text;
      this.ids = ids;
    }

    public void register(String id, String text) {
      if (id != null) {
        ids.add(id);
      }
      this.text.append(text.replaceAll("\n", "")).append("\n");
    }
  }

  private static Pattern docid = Pattern.compile("([0-9]+[\\.\\-][0-9]+[\\.\\-][0-9]+[\\.\\-][0-9]+)[\\.\\-]([0-9]+[\\.\\-][0-9]+[\\.\\-][0-9]+)");
  private static TokenSequencePattern person = TokenSequencePattern.compile("[{ner:PERSON}]+");
  private static TokenSequencePattern plural = TokenSequencePattern.compile("([{word:/the|a/}])? [{tag:VBG; word:/[A-Z].*/} || {tag:JJ}]* [{tag:/NNP?S/}]+ ([{lemma:and}] [{tag:JJ}]* [{tag:/NNP?S/}]+)?");
  private static TokenSequencePattern noun_phrase = TokenSequencePattern.compile("([{word:/the|a/}])? [{tag:VBG; word:/[A-Z].*/} || {tag:JJ}]*  [{tag:/N.*[^S]/}]+");

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
      setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
      setProperty("ssplit.newlineIsSentenceBreak", "always");
      setProperty("ner.model", DefaultPaths.DEFAULT_NER_THREECLASS_MODEL);
      setProperty(NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY, "false");
      setProperty(NumberSequenceClassifier.USE_SUTIME_PROPERTY, "false");
    }});
    System.err.println("Done loading CoreNLP: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));


    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    Document currentDocument = null;
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
        String docid = getDocId(fields[0]);
        String text = StringUtils.join(Arrays.asList(fields).subList(1, fields.length), " ");
        if (docid == null) {
          throw new IllegalArgumentException("Invalid docid: " + fields[0]);
        }
        if (!docid.equals(currentDocId)) {
          if (currentDocId != null) {
            documents.add(currentDocument);
          }
          currentDocId = docid;
          currentDocument = new Document(currentDocId, new StringBuilder(), new ArrayList<>());
        }
        currentDocument.register(docid, text);
      } else {
        currentDocument = new Document(currentDocId, new StringBuilder(), new ArrayList<>());
        currentDocument.register(null, line);
        documents.add(currentDocument);
      }
      linesread += 1;
      if (linesread % 10000 == 0) {
        System.err.println("  Read " + (linesread / 1000) + "k lines (" + (documents.size() + 1) + " documents)");
      }
    }

    int numDocsAnnotated = 0;
    System.err.println("Annotating Documents");
    for (Document doc : documents) {
//      System.err.println("-----DOC BOUNDARY: " + doc.id + "-----");
      doc.ann = new Annotation(doc.text.toString().trim());
      pipeline.annotate(doc.ann);
      List<CoreLabel> tokens = doc.ann.get(CoreAnnotations.TokensAnnotation.class);
      List<CoreMap> sentences = doc.ann.get(CoreAnnotations.SentencesAnnotation.class);
      Iterator<String> iter = doc.ids.isEmpty() ? null : doc.ids.iterator();

      TokenSequenceMatcher personMatcher = person.matcher(tokens);
      String firstPerson = personMatcher.find() ? personMatcher.group() : null;
      TokenSequenceMatcher pluralMatcher = plural.matcher(tokens);
      String firstPlural = pluralMatcher.find() ? pluralMatcher.group().toLowerCase() : null;
      TokenSequenceMatcher nounMatcher = noun_phrase.matcher(tokens);
      String firstNoun = nounMatcher.find() ? nounMatcher.group().toLowerCase() : null;

      for (CoreMap sentence : sentences) {
        if (iter != null) {
          String id = (iter.hasNext() ? iter.next() : "?.?.?.?.?.?.?");
          System.out.print(id);
          System.out.print("\t");
        }

        tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        String gloss = sentence.get(CoreAnnotations.TextAnnotation.class).toLowerCase();
        personMatcher = person.matcher(tokens);
        String localPerson = personMatcher.find() ? personMatcher.group() : firstPerson;
        pluralMatcher = plural.matcher(tokens);
        String localPlural = pluralMatcher.find() ? pluralMatcher.group().toLowerCase() : null;
        nounMatcher = noun_phrase.matcher(tokens);
        String localNoun = nounMatcher.find() ? nounMatcher.group().toLowerCase() : null;

        boolean sentenceStart = true;
//        System.err.println(gloss);
//        System.err.println("  firstPerson: " + firstPerson);
//        System.err.println("  firstPlural: " + firstPlural);
//        System.err.println("  firstNoun: " + firstNoun);
//        System.err.println("  localPerson: " + localPerson);
//        System.err.println("  localPlural: " + localPlural);
//        System.err.println("  localNoun: " + localNoun);
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
          switch (token.word().toLowerCase()) {
            case "he":
              if (localPerson != null && gloss.indexOf("he") > gloss.indexOf(localPerson.toLowerCase())) {
                token.setWord(localPerson);
              } else {
                token.setWord(firstPerson == null ? token.word() : firstPerson);
              }
              break;
            case "she":
              if (localPerson != null && gloss.indexOf("she") > gloss.indexOf(localPerson.toLowerCase())) {
                token.setWord(localPerson);
              } else {
                token.setWord(firstPerson == null ? token.word() : firstPerson);
              }
              break;
            case "they":
              if (localPerson != null && gloss.indexOf("they") > gloss.indexOf(localPerson.toLowerCase())) {
                token.setWord(localPerson);
              } else if (firstPerson != null) {
                token.setWord(firstPerson);
              } else if (localPlural != null && gloss.indexOf("they") > gloss.indexOf(localPlural)) {
                  token.setWord(localPlural);
              } else {
                token.setWord(firstPlural == null ? token.word() : firstPlural);
              }
              break;
            case "it":
              if (!gloss.contains(" that ") && !gloss.contains(" because ") && !gloss.contains(" so ")) {
                if (localNoun != null && gloss.indexOf("it") < gloss.indexOf(localNoun)) {
                  token.setWord(localNoun);
                } else {
                  token.setWord(firstNoun == null ? token.word() : token.word());
                }
              }
              break;
          }
          System.out.print(token.word());
          System.out.print(" ");
          sentenceStart = false;
        }
        System.out.println();
      }
      System.out.flush();

      numDocsAnnotated += 1;
      if (numDocsAnnotated % 1000 == 0) {
        System.err.println("  Annotated " + (numDocsAnnotated / 1000) + "k documents: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
      }
    }

    System.out.flush();
    System.err.println("DONE: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
  }


  /*
  public static void main(String[] args) throws IOException {
    long startTime = System.currentTimeMillis();
    StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties() {{
      setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, ner, dcoref");
      setProperty("parse.model", "edu/stanford/nlp/models/srparser/englishSR.ser.gz");
      setProperty("ssplit.newlineIsSentenceBreak", "always");
    }});
    System.err.println("Done loading CoreNLP: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));


    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
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
      doc.ann = new Annotation(doc.text.toString().trim());
      pipeline.annotate(doc.ann);

      Map<Integer, CorefChain> corefChains = doc.ann.get(CorefCoreAnnotations.CorefChainAnnotation.class);
      List<CoreMap> sentences = doc.ann.get(CoreAnnotations.SentencesAnnotation.class);
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

      Iterator<String> iter = doc.ids.isEmpty() ? null : doc.ids.iterator();
      for (CoreMap sentence : sentences) {
        if (iter != null) {
          String id = (iter.hasNext() ? iter.next() : "?.?.?.?.?.?.?");
          System.out.print(id);
          System.out.print("\t");
        }
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
          System.out.print(token.word());
          System.out.print(" ");
        }
        System.out.println();
      }
      System.out.flush();

      numDocsAnnotated += 1;
      if (numDocsAnnotated % 1000 == 0) {
        System.err.println("  Annotated " + (numDocsAnnotated / 1000) + "k documents: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
      }
    }
    System.err.println("Annotation DONE: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));

    System.err.println("DONE: " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime));
  }
  */
}
