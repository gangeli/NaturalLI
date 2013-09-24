package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection

import edu.stanford.nlp.io.IOUtils._
import edu.stanford.nlp.util.HashIndex
import edu.stanford.nlp.util.logging.Redwood.Util._

import edu.smu.tspell.wordnet._

import org.goobs.sim._

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._
import org.goobs.truth.EdgeType._

/**
 *
 * The entry point for constructing the nodes of a graph, indexed to a compact
 * form.
 *
 * @author Gabor Angeli
 */

object CreateIndexers {
  
  val wordIndexer = new HashIndex[String]

  // ( begin, end, log(P(hyper) / P(hypo)) )
  val wordnetGraphUp = new scala.collection.mutable.ArrayBuffer[(Int, Int, Double)]
  val wordnetGraphDown = new scala.collection.mutable.ArrayBuffer[(Int, Int, Double)]
  
  // ( begin, end, [1.0] )
  val wordnetNounAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val wordnetVerbAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val wordnetAdjAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val wordnetAdvAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val wordnetAdjPertainym = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val wordnetAdvPertainym = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val wordnetAdjSimilar = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  
  // ( begin, end, acos(cos_similarity) / π )
  val angleNearestNeighbors = new scala.collection.mutable.ArrayBuffer[(Int, Int, Double)]
  
  // ( begin, end, [1.0] )
  val freebaseGraphUp = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val freebaseGraphDown = new scala.collection.mutable.ArrayBuffer[(Int, Int)]

