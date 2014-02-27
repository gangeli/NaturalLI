package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection

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
import scala.collection.mutable

//
// SQL prerequisite statements for this script:
// CREATE TABLE word_indexer ( index INTEGER PRIMARY KEY, gloss TEXT);
// CREATE TABLE edge_type_indexer ( index SMALLINT PRIMARY KEY, gloss TEXT);
// CREATE TABLE edge ( source INTEGER, source_sense INTEGER, sink INTEGER, sink_sense INTEGER, type SMALLINT, cost REAL );
//

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
  val normalizedWordCache = new mutable.HashMap[String, String]

  def indexOf(rawPhrase:String):Int = {
    if (rawPhrase.trim == "") return 0
    val Number = """^([0-9]+)$""".r
    val phrase = rawPhrase.trim.toLowerCase.split("""\s+""")
                               .map( (w:String) => w match {
      case Number(n) => "num_" + n.length
      case _ => w
    }).mkString(" ")
    val normalized = normalizedWordCache.get(phrase) match {
      case Some(n) => n
      case None =>
        val n = tokenizeWithCase(phrase).mkString(" ")
        normalizedWordCache(phrase) = n
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

  // ( begin, beginSense, end, endSense, log(P(hyper) / P(hypo)) )
  val wordnetGraphUp = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int, Double)]
  val wordnetGraphDown = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int, Double)]
  
  // ( begin, beginSense, end, endSense, [1.0] )
  val wordnetNounAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]
  val wordnetVerbAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]
  val wordnetAdjAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]
  val wordnetAdvAntonym = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]
  val wordnetAdjPertainym = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]
  val wordnetAdvPertainym = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]
  val wordnetAdjSimilar = new scala.collection.mutable.ArrayBuffer[(Int, Int, Int, Int)]

  // ( begin, sense)
  val senses = new scala.collection.mutable.ArrayBuffer[(Int, Int)]

  // ( begin, end, acos(cos_similarity) / π )
  val angleNearestNeighbors = new scala.collection.mutable.ArrayBuffer[(Int, Int, Double)]
  
  // ( begin, end, [1.0] )
  val freebaseGraphUp = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val freebaseGraphDown = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  
  // ( begin, end, [1.0] )
  val word2lemma = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val lemma2word = new scala.collection.mutable.ArrayBuffer[(Int, Int)]
  val fudgeNumber = new scala.collection.mutable.ArrayBuffer[(Int, Int)]


  def main(args:Array[String]) = {
    edu.stanford.nlp.NLPConfig.caseless  // set caseless models
    Props.exec(() => {
      indexOf("")
      val wordnet = Ontology.load(Props.SCRIPT_WORDNET_PATH)
      val jaws    = WordNetDatabase.getFileInstance

      // Part 1: Read Wordnet
      // note: we are prohibiting ROOT as a valid node to traverse
      forceTrack("Reading WordNet")

      def getSense(phrase:String, synset:Synset):Int = {
        val synsets = jaws.getSynsets(phrase)
        val index = if (synsets == null || synsets.length == 0) {
          throw new IllegalStateException("Could not find any synsets for phrase: " + phrase)
        } else {
          synsets.indexOf(synset)
        }
        if (index < 0) {
          throw new IllegalStateException("Could not find sense for phrase: " + phrase)
        }
        if (index >= (0x1 << 5)) {
          warn("Too many senses for phrase: " + phrase)
          (0x1 << 5) - 1
        } else {
          index + 1
        }
      }


      for ((phrase, nodes) <- wordnet.ontology.toArray.sortBy( _._1.toString() )) {
        val phraseAsString:String = phrase.mkString(" ")
        val sourceWord:Int = indexOf(phraseAsString)
        for (node <- nodes;
             hyper <- node.hypernyms) {

          // Get word sense
          val sourceSense:Int = getSense(phraseAsString, node.synset)
          if (sourceSense != 0) {
            senses.append( (sourceWord, sourceSense) )
          }

          // Construct Edges
          hyper match {
            case (hyperNode:Ontology.RealNode) =>
              val edgeWeight = scala.math.log(
                hyperNode.subtreeCount.toDouble / node.subtreeCount.toDouble )
              assert(edgeWeight >= 0.0)
              // is_a ontology
              for (hyperWord <- hyperNode.synset.getWordForms) {
                val hyperInt:Int = indexOf(hyperWord)
                val hyperSense:Int = getSense(hyperWord, hyperNode.synset)
                wordnetGraphUp.append( (sourceWord, sourceSense, hyperInt, hyperSense, edgeWeight) )
                wordnetGraphDown.append( (hyperInt, hyperSense, sourceWord, sourceSense, edgeWeight) )
              }
              // other wordnet relations
              hyperNode.synset match {
                case (as:NounSynset) =>
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sinkWord:Int = indexOf(antonym.getWordForm)
                    wordnetVerbAntonym.append( (sourceWord, sourceSense, sinkWord, getSense(antonym.getWordForm, antonym.getSynset)) )
                  }
                case (as:VerbSynset) =>
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sinkWord:Int = indexOf(antonym.getWordForm)
                    wordnetNounAntonym.append( (sourceWord, sourceSense, sinkWord, getSense(antonym.getWordForm, antonym.getSynset)) )
                  }
                case (as:AdjectiveSynset) =>
                  for (related <- as.getSimilar;
                       wordForm <- related.getWordForms) {
                    val sinkWord:Int = indexOf(wordForm)
                    wordnetAdjSimilar.append( (sourceWord, sourceSense, sinkWord, getSense(wordForm, related)) )
                  }
                  for (pertainym <- as.getPertainyms(phraseAsString)) {
                    val sinkWord:Int = indexOf(pertainym.getWordForm)
                    wordnetAdjPertainym.append( (sourceWord, sourceSense, sinkWord, getSense(pertainym.getWordForm, pertainym.getSynset)) )
                  }
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sinkWord:Int = indexOf(antonym.getWordForm)
                    wordnetAdjAntonym.append( (sourceWord, sourceSense, sinkWord, getSense(antonym.getWordForm, antonym.getSynset)) )
                  }
                case (as:AdverbSynset) =>
                  for (pertainym <- as.getPertainyms(phraseAsString)) {
                    val sinkWord:Int = indexOf(pertainym.getWordForm)
                    wordnetAdvPertainym.append( (sourceWord, sourceSense, sinkWord, getSense(pertainym.getWordForm, pertainym.getSynset)) )
                  }
                  for (antonym <- as.getAntonyms(phraseAsString)) {
                    val sinkWord:Int = indexOf(antonym.getWordForm)
                    wordnetAdvAntonym.append( (sourceWord, sourceSense, sinkWord, getSense(antonym.getWordForm, antonym.getSynset)) )
                  }
                case _ =>
              }
            case _ =>
              debug("sub-root node: " + node + " [" + hyper.getClass + "]")
          }
          
        }
      }

      // Finish reading WordNet
      val numWordnetWords = wordIndexer.size
      logger.log("number of words in WordNet: " + numWordnetWords)
      endTrack("Reading WordNet")

      // Part 2: Read distsim
      forceTrack("Reading Nearest Neighbors")
      for (line <- Source.fromFile(Props.SCRIPT_DISTSIM_COS, "UTF-8").getLines()) {
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
      val numDistSimWords = wordIndexer.size - numWordnetWords
      log("number of (new) words in DistSim space: " + numDistSimWords)
      endTrack("Reading Nearest Neighbors")

      // Part 3: Read freebase
      forceTrack("Reading Freebase")
      val fbNames = new scala.collection.mutable.HashMap[String, String]
      val hypernyms = new scala.collection.mutable.ArrayBuffer[ (String, String) ]
      val unknownNames = new scala.collection.mutable.HashSet[String]
      forceTrack("Reading File")
      // Pass 1: read edges
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.SCRIPT_FREEBASE_RAW_PATH)))).getLines()) {
        val fields = line.split("\t")
        if (fields(1) == "fb:type.object.type" || fields(1) == "fb:common.topic.notable_types") {
          val target = if (fields(2).endsWith(".")) fields(2).substring(0, fields(2).length -1) else fields(2)
          if (!target.startsWith("fb:type") &&
              target != "fb:common.topic" &&
              target != "fb:freebase.type_profile") {
            hypernyms.append( (fields(0), target) )
            unknownNames.add(fields(0))
            unknownNames.add(target)
          }
        }
      }
      logger.log ("pass 1 complete: read edges")
      // Pass 2: read names
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.SCRIPT_FREEBASE_RAW_PATH)))).getLines()) {
        val fields = line.split("\t")
        if (fields(1) == "fb:type.object.name" && unknownNames(fields(0)) &&
            (!fields(2).contains("@") || fields(2).endsWith("@en.")) ) {
          val name:String = fields(2).substring(1, fields(2).length - 5)
          fbNames.put(fields(0), cleanFBName(name))
        }
      }
      logger.log ("pass 2 complete: read names")
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
      val numFBWords = wordIndexer.size - numDistSimWords
      logger.log("number of (new) words in Freebase: " + numFBWords)
      endTrack("Reading Freebase")
      
      // Part 4: Morphology
      forceTrack("Adding Morphology")
      for ( (word, index) <- wordIndexer.objectsList.zipWithIndex ) {
        if (!word.contains(" ")) {
          val lemmas = Sentence(word).lemma
          if (lemmas.length == 1) {
            val lemmaIndex = indexOf(lemmas(0))
            if (lemmaIndex != index) {
              word2lemma.add( (index, lemmaIndex) )
              lemma2word.add( (lemmaIndex, index) )
            }
          }
        }
      }
      logger.log("added lemmas")
      for (oom <- 2 until 100 if wordIndexer.indexOf("num_" + oom, false) >= 0 &&
                                 wordIndexer.indexOf("num_" + (oom-1), false) >= 0) {
        val lower = wordIndexer.indexOf("num_" + (oom-1))
        val higher = wordIndexer.indexOf("num_" + oom)
        fudgeNumber.add( (lower, higher) )
        fudgeNumber.add( (higher, lower) )
      }
      logger.log("added number fudging")
      endTrack("Adding Morphology")

      // Part 5: Save
      withConnection{ (psql:Connection) =>
        psql.setAutoCommit(true)
        forceTrack("Writing to DB")
        val wordInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_WORD_INTERN +
          " (index, gloss) VALUES (?, ?);")
        val edgeTypeInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_EDGE_TYPE_INTERN +
          " (index, gloss) VALUES (?, ?);")
        val edgeInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_EDGES +
          " (source, source_sense, sink, sink_sense, type, cost) VALUES (?, ?, ?, ?, ?, ?);")
        // Save words
        for ( (word, index) <- wordIndexer.objectsList.zipWithIndex ) {
          wordInsert.setInt(1, index)
          wordInsert.setString(2, word.replaceAll("\u0000", ""))
          wordInsert.addBatch()
        }
        wordInsert.executeBatch
        logger.log("saved words")
        // Save edge types
        for (edgeType <- EdgeType.values) {
          edgeTypeInsert.setInt(1, edgeType.id)
          edgeTypeInsert.setString(2, edgeType.toString.replaceAll("\u0000", ""))
          edgeTypeInsert.executeUpdate
        }
        logger.log("saved edge types")
        // Save edges
        var edgeCount = 0
        val outDegree = (0 until wordIndexer.size).map( x => 0 ).toArray
        def edge(t:EdgeType, source:Int, sourceSense:Int, sink:Int, sinkSense:Int, weight:Double):Unit = {
          outDegree(source) += 1
          edgeInsert.setInt(1, source)
          edgeInsert.setInt(2, sourceSense)
          edgeInsert.setInt(3, sink)
          edgeInsert.setInt(4, sinkSense)
          edgeInsert.setInt(5, t.id)
          edgeInsert.setFloat(6, weight.toFloat)
          edgeInsert.addBatch()
          edgeCount += 1
        }
        // (WordNet edges)
        for ( (source, sourceSense, sink, sinkSense, weight) <- wordnetGraphUp) {   edge(WORDNET_UP,                  source, sourceSense, sink, sinkSense, weight); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense, weight) <- wordnetGraphDown) { edge(WORDNET_DOWN,                source, sourceSense, sink, sinkSense, weight); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense ) <- wordnetNounAntonym) {      edge(WORDNET_NOUN_ANTONYM,        source, sourceSense, sink, sinkSense, 1.0); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense ) <- wordnetVerbAntonym) {      edge(WORDNET_VERB_ANTONYM,        source, sourceSense, sink, sinkSense, 1.0); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense ) <- wordnetAdjAntonym) {       edge(WORDNET_ADJECTIVE_ANTONYM,   source, sourceSense, sink, sinkSense, 1.0); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense ) <- wordnetAdvAntonym) {       edge(WORDNET_ADVERB_ANTONYM,      source, sourceSense, sink, sinkSense, 1.0); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense ) <- wordnetAdjPertainym) {     edge(WORDNET_ADJECTIVE_PERTAINYM, source, sourceSense, sink, sinkSense, 1.0); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense ) <- wordnetAdvPertainym) {     edge(WORDNET_ADVERB_PERTAINYM,    source, sourceSense, sink, sinkSense, 1.0); }; edgeInsert.executeBatch
        for ( (source, sourceSense, sink, sinkSense ) <- wordnetAdjSimilar) {       edge(WORDNET_ADJECTIVE_RELATED,   source, sourceSense, sink, sinkSense, 1.0); }; edgeInsert.executeBatch
        // (nearest neighbors edges)
        for ( (source, sink, weight) <- angleNearestNeighbors) { edge(ANGLE_NEAREST_NEIGHBORS, source, 0, sink, 0, weight); }; edgeInsert.executeBatch
        // (freebase edges)
        for ( (source, sink) <- freebaseGraphUp) { edge(FREEBASE_UP, source, 0, sink, 0, 1.0); }; edgeInsert.executeBatch
        for ( (source, sink) <- freebaseGraphDown) { edge(FREEBASE_DOWN, source, 0, sink, 0, 1.0); }; edgeInsert.executeBatch
        // (morpha edges)
        for ( (source, sink) <- word2lemma ) { edge(MORPH_TO_LEMMA, source, 0, sink, 0, 1.0); }; edgeInsert.executeBatch
        for ( (source, sink) <- lemma2word ) { edge(MORPH_FROM_LEMMA, source, 0, sink, 0, 1.0); }; edgeInsert.executeBatch
        for ( (source, sink) <- fudgeNumber ) { edge(MORPH_FUDGE_NUMBER, source, 0, sink, 0, 1.0); }; edgeInsert.executeBatch
        // (switch senses)
        for ( (source, sourceSense) <- senses) { edge(SENSE_REMOVE, source, sourceSense, source, 0, 1.0); }; edgeInsert.executeBatch
        for ( (source, sourceSense) <- senses) { edge(SENSE_ADD, source, 0, source, sourceSense, 1.0); }; edgeInsert.executeBatch
        logger.log("saved edges")
        endTrack("Writing to DB")

        // Print stats
        logger.log(BLUE, "   vocabulary size: " + wordIndexer.size)
        logger.log(BLUE, "       total edges: " + edgeCount)
        logger.log(BLUE, "average out-degree: " + outDegree.sum.toDouble / outDegree.length.toDouble)
      }
    }, args)
  }
}


