package org.goobs.naturalli.scripts;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Write facts to a Lucene index, from stdin
 *
 * @author Gabor Angeli
 */
public class IndexFactsToLucene {

  private static final String FIELD_TEXT = "text";

  public static void main(String[] args) throws IOException {
    // Parse command line arguments
    if (args.length == 0) {
      System.err.println("Usage: IndexFactsToLucene <index_dir>");
      System.exit(1);
    }
    String indexDir = args[0];

    // Create Lucene Index
    Directory directory = FSDirectory.open(new File(indexDir));
    IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_47, new EnglishAnalyzer(Version.LUCENE_47));
    IndexWriter indexWriter = new IndexWriter(directory, config);

    // Read facts
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = reader.readLine()) != null) {
      Document doc = new Document();
      // Clip ID
      doc.add(new Field(FIELD_TEXT, line, new FieldType(){{
        setIndexed(true);
        setTokenized(true);
        setStored(true);
        setStoreTermVectors(false);
        setStoreTermVectorPositions(false);
        freeze();
      }}));
      indexWriter.addDocument(doc);
    }

    // Close
    indexWriter.commit();
    indexWriter.close();
  }
}
