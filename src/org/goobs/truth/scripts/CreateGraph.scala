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
import org.goobs.sim.Ontology.RealNode
import scala.util.matching

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

  def getSense(word:Int, synset:Synset, senses:Map[Int,Array[Synset]]):Option[Int] = {
    senses.get(word) match {
      case Some(synsets) =>
        val index = synsets.indexOf(synset)
        if (index < 0) {
          System.out.println("  [WARN] unknown synset: " + synset)
          None
        }
        if (index > 30) None else Some(index + 1)
      case None => None
    }
  }

  def main(args:Array[String]) = {
    Props.exec(fn2execInput1(() => {
      // Some global stuff
      println("Started")
      wordIndexer.indexOf(Utils.WORD_NONE, true)
      wordIndexer.indexOf(Utils.WORD_UNK,  true)
      for (i <- 0 until 10) {
        wordIndexer.indexOf(Utils.mkUNK(i),  true)

      }
      implicit val jaws = WordNetDatabase.getFileInstance
      val wordnet       = Ontology.load(Props.SCRIPT_WORDNET_PATH)

      withConnection { (psql: Connection) =>
        // Create tables
        psql.prepareStatement("""DROP TABLE IF EXISTS word CASCADE;""").execute()
        psql.prepareStatement("""CREATE TABLE word ( index INTEGER PRIMARY KEY, gloss TEXT );""").execute()
        psql.prepareStatement("""DROP TABLE IF EXISTS edge_type CASCADE;""").execute()
        psql.prepareStatement("""CREATE TABLE edge_type ( index SMALLINT PRIMARY KEY, gloss TEXT );""").execute()
        psql.prepareStatement("""DROP TABLE IF EXISTS edge CASCADE;""").execute()
        psql.prepareStatement("""CREATE TABLE edge ( source INTEGER, source_sense INTEGER, sink INTEGER, sink_sense INTEGER, type SMALLINT, cost REAL );""").execute()
        psql.prepareStatement("""DROP TABLE IF EXISTS word_sense CASCADE;""").execute()
        psql.prepareStatement("""CREATE TABLE word_sense ( index INTEGER, sense INTEGER, definition TEXT);""").execute()
        psql.prepareStatement("""CREATE VIEW graph AS (SELECT source.gloss AS source, e.source_sense AS source_sense, t.gloss AS relation, sink.gloss AS sink, e.sink_sense AS sink_sense, e.cost AS cost FROM word source, word sink, edge e, edge_type t WHERE e.source=source.index AND e.sink=sink.index AND e.type=t.index);""").execute()
        psql.prepareStatement("""DROP TABLE IF EXISTS privative CASCADE;""").execute()

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
        val senseInsert = psql.prepareStatement(
          "INSERT INTO " + Postgres.TABLE_WORD_SENSE +
            " (index, sense, definition) VALUES (?, ?, ?);")

        // Function to add an edge
        var edgesAdded = new mutable.HashSet[(Int, Int, Int, Int, Int, Float)]
        def edge(t: EdgeType, source: Int, sourceSenseOption: Option[Int], sink: Int, sinkSenseOption: Option[Int], weight: Double): Unit = {
          for (sourceSense: Int <- sourceSenseOption;
               sinkSense: Int <- sinkSenseOption) {
            val key = (source, sourceSense, sink, sinkSense, t.id, weight.toFloat)
            if (!edgesAdded(key)) {
              edgeInsert.setInt(1, source)
              edgeInsert.setInt(2, sourceSense)
              edgeInsert.setInt(3, sink)
              edgeInsert.setInt(4, sinkSense)
              edgeInsert.setInt(5, t.id)
              edgeInsert.setFloat(6, weight.toFloat)
              edgeInsert.addBatch()
              edgesAdded += key
            }
          }
        }
        def edgeII(t: EdgeType, source: Int, sourceSense: Int, sink: Int, sinkSense: Int, weight: Double): Unit = {
          edge(t, source, Some(sourceSense), sink, Some(sinkSense), weight)
        }
        def edgeIO(t: EdgeType, source: Int, sourceSense: Int, sink: Int, sinkSenseOption: Option[Int], weight: Double): Unit = {
          edge(t, source, Some(sourceSense), sink, sinkSenseOption, weight)
        }
        def edgeOI(t: EdgeType, source: Int, sourceSenseOption: Option[Int], sink: Int, sinkSense: Int, weight: Double): Unit = {
          edge(t, source, sourceSenseOption, sink, Some(sinkSense), weight)
        }

        // Preparation
        println("[05] Sense Preparation")
        val synsetsByLemma = new mutable.HashMap[(Int,SynsetType), Array[Synset]]
        for ((phrase: Seq[String], nodes: Set[Ontology.RealNode]) <- wordnet.ontology.toArray.sortBy(_._1.toString())) {
          for (node: Ontology.RealNode <- nodes) {
            val gloss = phrase.mkString(" ")
            for ( (phraseGloss, trustCase) <- Seq((node.synset.getWordForms.find(_.toLowerCase == gloss).getOrElse(gloss), true), (gloss, false) ) ) {
              val index: Int = indexOf(phraseGloss, trustCase)
              val synsets:Array[Synset] = jaws.getSynsets(phraseGloss)
              for ( (synsetType, synsets) <- synsets.groupBy( _.getType ) ) {
                val existingSynsets: Array[Synset] = synsetsByLemma.get( (index, synsetType) ).getOrElse(Array[Synset]())
                val newSynsets = existingSynsets.map(Some(_)).zipAll(synsets.map(Some(_)), None, None).foldLeft(List[Synset]()){
                  case (lst, (Some(a), Some(b))) => a :: b :: lst
                  case (lst, (Some(a), None)) =>    a :: lst
                  case (lst, (None, Some(b))) =>    b :: lst
                  case _ => throw new IllegalStateException()
                }.reverse.distinct.toArray
                synsetsByLemma( (index, synsetType) ) = newSynsets
              }

            }
          }
        }
        val wordSenses: Map[Int, Array[Synset]] = synsetsByLemma
          .map{ case ((w:Int, t:SynsetType), synsets:Array[Synset]) => w}
          .map{ (word:Int) =>
          (word, SynsetType.ALL_TYPES.map{ x => synsetsByLemma.get( (word, x) ).getOrElse(Array()) }.flatten)
        }.toMap
        synsetsByLemma.clear()
        println("[07] Writing senses...")
        for ( (w, synsets:Array[Synset]) <- wordSenses ) {
          for ( (synset:Synset, sense) <- synsets.zipWithIndex) {
            senseInsert.setInt(1, w)
            senseInsert.setInt(2, sense)
            senseInsert.setString(3, synset.getDefinition)
            senseInsert.addBatch()
          }
        }
        senseInsert.executeBatch()

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
            val sense:Option[Int] = getSense(index, node.synset, wordSenses)

            // Add hyper/hypo-nyms
            for (x:Ontology.Node <- node.hypernyms.toArray.sortBy( _.toString() )) x match { case (hyperNode:Ontology.RealNode) =>
              val edgeWeight = scala.math.log(
                hyperNode.subtreeCount.toDouble / node.subtreeCount.toDouble )
              assert(edgeWeight >= 0.0)

              for (hyperWord <- hyperNode.synset.getWordForms) {
                val hyperInt:Int = indexOf(hyperWord)
                val hyperSense:Option[Int] = getSense(hyperInt, hyperNode.synset, wordSenses)
                if (!hyperSense.isDefined) {
                  System.out.println("  [WARN] unknown word: " + hyperWord + " (hypernym of " + phraseGloss + ")")
                }
                edge(EdgeType.WORDNET_UP, index, sense, hyperInt, hyperSense, edgeWeight)
                edge(EdgeType.WORDNET_DOWN, hyperInt, hyperSense, index, sense, edgeWeight)
              }
            case _ => }

            // Other WordNet edges
            node.synset match {
              case (as:NounSynset) =>
                for (antonym <- as.getAntonyms(phraseGloss)) {
                  if (!Quantifier.quantifierGlosses.contains(antonym.getWordForm.toLowerCase) ||
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    val antIndex = indexOf(antonym.getWordForm)
                    edge(EdgeType.WORDNET_NOUN_ANTONYM, index, sense, antIndex, getSense(antIndex, antonym.getSynset, wordSenses), 1.0)
                  }
                }
                if (!Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                  if (!Utils.INTENSIONAL_ADJECTIVES.contains(phraseGloss.toLowerCase) &&
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    edgeOI(EdgeType.DEL_NOUN, index, sense, 0, 0, 1.0)
                  }
                  edgeIO(EdgeType.ADD_NOUN, 0, 0, index, sense, 1.0)
                }
              case (as:VerbSynset) =>
                for (antonym <- as.getAntonyms(phraseGloss)) {
                  val antIndex = indexOf(antonym.getWordForm)
                  edge(EdgeType.WORDNET_VERB_ANTONYM, index, sense, antIndex, getSense(antIndex, antonym.getSynset, wordSenses), 1.0)
                }
                val isAuxilliary:Boolean = Utils.AUXILLIARY_VERBS.contains(phraseGloss.toLowerCase)
                if (!Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                  if (!Utils.INTENSIONAL_ADJECTIVES.contains(phraseGloss.toLowerCase) &&
                    !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    edgeOI(if (isAuxilliary) EdgeType.DEL_OTHER else EdgeType.DEL_VERB, index, sense, 0, 0, 1.0)
                  }
                  edgeIO(if (isAuxilliary) EdgeType.ADD_OTHER else EdgeType.ADD_VERB, 0, 0, index, sense, 1.0)
                }
              case (as:AdjectiveSynset) =>
                for (related <- as.getSimilar;
                     wordForm <- related.getWordForms) {
                  if (!Quantifier.quantifierGlosses.contains(wordForm.toLowerCase) ||
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    val relatedIndex = indexOf(wordForm)
                    edge(EdgeType.WORDNET_ADJECTIVE_RELATED, index, sense, relatedIndex, getSense(relatedIndex, related, wordSenses), 1.0)
                  }
                }
                for (pertainym <- as.getPertainyms(phraseGloss)) {
                  if (!Quantifier.quantifierGlosses.contains(pertainym.getWordForm.toLowerCase) ||
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    val relatedIndex = indexOf(pertainym.getWordForm)
                    edge(EdgeType.WORDNET_ADJECTIVE_PERTAINYM, index, sense, relatedIndex, getSense(relatedIndex, pertainym.getSynset, wordSenses), 1.0)
                  }
                }
                for (antonym <- as.getAntonyms(phraseGloss)) {
                  if (!Quantifier.quantifierGlosses.contains(antonym.getWordForm.toLowerCase) ||
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    val antIndex = indexOf(antonym.getWordForm)
                    edge(EdgeType.WORDNET_ADJECTIVE_ANTONYM, index, sense, antIndex, getSense(antIndex, antonym.getSynset, wordSenses), 1.0)
                  }
                }
                if (!Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                  if (!Utils.INTENSIONAL_ADJECTIVES.contains(phraseGloss.toLowerCase) &&
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    edgeIO(EdgeType.ADD_ADJ, 0, 0, index, sense, 1.0)
                    edgeOI(EdgeType.DEL_ADJ, index, sense, 0, 0, 1.0)
                  }
                }
              case (as:AdverbSynset) =>
                for (pertainym: WordSense <- as.getPertainyms(phraseGloss)) {
                  if (!Quantifier.quantifierGlosses.contains(pertainym.getWordForm.toLowerCase) ||
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    val pertIndex = indexOf(pertainym.getWordForm)
                    edge(EdgeType.WORDNET_ADVERB_PERTAINYM, index, sense, pertIndex, getSense(pertIndex, pertainym.getSynset, wordSenses), 1.0)
                  }
                }
                for (antonym: WordSense <- as.getAntonyms(phraseGloss)) {
                  if (!Quantifier.quantifierGlosses.contains(antonym.getWordForm.toLowerCase) ||
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    val antIndex = indexOf(antonym.getWordForm)
                    edge(EdgeType.WORDNET_ADVERB_ANTONYM, index, sense, antIndex, getSense(antIndex, antonym.getSynset, wordSenses), 1.0)
                  }
                }
                if (!Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                  if (!Utils.INTENSIONAL_ADJECTIVES.contains(phraseGloss.toLowerCase) &&
                      !Quantifier.quantifierGlosses.contains(phraseGloss.toLowerCase)) {
                    edgeOI(EdgeType.DEL_OTHER, index, sense, 0, 0, 1.0)
                  }
                  edgeIO(EdgeType.ADD_OTHER, 0, 0, index, sense, 1.0)
                }
              case _ =>
            }
          }
        }
        // Synonyms
        case class SynonymPair(a:String, aSense:Option[Int], b:String, bSense:Option[Int])
        val synonyms = new mutable.HashSet[SynonymPair]
        for ( node: RealNode <- wordnet.ontology.values.flatten ) {
          for ( word:String <- node.synset.getWordForms ) {
            val wordIndex = indexOf(word)
            for ( synonym:String <- node.synset.getWordForms ) {
              if (word != synonym) {
                val synIndex = indexOf(synonym)
                synonyms.add(SynonymPair(word, getSense(wordIndex, node.synset, wordSenses),
                  synonym, getSense(synIndex, node.synset, wordSenses)))
              }
            }
          }
        }
        for ( pair <- synonyms ) {
          edge(WORDNET_NOUN_SYNONYM, indexOf(pair.a, trustCase = true), pair.aSense, indexOf(pair.b, trustCase = true), pair.bSense, 1.0)
        }

        //
        // Nearest Neighbors
        //
        println("[20] Nearest Neighbors")
        val NUMBER: matching.Regex = """[0-9]+(\.[0-9]*)?""".r
        for (line <- Source.fromFile(Props.SCRIPT_DISTSIM_COS, "UTF-8").getLines()) {
          val fields = line.split("\t")
          fields(0) match {
            case NUMBER(_) => // noop; don't nearest neighbors numbers
            case _ => // noop; don't nearest neighbors numbers
              val source:Int = indexOf(fields(0))
              for (i <- 1 until fields.length) {
                val scoreAndGloss = fields(i).split(" ")
                assert(scoreAndGloss.length == 2)
                scoreAndGloss(1) match {
                  case NUMBER(_) => // noop; don't nearest neighbors numbers
                  case _ =>
                    val sink:Int = indexOf(scoreAndGloss(1))
                    val angle:Double = math.acos( scoreAndGloss(0).toDouble ) / scala.math.Pi
                    assert(angle >= 0.0)
                    if (!Quantifier.quantifierGlosses.contains(scoreAndGloss(1).toLowerCase) ||
                      !Quantifier.quantifierGlosses.contains(fields(0).toLowerCase)) {  // don't add quantifier replacements
                      edgeII(EdgeType.ANGLE_NEAREST_NEIGHBORS, source, 0, sink, 0, angle)
                    }
                }
              }
          }
        }

        //
        // Quantifier Replacement
        //
        println("[30] Quantifier Replacement")
        val quantifiers: Set[Int] = (for ( source <- Quantifier.values()) yield {
          // Allow swapping
          val sourceIndexed:Int = indexOf(source.surfaceForm.mkString(" "))
          for (sink <- Quantifier.values()) {
            val sinkIndexed:Int = indexOf(sink.surfaceForm.mkString(" "))
            if (sourceIndexed != sinkIndexed) {
              if (source.closestMeaning.isComparableTo(sink.closestMeaning)) {
                if (source.closestMeaning.denotationLessThan(sink.closestMeaning)) {
                  edgeII(EdgeType.QUANTIFIER_UP, sourceIndexed, 0, sinkIndexed, 0, 1.0)
                } else if (sink.closestMeaning.denotationLessThan(source.closestMeaning)) {
                  edgeII(EdgeType.QUANTIFIER_DOWN, sourceIndexed, 0, sinkIndexed, 0, 1.0)
                } else if (source.closestMeaning.equals(sink.closestMeaning)) {
                  edgeII(EdgeType.QUANTIFIER_REWORD, sourceIndexed, 0, sinkIndexed, 0, 1.0)
                }
              }
              if (source.closestMeaning.isNegationOf(sink.closestMeaning)) {
                edgeII(EdgeType.QUANTIFIER_NEGATE, sourceIndexed, 0, sinkIndexed, 0, 1.0)
              }
            }
          }
          // Allow insertion/deletion
          source.closestMeaning match {
            case Quantifier.LogicalQuantifier.FORALL =>
              edgeII(EdgeType.ADD_UNIVERSAL, 0, 0, sourceIndexed, 0, 1.0)
              edgeII(EdgeType.DEL_UNIVERSAL, sourceIndexed, 0, 0, 0, 1.0)
            case Quantifier.LogicalQuantifier.EXISTS =>
              edgeII(EdgeType.ADD_EXISTENTIAL, 0, 0, sourceIndexed, 0, 1.0)
              edgeII(EdgeType.DEL_EXISTENTIAL, sourceIndexed, 0, 0, 0, 1.0)
            case Quantifier.LogicalQuantifier.FEW =>
              edgeII(EdgeType.ADD_QUANTIFIER_OTHER, 0, 0, sourceIndexed, 0, 1.0)
              edgeII(EdgeType.DEL_QUANTIFIER_OTHER, sourceIndexed, 0, 0, 0, 1.0)
            case Quantifier.LogicalQuantifier.MOST =>
              edgeII(EdgeType.ADD_QUANTIFIER_OTHER, 0, 0, sourceIndexed, 0, 1.0)
              edgeII(EdgeType.DEL_QUANTIFIER_OTHER, sourceIndexed, 0, 0, 0, 1.0)
            case Quantifier.LogicalQuantifier.NONE =>
              edgeII(EdgeType.ADD_NEGATION, 0, 0, sourceIndexed, 0, 1.0)
              edgeII(EdgeType.DEL_NEGATION, sourceIndexed, 0, 0, 0, 1.0)
          }
          sourceIndexed
        }).toSet

        //
        // Senseless Insertions/Deletions
        //
        println("[40] Senseless insert/delete")
        for ( (index, pos) <- posCache) {
          if (!quantifiers.contains(index)) {
            pos match {
              case 'N' =>
                if (!Utils.INTENSIONAL_ADJECTIVES.contains(wordIndexer.get(index).toLowerCase) &&
                    !Quantifier.quantifierGlosses.contains(wordIndexer.get(index).toLowerCase)) {
                  edgeII(EdgeType.DEL_NOUN, index, 0, 0, 0, 1.0)
                }
                edgeII(EdgeType.ADD_NOUN, 0, 0, index, 0, 1.0)
              case 'V' =>
                val isAuxilliary:Boolean = Utils.AUXILLIARY_VERBS.contains(wordIndexer.get(index).toLowerCase)
                if (!Utils.INTENSIONAL_ADJECTIVES.contains(wordIndexer.get(index).toLowerCase) &&
                    !Quantifier.quantifierGlosses.contains(wordIndexer.get(index).toLowerCase)) {
                  edgeII(if (isAuxilliary) EdgeType.DEL_OTHER else EdgeType.DEL_VERB, index, 0, 0, 0, 1.0)
                }
                edgeII(if (isAuxilliary) EdgeType.DEL_OTHER else EdgeType.ADD_VERB, 0, 0, index, 0, 1.0)
              case 'J' =>
                if (!Utils.INTENSIONAL_ADJECTIVES.contains(wordIndexer.get(index).toLowerCase) &&
                    !Quantifier.quantifierGlosses.contains(wordIndexer.get(index).toLowerCase)) {
                  edgeII(EdgeType.DEL_ADJ, index, 0, 0, 0, 1.0)
                  edgeII(EdgeType.ADD_ADJ, 0, 0, index, 0, 1.0)
                }
              case _ =>
                if (!Utils.INTENSIONAL_ADJECTIVES.contains(wordIndexer.get(index).toLowerCase) &&
                    !Quantifier.quantifierGlosses.contains(wordIndexer.get(index).toLowerCase)) {
                  edgeII(EdgeType.DEL_OTHER, index, 0, 0, 0, 1.0)
                }
                edgeII(EdgeType.ADD_OTHER, 0, 0, index, 0, 1.0)
            }
          }
        }

        //
        // Morphology
        //
        println("[50] Morphology (skipping)")
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
          edgeII(EdgeType.MORPH_FUDGE_NUMBER, lower, 0, higher, 0, 1.0)
          edgeII(EdgeType.MORPH_FUDGE_NUMBER, higher, 0, lower, 0, 1.0)
        }
        // Fudge senses
        for ( (word, index) <- wordIndexer.objectsList.zipWithIndex ) {
          val synsets = jaws.getSynsets(word)
          for (sense <- 1 to math.min(31, if (synsets != null) synsets.length else 0)) {
            edgeII(EdgeType.SENSE_ADD, index, 0, index, sense, 1.0)
            edgeII(EdgeType.SENSE_REMOVE, index, sense, index, 0, 1.0)

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
        println("  privative table...")
        psql.prepareStatement("""CREATE INDEX word_gloss ON word(gloss);""").execute()
        psql.prepareStatement("""CREATE INDEX edge_outgoing ON edge (source, source_sense);""").execute()
        psql.prepareStatement("""CREATE INDEX edge_incoming ON edge (sink, sink_sense);""").execute()
        psql.prepareStatement("""CREATE INDEX edge_source ON edge (source);""").execute()
        psql.prepareStatement("""CREATE INDEX edge_sink ON edge (sink);""").execute()
        psql.prepareStatement("""CREATE INDEX edge_type_index ON edge (type);""").execute()
        psql.prepareStatement("""CREATE TABLE privative AS (SELECT DISTINCT source, source_sense FROM edge e1 WHERE source <> 0 AND NOT EXISTS (SELECT * FROM edge e2 WHERE e2.source=e1.source AND e2.source_sense=e1.source_sense AND e2.sink=0));""").execute()

        println("DONE.")
      }
    }), args)
  }

}
