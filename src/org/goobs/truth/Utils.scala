package org.goobs.truth

import edu.stanford.nlp._
import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import edu.smu.tspell.wordnet.SynsetType

import org.goobs.truth.Implicits._
import org.goobs.truth.Postgres.{slurpTable, TABLE_WORD_INTERN}
import gnu.trove.map.hash.TObjectIntHashMap
import java.sql.ResultSet
import gnu.trove.map.TObjectIntMap

object Utils {
  NLPConfig.truecase.bias = "INIT_UPPER:-0.7,UPPER:-2.5,O:0"
  private val logger = Redwood.channels("Utils")

  def pos2synsetType(pos:String):SynsetType = pos match {
    case r"""[Nn].*""" => SynsetType.NOUN
    case r"""[Vv].*""" => SynsetType.VERB
    case r"""[Jj].*""" => SynsetType.ADJECTIVE
    case r"""[Rr].*""" => SynsetType.ADVERB
    case _ => logger.debug("Unknown POS: " + pos); SynsetType.NOUN
  }
  
  val Whitespace = """\s+""".r
  val Roman_Numeral = """^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$""".r
  
  def tokenizeWithCase(phrase:Array[String], headWord:Option[String=>Any]=None):Array[String] = {
    // Construct a fake sentence
    val offset = 3
    val lowercaseWords = phrase.map( _.toLowerCase )
    val lowercaseSent = new Array[String](lowercaseWords.length + 6)
    System.arraycopy(lowercaseWords, 0, lowercaseSent, offset, lowercaseWords.length)
    lowercaseSent(0) = "we"
    lowercaseSent(1) = "see"
    lowercaseSent(2) = "that"
    lowercaseSent(lowercaseWords.length + offset + 0) = "is"
    lowercaseSent(lowercaseWords.length + offset + 1) = "blue"
    lowercaseSent(lowercaseWords.length + offset + 2) = "."
    val sentence = Sentence(lowercaseSent)
    for (fn <- headWord) { 
      if (phrase.length == 0) { }
      else if (phrase.length == 1) { fn(phrase(0)) }
      else {
        val headIndex = sentence.headIndex(offset, offset + phrase.length)
        fn(phrase(offset + headIndex))
      }
    }
    // Tokenize
    if (lowercaseWords.length == 0) { 
      new Array[String](0)
    } else {
      sentence.truecase.slice(offset, offset + phrase.length)
    }
  }
  
  def tokenizeWithCase(phrase:String):Array[String] = {
    tokenizeWithCase(Sentence(phrase).words, None)
  }
  
  def tokenizeWithCase(phrase:String, headWord:String=>Any):Array[String] = {
    tokenizeWithCase(Sentence(phrase).words, Some(headWord))
  }

