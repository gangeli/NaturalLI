package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test the QRewrite stages.
 *
 * @author Gabor Angeli
 */
public class QRewriteTest {

  protected CoreLabel mkWord(String gloss, int index) {
    CoreLabel w = new CoreLabel();
    w.setWord(gloss);
    w.setValue(gloss);
    if (index >= 0) {
      w.setIndex(index);
    }
    return w;
  }

  protected SemanticGraph mkTree(String conll) {
    List<CoreLabel> sentence = new ArrayList<>();
    SemanticGraph tree = new SemanticGraph();
    for (String line : conll.split("\n")) {
      if (line.trim().equals("")) {
        continue;
      }
      String[] fields = line.trim().split("\\s+");
      int index = Integer.parseInt(fields[0]);
      String word = fields[1];
      CoreLabel label = mkWord(word, index);
      sentence.add(label);
      if (fields[2].equals("0")) {
        tree.addRoot(new IndexedWord(label));
      } else {
        tree.addVertex(new IndexedWord(label));
      }
      if (fields.length > 4) {
        label.setTag(fields[4]);
      }
      if (fields.length > 5) {
        label.setNER(fields[5]);
      }
      if (fields.length > 6) {
        label.setLemma(fields[6]);
      }
      if (fields.length > 7) {
        label.set(NaturalLogicAnnotations.OperatorAnnotation.class, new OperatorSpec(Operator.valueOf(fields[7]), 0, 0, 0, 0, 0, 0));
      }
    }
    int i = 0;
    for (String line : conll.split("\n")) {
      if (line.trim().equals("")) {
        continue;
      }
      String[] fields = line.trim().split("\\s+");
      int parent = Integer.parseInt(fields[2]);
      String reln = fields[3];
      if (parent > 0) {
        tree.addEdge(
            new IndexedWord(sentence.get(parent - 1)),
            new IndexedWord(sentence.get(i)),
            new GrammaticalRelation(GrammaticalRelation.Language.English, reln, null, null),
            1.0, false
        );
      }
      i += 1;
    }
    return tree;
  }



  @Test
  public void testThereBe() {
    assertEquals("Some Italians sing opera", QRewrite.rewriteThereBe("there are Italians who sing opera"));
    assertEquals("Some Italians sing opera", QRewrite.rewriteThereBe("there were Italians who sing opera"));
    assertEquals("An Italian sings opera", QRewrite.rewriteThereBe("there is an Italian who sings opera"));
    assertEquals("Many Italians sing opera", QRewrite.rewriteThereBe("there are many Italians who sing opera"));
  }

  @Test
  public void testRootedQuantifier() {
    SemanticGraph input = mkTree(
        "1\tthere\t2\texpl\n" +
        "2\tare\t0\troot\tVB\tO\tbe\tTHERE_BE\n" +
        "3\tfat\t4\tamod\n" +
        "4\tcats\t2\tnsubj\n"
    );
    SemanticGraph expected = mkTree(
        "1\tthere\t2\texpl\n" +
        "2\tare\t4\top\tVB\tO\tbe\tTHERE_BE\n" +
        "3\tfat\t4\tamod\n" +
        "4\tcats\t0\troot\n"
    );
    assertEquals(expected, QRewrite.rewriteRootedQuantifier(input));
  }

  @Test
  public void testALot() {
    SemanticGraph input = mkTree(
        "1\tcats\t2\tnsubj\n" +
        "2\thave\t0\troot\n" +
        "3\ta\t4\tdet\n" +
        "4\tlot\t2\tdobj\n" +
        "5\tenergy\t4\tprep_of\n"
    );
    SemanticGraph expected = mkTree(
        "1\tcats\t2\tnsubj\n" +
        "2\thave\t0\troot\n" +
        "3\ta\t4\tdet\n" +
        "4\tlot\t5\tamod\n" +
        "5\tenergy\t2\tdobj\n"
    );
    assertEquals(expected, QRewrite.rewriteMods(input));
  }

  @Test
  public void testNOfThe() {
    SemanticGraph input = mkTree(
        "1\tTwo\t4\tnsubj\tCD\n" +
        "2\tthe\t3\tdet\n" +
        "3\tcats\t1\tprep_of\n" +
        "4\thave\t0\troot\n" +
        "5\ttails\t4\tdobj\n"
    );
    SemanticGraph expected = mkTree(
        "1\tTwo\t3\tquantmod\tCD\n" +
        "2\tthe\t3\tdet\n" +
        "3\tcats\t4\tnsubj\n" +
        "4\thave\t0\troot\n" +
        "5\ttails\t4\tdobj\n"
    );
    assertEquals(expected, QRewrite.rewriteMods(input));
  }

  @Test
  public void testDropDet() {
    SemanticGraph input = mkTree(
        "1\ta\t2\tdet\n" +
        "2\tcat\t3\tnsubj\n" +
        "3\thas\t0\troot\n" +
        "4\ta\t5\tdet\n" +
        "5\ttail\t3\tdobj\n"
    );
    SemanticGraph expected = mkTree(
        "2\tcat\t2\tnsubj\n" +
        "3\thas\t0\troot\n" +
        "5\ttail\t2\tdobj\n"
    );
    SemanticGraph actual = QRewrite.rewriteDropDeterminers(input);
    assertEquals(expected, actual);
  }
}
