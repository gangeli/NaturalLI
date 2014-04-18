package org.goobs.truth.scripts

import java.sql.Connection

import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.io.Source

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._
import org.goobs.truth.EdgeType._
import org.goobs.truth.Utils._

import edu.smu.tspell.wordnet._

import edu.stanford.nlp.util.HashIndex

import org.goobs.sim._
import edu.stanford.nlp.Sentence

//
// SQL prerequisite statements for this script:
// -----
// CREATE TABLE word ( index INTEGER PRIMARY KEY, gloss TEXT );
// CREATE TABLE edge_type ( index SMALLINT PRIMARY KEY, gloss TEXT );
// CREATE TABLE edge ( source INTEGER, source_sense INTEGER, sink INTEGER, sink_sense INTEGER, type SMALLINT, cost REAL );
//
// SQL post-requisite statements on completion of this script (only the first is crucial):
// -----
// CREATE INDEX word_gloss ON word_indexer(gloss);
// CREATE INDEX edge_outgoing ON edge (source, source_sense);
// CREATE INDEX edge_type ON edge (type);
//

/**
 * Populate the database graph from various resources.
 *
 * @author gabor
 */
object CreateGraph {
  /** The indexer to use for converting phrases to their corresponding indices */
  val wordIndexer = new HashIndex[String]
  /** The cache for converting a raw phrase to its tokenized form */
  val normalizedWordCache = new mutable.HashMap[String, String]
  /** A regular expression for numbers */
  val NUMBER = """^([0-9]+)$""".r
  /** POS cache; a mapping from a word to its most likely POS tag */
  val posCache = new mutable.HashMap[Int, Char]

  /**
   * Index a phrase to an integer, respecting various relevant transformations
   * @param rawPhrase The raw phrase to i
   * @return The index of that phrase, starting with 0
   */
  def indexOf(rawPhrase:String, trustCase:Boolean = true):Int = {
    // Normalize phrase
    if (rawPhrase.trim == "") return 0
    val phrase = rawPhrase.trim.toLowerCase.split("""\s+""")
      .map( (w:String) => w match {
      case NUMBER(n) => "num_" + n.length
      case _ => w
    }).mkString(" ")
    val normalized = normalizedWordCache.get(phrase) match {
      case Some(n) => n
      case None =>
        val n = if (trustCase) new Sentence(phrase).lemma.mkString(" ") else tokenizeWithCase(phrase).mkString(" ")
        normalizedWordCache(phrase) = n
        n
    }
    // Index phrase
    val index:Int = wordIndexer.indexOf(normalized, true)
    // Get most common POS of phrase
    if (!posCache.contains(index)) {
      val pos:Character = new Sentence(rawPhrase).pos.map( _.charAt(0) ).groupBy(identity).maxBy(_._2.size)._1
      posCache(index) = pos
    }
    // Return index of phrase
    index
  }

  def getSense(phrase:String, synset:Synset)(implicit jaws:WordNetDatabase):Int = {
    val synsets:Array[Synset] = jaws.getSynsets(phrase)
    val index = synsets.indexOf(synset)
    if (index < 0) {
      throw new IllegalStateException("Unknown synset: " + synset)
    }
    if (index > 30) 0 else index + 1
  }