  def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }

  def index(rawPhrase:String, doHead:Boolean=false, allowEmpty:Boolean=false)
           (implicit contains:String=>Boolean, wordIndexer:String=>Int) :Option[(Array[Int],Int)] = {
    if (!allowEmpty && rawPhrase.trim.equals("")) { return None }
    var headWord:Option[String] = None
    val phrase:Array[String]
    = if (doHead && Props.SCRIPT_REVERB_HEAD_DO) tokenizeWithCase(rawPhrase, (hw:String) => headWord = Some(hw))
    else tokenizeWithCase(rawPhrase)
    // Create object to store result
    val indexResult:Array[Int] = new Array[Int](phrase.length)
    for (i <- 0 until indexResult.length) { indexResult(i) = -1 }

    for (length <- phrase.length until 0 by -1;
         start <- 0 to phrase.length - length) {
      var found = false
      // Find largest phrases to index into (case sensitive)
      if ( (start until (start + length)) forall (indexResult(_) < 0) ) {
        val candidate:String = phrase.slice(start, start+length).mkString(" ")
        if (contains(candidate)) {
          val index = wordIndexer(candidate)
          for (i <- start until start + length) { indexResult(i) = index; }
          found = true
        }
      }
      if (length > 1 && !found) {
        // Try to title-case (if it was title cased to begin with)
        val candidate:String = phrase.slice(start, start+length)
          .map( (w:String) => if (w.length <= 1) w.toUpperCase
        else w.substring(0, 1).toUpperCase + w.substring(1) )
          .mkString(" ")
        if (rawPhrase.contains(candidate) &&  // not _technically_ sufficent, but close enough
          contains(candidate)) {
          val index = wordIndexer(candidate)
          for (i <- start until start + length) { indexResult(i) = index; }
          found = true
        }
      }
    }

    // Find any dangling singletons
    for (length <- phrase.length until 0 by -1;
         start <- 0 to phrase.length - length) {
      if ( (start until (start + length)) forall (indexResult(_) < 0) ) {
        val candidate:String = phrase.slice(start, start+length).mkString(" ")
        if (contains(candidate.toLowerCase)) {
          val index = wordIndexer(candidate.toLowerCase)
          for (i <- start until start + length) { indexResult(i) = index; }
        }
      }
    }

    // Find head word index
    val headWordIndexed:Int =
      (for (hw <- headWord) yield {
        if (contains(hw)) { Some(wordIndexer(hw)) }
        else if (contains(hw.toLowerCase)) { Some(wordIndexer(hw.toLowerCase)) }
        else { None }
      }).flatten.getOrElse(-1)

    // Create resulting array
    var lastElem:Int = -999
    var rtn = List[Int]()
    for (i <- indexResult.length - 1 to 0 by -1) {
      if (indexResult(i) < 0) { return None }
      if (indexResult(i) != lastElem) {
        lastElem = indexResult(i)
        rtn = indexResult(i) :: rtn
      }
    }
    Some( (rtn.toArray, headWordIndexed) )
  }

  lazy val (wordIndexer, wordGloss):(TObjectIntHashMap[String],Array[String]) = {
    startTrack("Reading Word Index")
    val wordIndexer = new TObjectIntHashMap[String]
    var count = 0
    slurpTable(TABLE_WORD_INTERN, {(r:ResultSet) =>
      val key:Int = r.getInt("index")
      val gloss:String = r.getString("gloss")
      wordIndexer.put(gloss, key)
      count += 1
      if (count % 1000000 == 0) {
        logger.log("read " + (count / 1000000) + "M words; " + (Runtime.getRuntime.freeMemory / 1000000) + " MB of memory free")
      }
    })
    endTrack("Reading Word Index")
    (wordIndexer, wordIndexer.keys(new Array[String](wordIndexer.size())))
  }

}

object EdgeType extends Enumeration {
  type EdgeType = Value
  val WORDNET_UP                     = Value(0,  "wordnet_up")
  val WORDNET_DOWN                   = Value(1,  "wordnet_down")
  val WORDNET_NOUN_ANTONYM           = Value(2,  "wordnet_noun_antonym")
  val WORDNET_VERB_ANTONYM           = Value(3,  "wordnet_verb_antonym")
  val WORDNET_ADJECTIVE_ANTONYM      = Value(4,  "wordnet_adjective_antonym")
  val WORDNET_ADVERB_ANTONYM         = Value(5,  "wordnet_adverb_antonym")
  val WORDNET_ADJECTIVE_PERTAINYM    = Value(6,  "wordnet_adjective_pertainym")
  val WORDNET_ADVERB_PERTAINYM       = Value(7,  "wordnet_adverb_pertainym")
  val WORDNET_ADJECTIVE_RELATED      = Value(8,  "wordnet_adjective_related")
  
  val ANGLE_NEAREST_NEIGHBORS        = Value(9,  "angle_nn")
  
  val FREEBASE_UP                    = Value(10, "freebase_up")
  val FREEBASE_DOWN                  = Value(11, "freebase_down")
  
  // Could in theory be subdivided: tense, plurality, etc.
  val MORPH_TO_LEMMA                 = Value(12, "morph_to_lemma")
  val MORPH_FROM_LEMMA               = Value(13, "morph_from_lemma")
  val MORPH_FUDGE_NUMBER             = Value(14, "morph_fudge_number")

  // Word Sense Disambiguation
  val SENSE_REMOVE                   = Value(15, "sense_remove")
}
