package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import scala.io.Source

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection

import edu.stanford.nlp.io.IOUtils._
import edu.stanford.nlp.util.HashIndex
import edu.stanford.nlp.math.SloppyMath.acos
import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._
import edu.stanford.nlp._

import edu.smu.tspell.wordnet._

import org.goobs.sim._

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._
import org.goobs.truth.EdgeType._
import org.goobs.truth.Utils._

/**
 *
 * The entry point for constructing the nodes of a graph, indexed to a compact
 * form.
 *
 * @author Gabor Angeli
 */

object BootstrapGraph {
  
  private val logger = Redwood.channels("MKGraph")
  
  val wordIndexer = new HashIndex[String]
  val normalizedWordCache = new HashMap[String, String]

  def indexOf(phrase:String):Int = {
    val normalized = normalizedWordCache.get(phrase.toLowerCase) match {
      case Some(normalized) => normalized
      case None =>
        val n = tokenizeWithCase(phrase).mkString(" ")
        normalizedWordCache(phrase.toLowerCase) = n
        n
    }
    wordIndexer.indexOf(normalized, true)
  }

  private val Timestamp = """\s*\d{4}-\d{2}-\d{2}[tT]\d{2}:\d{2}:\d{2}[^\s]+\s*""".r
  private val Parentheticals = """\s*\([^\)]+\)\s*""".r
  private val Brackets = """\s*\[[^\]]+\]\s*""".r
  private val EscapeChar = """\\""".r