  def main(args:Array[String]) = {
    Props.exec(() => {
      wordIndexer.indexOf("", true)
      val wordnet = Ontology.load(Props.SCRIPT_WORDNET_PATH)
      withConnection{ (psql:Connection) =>

        // Part 1: Read Wordnet
        // note: we are prohibiting ROOT as a valid node to traverse
        forceTrack("Reading WordNet")
        for ((phrase, nodes) <- wordnet.ontology) {
          val phraseAsString:String = phrase.mkString(" ")
          val source:Int = wordIndexer.indexOf(phraseAsString, true)
          for (node <- nodes;
               hyper <- node.hypernyms) {
            hyper match {
              case (hyperNode:Ontology.RealNode) =>
                val edgeWeight:Double = scala.math.log( hyperNode.count / node.count )
                // is_a ontology
                for (hyperWord <- hyperNode.synset.getWordForms) {
                  val hyperInt:Int = wordIndexer.indexOf(hyperWord, true)
                  wordnetGraphUp.append( (source, hyperInt, edgeWeight) )
                  wordnetGraphDown.append( (hyperInt, source, edgeWeight) )
                }
                // other wordnet relations
                hyperNode.synset match {
                  case (as:NounSynset) =>
                    for (antonym <- as.getAntonyms(phraseAsString)) {
                      val sink:Int = wordIndexer.indexOf(antonym.getWordForm, true)
                      wordnetVerbAntonym.append( (source, sink) )
                    }
                  case (as:VerbSynset) =>
                    for (antonym <- as.getAntonyms(phraseAsString)) {
                      val sink:Int = wordIndexer.indexOf(antonym.getWordForm, true)
                      wordnetNounAntonym.append( (source, sink) )
                    }
                  case (as:AdjectiveSynset) =>
                    for (related <- as.getSimilar;
                         wordForm <- related.getWordForms) {
                      val sink:Int = wordIndexer.indexOf(wordForm, true)
                      wordnetAdjSimilar.append( (source, sink) )
                    }
                    for (pertainym <- as.getPertainyms(phraseAsString)) {
                      val sink:Int = wordIndexer.indexOf(pertainym.getWordForm, true)
                      wordnetAdjPertainym.append( (source, sink) )
                    }
                    for (antonym <- as.getAntonyms(phraseAsString)) {
                      val sink:Int = wordIndexer.indexOf(antonym.getWordForm, true)
                      wordnetAdjAntonym.append( (source, sink) )
                    }
                  case (as:AdverbSynset) =>
                    for (pertainym <- as.getPertainyms(phraseAsString)) {
                      val sink:Int = wordIndexer.indexOf(pertainym.getWordForm, true)
                      wordnetAdvPertainym.append( (source, sink) )
                    }
                    for (antonym <- as.getAntonyms(phraseAsString)) {
                      val sink:Int = wordIndexer.indexOf(antonym.getWordForm, true)
                      wordnetAdvAntonym.append( (source, sink) )
                    }
                  case _ =>
                }
              case _ =>
                debug("sub-root node: " + node + " [" + hyper.getClass + "]")
            }
            
          }
        }
        endTrack("Reading WordNet")

        // Part 2: Read distsim
        forceTrack("Reading Nearest Neighbors")
        for (line <- Source.fromFile(Props.SCRIPT_DISTSIM_COS, "UTF-8").getLines) {
          val fields = line.split("\t")
          val source:Int = wordIndexer.indexOf(fields(0), true)
          for (i <- 1 until fields.length) {
            val scoreAndGloss = fields(i).split(" ")
            assert(scoreAndGloss.length == 2)
            val sink:Int = wordIndexer.indexOf(scoreAndGloss(1), true)
            val angle:Double = scala.math.acos( scoreAndGloss(0).toDouble ) / scala.math.Pi
            angleNearestNeighbors.append( (source, sink, angle) )
          }
        }
        endTrack("Reading Nearest Neighbors")

        // Part 3: Read freebase
        forceTrack("Reading Freebase")
        val fbNames = new scala.collection.mutable.HashMap[String, String]
        val hypernyms = new scala.collection.mutable.ArrayBuffer[ (String, String) ]
        forceTrack("Reading File")
        for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.SCRIPT_FREEBASE_RAW_PATH)))).getLines) {
          val fields = line.split("\t")
          if (fields(1) == "fb:type.object.name") {
            fbNames.put(fields(0), fields(2).substring(1, fields(2).length - 5))
            fbNames.put(fields(0), fields(0))
          } else if (fields(1) == "fb:type.object.type") {
            val target = if (fields(2).endsWith(".")) fields(2).substring(2, fields(2).length -1) else fields(2)
            hypernyms.append( (fields(0), target) )
          } else {
            /* do nothing */
          }
        }
        endTrack("Reading File")
        startTrack("Creating Edges")
        for ( (hypo, hyper) <- hypernyms ) {
          val hypoInt:Int = wordIndexer.indexOf(fbNames(hypo), true)
          val hyperInt:Int = wordIndexer.indexOf(fbNames(hyper), true)
          freebaseGraphUp.append( (hypoInt, hyperInt) )
          freebaseGraphDown.append( (hyperInt, hypoInt) )
        }
        endTrack("Creating Edges")
        endTrack("Reading Freebase")

        // Part 4: Save
        forceTrack("Writing to DB")
        val wordInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_WORD_INTERN +
          " (index, gloss) VALUES (?, ?);")
        val edgeTypeInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_EDGE_TYPE_INTERN +
          " (index, gloss) VALUES (?, ?);")
        val edgeInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_EDGES +
          " (source, sink, type, cost) VALUES (?, ?, ?, ?);")
        // Save words
        for ( (word, index) <- wordIndexer.objectsList.zipWithIndex ) {
          wordInsert.setInt(1, index)
          wordInsert.setString(2, word)
          wordInsert.executeUpdate
        }
        log("saved words")
        // Save edge types
        for (edgeType <- EdgeType.values) {
          edgeTypeInsert.setInt(1, edgeType.id)
          edgeTypeInsert.setString(2, edgeType.toString)
          edgeTypeInsert.executeUpdate
        }
        log("saved edge types")
        // Save edges
        var edgeCount = 0;
        var outDegree = (0 until wordIndexer.size).map( x => 0 ).toArray
        def edge(t:EdgeType, source:Int, sink:Int, weight:Double):Unit = {
          outDegree(source) += 1;
          edgeInsert.setInt(1, source)
          edgeInsert.setInt(2, sink)
          edgeInsert.setInt(3, t.id)
          edgeInsert.setFloat(4, weight.toFloat)
          edgeCount += 1
        }
        for ( (source, sink, weight) <- wordnetGraphUp) { edge(WORDNET_UP, source, sink, weight); }
        for ( (source, sink, weight) <- wordnetGraphDown) { edge(WORDNET_DOWN, source, sink, weight); }
        for ( (source, sink) <- wordnetNounAntonym) { edge(WORDNET_NOUN_ANTONYM, source, sink, 1.0); }
        for ( (source, sink) <- wordnetVerbAntonym) { edge(WORDNET_VERB_ANTONYM, source, sink, 1.0); }
        for ( (source, sink) <- wordnetAdjAntonym) { edge(WORDNET_ADJECTIVE_ANTONYM, source, sink, 1.0); }
        for ( (source, sink) <- wordnetAdvAntonym) { edge(WORDNET_ADVERB_ANTONYM, source, sink, 1.0); }
        for ( (source, sink) <- wordnetAdjPertainym) { edge(WORDNET_ADJECTIVE_PERTAINYM, source, sink, 1.0); }
        for ( (source, sink) <- wordnetAdvPertainym) { edge(WORDNET_ADVERB_PERTAINYM, source, sink, 1.0); }
        for ( (source, sink) <- wordnetAdjSimilar) { edge(WORDNET_ADJECTIVE_RELATED, source, sink, 1.0); }
        for ( (source, sink, weight) <- angleNearestNeighbors) { edge(ANGLE_NEAREST_NEIGHBORS, source, sink, weight); }
        for ( (source, sink) <- freebaseGraphUp) { edge(FREEBASE_UP, source, sink, 1.0); }
        for ( (source, sink) <- freebaseGraphDown) { edge(FREEBASE_DOWN, source, sink, 1.0); }
        log("saved edges")
        forceTrack("Committing")
        psql.commit
        endTrack("Committing")
        endTrack("Writing to DB")

        // Print stats
        log(BLUE, "   vocabulary size: " + wordIndexer.size)
        log(BLUE, "       total edges: " + edgeCount)
        log(BLUE, "average out-degree: " + outDegree.sum.toDouble / outDegree.length.toDouble)
      }
    }, args)
  }

  /*
  def main(args:Array[String]) = {
    val wordIndexer = new HashIndex[String]
    val phraseIndexer = new HashIndex[String]
    var factI = 0;

    Props.exec(() => {
      withConnection{ (psql:Connection) =>
        // Create Statements
        val wordInsert = psql.prepareStatement("INSERT INTO " + Postgres.TABLE_WORD_INTERN + " (key, gloss) VALUES (?, ?);")
        val phraseInsert = psql.prepareStatement("INSERT INTO " + Postgres.TABLE_PHRASE_INTERN + " (key, words) VALUES (?, ?);")
        val factInsert = psql.prepareStatement("INSERT INTO " + Postgres.TABLE_FACT_INTERN + " (key, file, begin_index, end_index, left_arg, rel, right_arg) VALUES (?, ?, ?, ?, ?, ?, ?);")
        wordInsert.setInt(1, wordIndexer.indexOf("", true))
        wordInsert.setString(2, "")
        wordInsert.executeUpdate
        // Iterate over facts
        for (fact <- facts) {
          val phrases = for (phrase <- fact.elements) yield {
            var phraseIndex = phraseIndexer.indexOf(phrase)
            if (phraseIndex < 0) {
              phraseIndex = phraseIndexer.indexOf(phrase, true)
              // Add words
              val words = for (word <- phrase.split("""\s+""")) yield {
                  var index = wordIndexer.indexOf(word);
                  if (index < 0) {
                    index = wordIndexer.indexOf(word, true)
                    wordInsert.setInt(1, index)
                    wordInsert.setString(2, word.replaceAll("\u0000", ""));
                    wordInsert.executeUpdate
                  }
                  index
                }
              // Add phrase
              val array = psql.createArrayOf("int4", words.map( x => x.asInstanceOf[Object]))
              phraseInsert.setInt(1, phraseIndex)
              phraseInsert.setArray(2, array)
              phraseInsert.executeUpdate
            }
            phraseIndex
          }
          // Add fact
          factInsert.setInt(1, factI)
          factI += 1;
          factInsert.setString(2, fact.file.replaceAll("\u0000", ""))
          factInsert.setLong(3, fact.begin)
          factInsert.setLong(4, fact.end)
          factInsert.setInt(5, phrases(0))
          factInsert.setInt(6, phrases(1))
          factInsert.setInt(7, phrases(2))
          factInsert.executeUpdate
          if (factI % 10000 == 0) {
            psql.commit
            println("added " + factI + " facts")
          }
        }
      }
    }, args)

  }
  */

  def facts:Iterator[Fact] = {
    var offset:Long = 0;
    for (file <- iterFilesRecursive(Props.SCRIPT_REVERB_RAW_DIR).iterator;
         line <-
           try {
            { if (Props.SCRIPT_REVERB_RAW_GZIP) Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))
              else Source.fromFile(file) }.getLines
           } catch {
             case (e:java.util.zip.ZipException) =>
               try {
                 Source.fromFile(file).getLines
               } catch {
                 case (e:Exception) => Nil
               }
           }
         ) yield Fact(file.getName, offset,
                      {offset += line.length + 1; offset},
                      line.split("\t"))
  }

  /**
   * An embodiment of a fact.
   *
   * @author Gabor Angeli
   */
  case class Fact(file:String, begin:Long, end:Long, fields:Array[String]) {
    assert(fields.length == 18 || fields.length == 17,
           "Invalid length line\n" + fields.zipWithIndex.map{ case (a, b) => (b.toInt + 1) + " " + a }.mkString("\n"))
  
    def leftArg :String = fields(15)
    def relation:String = fields(16)
    def rightArg:String = if (fields.length == 18) fields(17) else ""
  
    def confidence:Double = fields(11).toDouble

    def words:Iterable[String] = leftArg.split("""\s+""") ++ relation.split("""\s+""") ++ rightArg.split("""\s+""")
    def elements:Array[String] = Array[String](leftArg, relation, rightArg)
  
    override def toString:String = leftArg + " ~ " + relation + " ~ " + rightArg
  }
}


