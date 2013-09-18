package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection
import java.sql.ResultSet

import edu.stanford.nlp.io.IOUtils._
import edu.stanford.nlp.util.HashIndex
import edu.stanford.nlp._
import edu.stanford.nlp.Magic._
import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import edu.smu.tspell.wordnet._

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._
import org.goobs.truth.Utils._
import org.goobs.truth.Edges._

/**
 *
 * The entry point for constructing graph edges, once the node list
 * is fully populated.
 *
 * @author Gabor Angeli
 */
object CreateEdges {
  val jaws = WordNetDatabase.getFileInstance(); 
  private val logger = Redwood.channels("Edges")


  def handleWords:Map[Int, String] = {
    var map = new scala.collection.mutable.HashMap[Int, String]
    slurpTable(TABLE_WORD_INTERN, {(r:ResultSet) =>
      val key:Int = r.getInt("key")
      val gloss:String = r.getString("gloss")
      map(key) = gloss
    })
    map.toMap
  }

  def wordnet(phrase:Sentence, register:(Seq[String], String) => Unit) = {
    val gloss = phrase.words.mkString(" ")
    for (elem <- jaws.getSynsets(gloss, pos2synsetType(phrase.pos(0))) ) {
      for (antonyms <- Option(elem.getAntonyms(gloss));
           wordForm <- antonyms) {
        register(wordForm.getWordForm.split("""\s+"""), WORDNET_ANTONYM)
      }
      elem match {
        case (ns:NounSynset) =>
          for (hyper <- ns.getHypernyms;
               wordForm <- hyper.getWordForms) {
            register(wordForm.split("""\s+"""), WORDNET_NOUN_HYPERNYM)
          }
          for (hyper <- ns.getInstanceHypernyms;
               wordForm <- hyper.getWordForms) {
            register(wordForm.split("""\s+"""), WORDNET_NOUN_INSTANCE_HYPERNYM)
          }
          for (hypo <- ns.getHyponyms;
               wordForm <- hypo.getWordForms) {
            register(wordForm.split("""\s+"""), WORDNET_NOUN_HYPONYM)
          }
          for (hypo <- ns.getInstanceHyponyms;
               wordForm <- hypo.getWordForms) {
            register(wordForm.split("""\s+"""), WORDNET_NOUN_INSTANCE_HYPONYM)
          }
        case (vs:VerbSynset) =>
          for (hyper <- vs.getHypernyms;
               wordForm <- hyper.getWordForms) {
            register(wordForm.split("""\s+"""), WORDNET_VERB_HYPERNYM)
          }
          for (hypo <- vs.getTroponyms;
               wordForm <- hypo.getWordForms) {
            register(wordForm.split("""\s+"""), WORDNET_VERB_HYPONYM)
          }
        case (as:AdjectiveSynset) =>
          for (related <- as.getSimilar;
               wordForm <- related.getWordForms) {
            register(wordForm.split("""\s+"""), WORDNET_ADJECTIVE_RELATED)
          }
          for (pertainym <- as.getPertainyms(gloss)) {
            register(pertainym.getWordForm.split("""\s+"""), WORDNET_ADJECTIVE_PERTAINYM)
          }
          val participle = as.getParticiple(gloss)
          if (participle != null) {
            register(participle.getWordForm.split("""\s+"""), WORDNET_ADJECTIVE_PARTICIPLE)
          }
        case (as:AdverbSynset) =>
          for (pertainym <- as.getPertainyms(gloss)) {
            register(pertainym.getWordForm.split("""\s+"""), WORDNET_ADVERB_PERTAINYM)
          }
      }
    }
  }
  
  def quantifiers(phrase:Sentence, register:(Seq[String], String) => Unit) = {
  }

  /**
   * Returns a set of edges going out of a particular phrase.
   * An edge is parameterized by the destination (Seq[String]), and
   * the edge type (String).
   */
  def edges(phrase:Sentence):TraversableOnce[(Seq[String], String)] = {
    val registeredEdges = new scala.collection.mutable.HashSet[(Seq[String], String)]()
    wordnet(phrase, (target, typ) => registeredEdges.add( (target, typ) ))
    return registeredEdges
  }

  def handlePhrases(wordIndex:Map[Int, String],
                    handleEdge:(Int,Int,String)=>Any):Unit = {
    val reverseWordIndex:Map[String,Int] = wordIndex.map{ case (x, y) => (y, x) }.toMap
    var totalPhrases = 0
    var phrasesWithEdge = 0
    var totalEdges:Long = 0
    slurpTable(TABLE_PHRASE_INTERN, {(r:ResultSet) =>
      val phrase:Array[Int] = r.getArray("words").getArray.asInstanceOf[Array[Integer]].map( x => x.asInstanceOf[Int] );
      var hasEdge = false
      for ( (target, edgeType) <- edges(Sentence(phrase.map( wordIndex(_) ) )) ) {
        if (target.forall(reverseWordIndex.contains(_))) {
          totalEdges += 1
          hasEdge = true
          val targetPhrase:Array[Int] = target.toArray.map( reverseWordIndex(_) )
          // TODO(gabor) actually add the edge
//          logger.debug(phrase.map( wordIndex(_) ).mkString(" ") +
//                       " -- " + edgeType + "--> " +
//                       target.mkString(" "))
        } else {
          logger.warn("phrase has unknown word: " + target.mkString(" "))
        }
      }
      totalPhrases += 1
      if (hasEdge) phrasesWithEdge += 1;
      if (totalPhrases % 1000 == 0) {
        logger.log("handled " + totalPhrases + " phrases " +
                   "[" + (totalPhrases - phrasesWithEdge) + " without outgoing edges] " +
                   "[" + totalEdges + " edges; " + (totalEdges.toDouble / totalPhrases.toDouble) + " edges / phrase] ")
      }
    })
  }

  def main(args:Array[String]) = {
    Props.exec(() => {
      forceTrack("Reading words")
      val wordIndex:Map[Int, String] = handleWords
      endTrack("Reading words")
      forceTrack("Constructing edges")
      handlePhrases(wordIndex, { case (start:Int, end:Int, name:String) =>
        7
      })
      endTrack("Constructing edges")
    }, args)
  }
}