  def cleanFBName(raw:String):String = {
    val withDeletions = List(Timestamp, Parentheticals, Brackets, EscapeChar)
      .foldLeft(raw){ case (str:String, regexp:scala.util.matching.Regex) => regexp.replaceAllIn(str, " ") }
    new Sentence(withDeletions).words.mkString(" ")
  }

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
    edu.stanford.nlp.NLPConfig.caseless  // set caseless models
    Props.exec(() => {
      indexOf("")
      val wordnet = Ontology.load(Props.SCRIPT_WORDNET_PATH)

      // Part 1: Read Wordnet
      // note: we are prohibiting ROOT as a valid node to traverse
      forceTrack("Reading WordNet")
      for ((phrase, nodes) <- wordnet.ontology) {
        val phraseAsString:String = phrase.mkString(" ")
        val source:Int = indexOf(phraseAsString)
        for (node <- nodes;
             hyper <- node.hypernyms) {
          hyper match {
            case (hyperNode:Ontology.RealNode) =>
              val edgeWeight = scala.math.log(
                hyperNode.subtreeCount.toDouble / node.subtreeCount.toDouble )
              assert(edgeWeight >= 0.0)
              // is_a ontology
              for (hyperWord <- hyperNode.synset.getWordForms) {
                val hyperInt:Int = indexOf(hyperWord)
                wordnetGraphUp.append( (source, hyperInt, edgeWeight) )
                wordnetGraphDown.append( (hyperInt, source, edgeWeight) )
              }
              // other wordnet relations
              hyperNode.synset match {
                case (as:NounSynset) =>
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sink:Int = indexOf(antonym.getWordForm)
                    wordnetVerbAntonym.append( (source, sink) )
                  }
                case (as:VerbSynset) =>
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sink:Int = indexOf(antonym.getWordForm)
                    wordnetNounAntonym.append( (source, sink) )
                  }
                case (as:AdjectiveSynset) =>
                  for (related <- as.getSimilar;
                       wordForm <- related.getWordForms) {
                    val sink:Int = indexOf(wordForm)
                    wordnetAdjSimilar.append( (source, sink) )
                  }
                  for (pertainym <- as.getPertainyms(phraseAsString)) {
                    val sink:Int = indexOf(pertainym.getWordForm)
                    wordnetAdjPertainym.append( (source, sink) )
                  }
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sink:Int = indexOf(antonym.getWordForm)
                    wordnetAdjAntonym.append( (source, sink) )
                  }
                case (as:AdverbSynset) =>
                  for (pertainym <- as.getPertainyms(phraseAsString)) {
                    val sink:Int = indexOf(pertainym.getWordForm)
                    wordnetAdvPertainym.append( (source, sink) )
                  }
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sink:Int = indexOf(antonym.getWordForm)
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
        val source:Int = indexOf(fields(0))
        for (i <- 1 until fields.length) {
          val scoreAndGloss = fields(i).split(" ")
          assert(scoreAndGloss.length == 2)
          val sink:Int = indexOf(scoreAndGloss(1))
          val angle:Double = acos( scoreAndGloss(0).toDouble ) / scala.math.Pi
          assert(angle >= 0.0)
          angleNearestNeighbors.append( (source, sink, angle) )
        }
      }
      endTrack("Reading Nearest Neighbors")

      // Part 3: Read freebase
      forceTrack("Reading Freebase")
      val fbNames = new scala.collection.mutable.HashMap[String, String]
      val hypernyms = new scala.collection.mutable.ArrayBuffer[ (String, String) ]
      val unknownNames = new scala.collection.mutable.HashSet[String]
      forceTrack("Reading File")
      // Pass 1: read edges
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.SCRIPT_FREEBASE_RAW_PATH)))).getLines) {
        val fields = line.split("\t")
        if (fields(1) == "fb:type.object.type") {
          val target = if (fields(2).endsWith(".")) fields(2).substring(0, fields(2).length -1) else fields(2)
          hypernyms.append( (fields(0), target) )
          unknownNames.add(fields(0))
          unknownNames.add(target)
        }
      }
      log ("pass 1 complete: read edges")
      // Pass 2: read names
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.SCRIPT_FREEBASE_RAW_PATH)))).getLines) {
        val fields = line.split("\t")
        if (fields(1) == "fb:type.object.name" && unknownNames(fields(0)) &&
            (!fields(2).contains("@") || fields(2).endsWith("@en.")) ) {
          val name:String = fields(2).substring(1, fields(2).length - 5)
          fbNames.put(fields(0), cleanFBName(name))
        }
      }
      log ("pass 2 complete: read names")
      endTrack("Reading File")
      // Store edges
      forceTrack("Creating Edges")
      var edgesAdded = 0
      for ( (hypo, hyper) <- hypernyms;
            hypoName <- fbNames.get(hypo) ) {
        val hyperName:String = fbNames.get(hyper).getOrElse(hyper)
        val hypoInt:Int = wordIndexer.indexOf(hypoName, true)  // trust case
        val hyperInt:Int = indexOf(hyperName)  // don't trust hypernym case
        freebaseGraphUp.append( (hypoInt, hyperInt) )
        freebaseGraphDown.append( (hyperInt, hypoInt) )
        edgesAdded += 1
        if (edgesAdded % 1000000 == 0) {
          logger.log("Added " + (edgesAdded / 1000000) + "M edges (@ " +
              hypoName + " is_a " + hyperName + ")")
        }
      }
      endTrack("Creating Edges")
      endTrack("Reading Freebase")

      withConnection{ (psql:Connection) =>
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
          wordInsert.setString(2, word.replaceAll("\u0000", ""))
          wordInsert.executeUpdate
        }
        logger.log("saved words")
        psql.commit
        // Save edge types
        for (edgeType <- EdgeType.values) {
          edgeTypeInsert.setInt(1, edgeType.id)
          edgeTypeInsert.setString(2, edgeType.toString.replaceAll("\u0000", ""))
          edgeTypeInsert.executeUpdate
        }
        logger.log("saved edge types")
        psql.commit
        // Save edges
        var edgeCount = 0;
        var outDegree = (0 until wordIndexer.size).map( x => 0 ).toArray
        def edge(t:EdgeType, source:Int, sink:Int, weight:Double):Unit = {
          outDegree(source) += 1;
          edgeInsert.setInt(1, source)
          edgeInsert.setInt(2, sink)
          edgeInsert.setInt(3, t.id)
          edgeInsert.setFloat(4, weight.toFloat)
          edgeInsert.executeUpdate
          edgeCount += 1
        }
        for ( (source, sink, weight) <- wordnetGraphUp) { edge(WORDNET_UP, source, sink, weight); }; psql.commit
        for ( (source, sink, weight) <- wordnetGraphDown) { edge(WORDNET_DOWN, source, sink, weight); }; psql.commit
        for ( (source, sink) <- wordnetNounAntonym) { edge(WORDNET_NOUN_ANTONYM, source, sink, 1.0); }; psql.commit
        for ( (source, sink) <- wordnetVerbAntonym) { edge(WORDNET_VERB_ANTONYM, source, sink, 1.0); }; psql.commit
        for ( (source, sink) <- wordnetAdjAntonym) { edge(WORDNET_ADJECTIVE_ANTONYM, source, sink, 1.0); }; psql.commit
        for ( (source, sink) <- wordnetAdvAntonym) { edge(WORDNET_ADVERB_ANTONYM, source, sink, 1.0); }; psql.commit
        for ( (source, sink) <- wordnetAdjPertainym) { edge(WORDNET_ADJECTIVE_PERTAINYM, source, sink, 1.0); }; psql.commit
        for ( (source, sink) <- wordnetAdvPertainym) { edge(WORDNET_ADVERB_PERTAINYM, source, sink, 1.0); }; psql.commit
        for ( (source, sink) <- wordnetAdjSimilar) { edge(WORDNET_ADJECTIVE_RELATED, source, sink, 1.0); }; psql.commit
        for ( (source, sink, weight) <- angleNearestNeighbors) { edge(ANGLE_NEAREST_NEIGHBORS, source, sink, weight); }; psql.commit
        for ( (source, sink) <- freebaseGraphUp) { edge(FREEBASE_UP, source, sink, 1.0); }; psql.commit
        for ( (source, sink) <- freebaseGraphDown) { edge(FREEBASE_DOWN, source, sink, 1.0); }; psql.commit
        logger.log("saved edges")
        forceTrack("Committing")
        psql.commit
        endTrack("Committing")
        endTrack("Writing to DB")

        // Print stats
        logger.log(BLUE, "   vocabulary size: " + wordIndexer.size)
        logger.log(BLUE, "       total edges: " + edgeCount)
        logger.log(BLUE, "average out-degree: " + outDegree.sum.toDouble / outDegree.length.toDouble)
      }
    }, args)
  }
}