  def main(args:Array[String]) = {
    Props.exec(fn2execInput1(() => {
      // Some global stuff
      println("Started")
      wordIndexer.indexOf(Utils.WORD_NONE, true)
      wordIndexer.indexOf(Utils.WORD_UNK,  true)
      implicit val jaws = WordNetDatabase.getFileInstance
      val wordnet       = Ontology.load(Props.SCRIPT_WORDNET_PATH)

      withConnection{ (psql:Connection) =>
        // Database variables
        psql.setAutoCommit(true)
        val wordInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_WORD_INTERN +
            " (index, gloss) VALUES (?, ?);")
        val edgeTypeInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_EDGE_TYPE_INTERN +
            " (index, gloss) VALUES (?, ?);")
        val edgeInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_EDGES +
            " (source, source_sense, sink, sink_sense, type, cost) VALUES (?, ?, ?, ?, ?, ?);")

        // Function to add an edge
        def edge(t:EdgeType, source:Int, sourceSense:Int, sink:Int, sinkSense:Int, weight:Double):Unit = {
          edgeInsert.setInt(1, source)
          edgeInsert.setInt(2, sourceSense)
          edgeInsert.setInt(3, sink)
          edgeInsert.setInt(4, sinkSense)
          edgeInsert.setInt(5, t.id)
          edgeInsert.setFloat(6, weight.toFloat)
          edgeInsert.addBatch()
        }

        //
        // WordNet
        //
        println("[10] WordNet Edges")
        for ((phrase:Seq[String], nodes:Set[Ontology.RealNode]) <- wordnet.ontology.toArray.sortBy( _._1.toString() )) {
          for (node:Ontology.RealNode <- nodes) {
            val (phraseGloss:String, trustCase:Boolean) = {
              val gloss = phrase.mkString(" ")
              val wordForms = node.synset.getWordForms.filter( _.toLowerCase == gloss )
              if (wordForms.length > 0) (wordForms(0), true) else (gloss, false)
            }
            val index:Int = indexOf(phraseGloss, trustCase)
            val sense:Int = getSense(phraseGloss, node.synset)

            // Add hyper/hypo-nyms
            for (x:Ontology.Node <- node.hypernyms.toArray.sortBy( _.toString() )) x match { case (hyperNode:Ontology.RealNode) =>
              val edgeWeight = scala.math.log(
                hyperNode.subtreeCount.toDouble / node.subtreeCount.toDouble )
              assert(edgeWeight >= 0.0)

              for (hyperWord <- hyperNode.synset.getWordForms) {
                val hyperInt:Int = indexOf(hyperWord)
                val hyperSense:Int = getSense(hyperWord, hyperNode.synset)
                edge(EdgeType.WORDNET_UP, index, sense, hyperInt, hyperSense, edgeWeight)
                edge(EdgeType.WORDNET_DOWN, hyperInt, hyperSense, index, sense, edgeWeight)
              }
            case _ => }

            // Other WordNet edges
            node.synset match {
              case (as:NounSynset) =>
                for (antonym <- as.getAntonyms(phraseGloss)) {
                  edge(EdgeType.WORDNET_NOUN_ANTONYM, index, sense, indexOf(antonym.getWordForm), getSense(antonym.getWordForm, antonym.getSynset), 1.0)
                }
                edge(EdgeType.ADD_NOUN, 0, 0, index, sense, 1.0)
                edge(EdgeType.DEL_NOUN, index, sense, 0, 0, 1.0)
              case (as:VerbSynset) =>
                for (antonym <- as.getAntonyms(phraseGloss)) {
                  edge(EdgeType.WORDNET_VERB_ANTONYM, index, sense, indexOf(antonym.getWordForm), getSense(antonym.getWordForm, antonym.getSynset), 1.0)
                }
                edge(EdgeType.ADD_VERB, 0, 0, index, sense, 1.0)
                edge(EdgeType.DEL_VERB, index, sense, 0, 0, 1.0)
              case (as:AdjectiveSynset) =>
                for (related <- as.getSimilar;
                     wordForm <- related.getWordForms) {
                  edge(EdgeType.WORDNET_ADJECTIVE_RELATED, index, sense, indexOf(wordForm), getSense(wordForm, related), 1.0)
                }
                for (pertainym <- as.getPertainyms(phraseGloss)) {
                  edge(EdgeType.WORDNET_ADJECTIVE_PERTAINYM, index, sense, indexOf(pertainym.getWordForm), getSense(pertainym.getWordForm, pertainym.getSynset), 1.0)
                }
                for (antonym <- as.getAntonyms(phraseGloss)) {
                  edge(EdgeType.WORDNET_ADJECTIVE_ANTONYM, index, sense, indexOf(antonym.getWordForm), getSense(antonym.getWordForm, antonym.getSynset), 1.0)
                }
                edge(EdgeType.ADD_ADJ, 0, 0, index, sense, 1.0)
                edge(EdgeType.DEL_ADJ, index, sense, 0, 0, 1.0)
              case (as:AdverbSynset) =>
                for (pertainym <- as.getPertainyms(phraseGloss)) {
                  edge(EdgeType.WORDNET_ADVERB_PERTAINYM, index, sense, indexOf(pertainym.getWordForm), getSense(pertainym.getWordForm, pertainym.getSynset), 1.0)
                }
                for (antonym <- as.getAntonyms(phraseGloss)) {
                  edge(EdgeType.WORDNET_ADVERB_ANTONYM, index, sense, indexOf(antonym.getWordForm), getSense(antonym.getWordForm, antonym.getSynset), 1.0)
                }
                edge(EdgeType.ADD_ADV, 0, 0, index, sense, 1.0)
                edge(EdgeType.DEL_ADV, index, sense, 0, 0, 1.0)
              case _ =>
            }
          }
        }


        //
        // Nearest Neighbors
        //
        println("[20] Nearest Neighbors")
        for (line <- Source.fromFile(Props.SCRIPT_DISTSIM_COS, "UTF-8").getLines()) {
          val fields = line.split("\t")
          val source:Int = indexOf(fields(0))
          for (i <- 1 until fields.length) {
            val scoreAndGloss = fields(i).split(" ")
            assert(scoreAndGloss.length == 2)
            val sink:Int = indexOf(scoreAndGloss(1))
            val angle:Double = math.acos( scoreAndGloss(0).toDouble ) / scala.math.Pi
            assert(angle >= 0.0)
            edge(EdgeType.ANGLE_NEAREST_NEIGHBORS, source, 0, sink, 0, angle)
          }
        }

        //
        // Senseless Insertions/Deletions
        //
        println("[30] Senseless insert/delete")
        for ( (index, pos) <- posCache) {
          pos match {
            case 'N' =>
              edge(EdgeType.DEL_NOUN, index, 0, 0, 0, 1.0)
              edge(EdgeType.ADD_NOUN, 0, 0, index, 0, 1.0)
            case 'V' =>
              edge(EdgeType.DEL_VERB, index, 0, 0, 0, 1.0)
              edge(EdgeType.ADD_VERB, 0, 0, index, 0, 1.0)
            case 'J' =>
              edge(EdgeType.DEL_ADJ, index, 0, 0, 0, 1.0)
              edge(EdgeType.ADD_ADJ, 0, 0, index, 0, 1.0)
            case 'R' =>
              edge(EdgeType.DEL_ADV, index, 0, 0, 0, 1.0)
              edge(EdgeType.ADD_ADV, 0, 0, index, 0, 1.0)
            case _ =>
              edge(EdgeType.DEL_OTHER, index, 0, 0, 0, 1.0)
              edge(EdgeType.ADD_OTHER, 0, 0, index, 0, 1.0)
          }
        }

        //
        // Quantifier Replacement
        //
        println("[40] Quantifier Replacement")
        for ( source <- Quantifier.values()) {
          val sourceIndexed:Int = indexOf(source.surfaceForm.mkString(" "))
          for (sink <- Quantifier.values()) {
            val sinkIndexed:Int = indexOf(sink.surfaceForm.mkString(" "))
            if (sourceIndexed != sinkIndexed) {
              if (source.closestMeaning.partialOrder == sink.closestMeaning.partialOrder) {
                edge(EdgeType.QUANTIFIER_REWORD, sourceIndexed, 0, sinkIndexed, 0, 1.0)
              } else if (source.closestMeaning.partialOrder == -sink.closestMeaning.partialOrder) {
                edge(EdgeType.QUANTIFIER_NEGATE, sourceIndexed, 0, sinkIndexed, 0, 1.0)
              } else if (source.closestMeaning.partialOrder < sink.closestMeaning.partialOrder) {
                edge(EdgeType.QUANTIFIER_STRENGTHEN, sourceIndexed, 0, sinkIndexed, 0, 1.0)
              } else if (source.closestMeaning.partialOrder > sink.closestMeaning.partialOrder) {
                edge(EdgeType.QUANTIFIER_WEAKEN, sourceIndexed, 0, sinkIndexed, 0, 1.0)
              }
            }
          }
        }

        //
        // Morphology
        //
        println("[50] Morphology")
////        Lemmas
//        for ( (word, index) <- wordIndexer.objectsList.zipWithIndex ) {
//          if (!word.contains(" ")) {
//            val lemmas = new Sentence(word).lemma
//            if (lemmas.length == 1 && lemmas(0) != word) {
//              val lemmaIndex = indexOf(lemmas(0))
//              if (lemmaIndex != index) {
//                // Add lemma (one per sense)
//                val synsets = jaws.getSynsets(word)
//                for (sense <- 0 to math.min(31, if (synsets != null) synsets.length else 0)) {
//                  edge(EdgeType.MORPH_TO_LEMMA, index, sense, lemmaIndex, sense, 1.0)
//                  edge(EdgeType.MORPH_FROM_LEMMA, lemmaIndex, sense, index, sense, 1.0)
//                }
//              }
//            }
//          }
//        }


        //
        // Misc.
        //
        println("[60] Misc.")
        // Fudge Numbers
        for (oom <- 2 until 100 if wordIndexer.indexOf("num_" + oom, false) >= 0 && wordIndexer.indexOf("num_" + (oom-1), false) >= 0) {
          val lower = wordIndexer.indexOf("num_" + (oom-1))
          val higher = wordIndexer.indexOf("num_" + oom)
          edge(EdgeType.MORPH_FUDGE_NUMBER, lower, 0, higher, 0, 1.0)
          edge(EdgeType.MORPH_FUDGE_NUMBER, higher, 0, lower, 0, 1.0)
        }
        // Fudge senses
        for ( (word, index) <- wordIndexer.objectsList.zipWithIndex ) {
          val synsets = jaws.getSynsets(word)
          for (sense <- 1 to math.min(31, if (synsets != null) synsets.length else 0)) {
            edge(EdgeType.SENSE_ADD, index, 0, index, sense, 1.0)
            edge(EdgeType.SENSE_REMOVE, index, sense, index, 0, 1.0)

          }
        }

        //
        // Flushing to DB
        //
        println("[70] Flushing")
        println("  words...")
        for ( (word, index) <- wordIndexer.objectsList.zipWithIndex ) {
          wordInsert.setInt(1, index)
          wordInsert.setString(2, word.replaceAll("\u0000", ""))
          wordInsert.addBatch()
        }
        wordInsert.executeBatch
        println("  edge types...")
        for (edgeType <- EdgeType.values) {
          edgeTypeInsert.setInt(1, edgeType.id)
          edgeTypeInsert.setString(2, edgeType.toString.replaceAll("\u0000", ""))
          edgeTypeInsert.executeUpdate
        }
        println("  edges...")
        edgeInsert.executeBatch()

        println("DONE.")
      }
    }), args)
  }

}
