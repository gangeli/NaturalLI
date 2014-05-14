package org.goobs.truth

import scala.collection.mutable

import edu.stanford.nlp._
import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import edu.smu.tspell.wordnet.SynsetType

import org.goobs.truth.Implicits._
import org.goobs.truth.Postgres.{slurpTable, TABLE_WORD_INTERN}
import gnu.trove.map.hash.TObjectIntHashMap
import java.sql.ResultSet
import gnu.trove.procedure.TObjectIntProcedure

object Utils {
  def f1(guessed: Int, correct: Int, shouldHaveGuessed: Int) = {
    val p = correct.toDouble / guessed.toDouble
    val r = correct.toDouble / shouldHaveGuessed.toDouble
    (2.0 * p * r) / (p + r)
  }

  NLPConfig.truecase.bias = "INIT_UPPER:-0.7,UPPER:-2.5,O:0"
  private val logger = Redwood.channels("Utils")

  val percent = new java.text.DecimalFormat("0.00%")
  val df = new java.text.DecimalFormat("0.0000")

  // Note: match these with CreateGraph's index creation
  val WORD_NONE:String = "__none__"
  val WORD_UNK:String  = "__unk__"
  val AUXILLIARY_VERBS = Set[String](
    "be",
    "can",
    "could",
    "dare",
    "do",
    "have",
    "may",
    "might",
    "must",
    "need",
    "ought",
    "shall",
    "should",
    "will",
    "would"
  )

  val INTENSIONAL_ADJECTIVES = Set[String](
    "believed",
    "debatable",
    "disputed",
    "dubious",
    "hypothetical",
    "impossible",
    "improbable",
    "plausible",
    "putative",
    "questionable",
    "so called",
    "supposed",
    "suspicious",
    "theoretical",
    "uncertain",
    "unlikely",
    "would - be",
    "apparent",
    "arguable",
    "assumed",
    "likely",
    "ostensible",
    "possible",
    "potential",
    "predicted",
    "presumed",
    "probable",
    "seeming",
    "anti",
    "fake",
    "fictional",
    "fictitious",
    "imaginary",
    "mythical",
    "phony",
    "false",
    "artificial",
    "erroneous",
    "mistaken",
    "mock",
    "pseudo",
    "simulated",
    "spurious",
    "deputy",
    "faulty",
    "virtual",
    "doubtful",
    "erstwhile",
    "ex",
    "expected",
    "former",
    "future",
    "onetime",
    "past",
    "proposed"
  )

  def mkUNK(identifier:Int):String = {
    if (identifier >= 10 || identifier < 0) {
      throw new IllegalArgumentException("Can only instantiate up to 10 UNK types")
    }
    "__unk[" + identifier + "]__"
  }

  /**
   * Creates a new unknown word provider.
   * This hsould be created on every (antecedent, consequent) pair to ensure that
   * the antecedents and consequents map to the same unknown word if the words
   * are equal.
   */
  def newUnkProvider:String=>String = {
    val unkMapping = new mutable.HashMap[String, Int]
    (w:String) => {
      unkMapping.get(w) match {
        case Some(index) => Utils.mkUNK(index)
        case None =>
          unkMapping(w) = unkMapping.size
          Utils.mkUNK(unkMapping(w))
      }
    }
  }

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
    tokenizeWithCase(Sentence(phrase).lemma, None)
  }
  
  def tokenizeWithCase(phrase:String, headWord:String=>Any):Array[String] = {
    tokenizeWithCase(Sentence(phrase).lemma, Some(headWord))
  }

  def printToFile(f: java.io.File)(op: java.io.PrintWriter => Any) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }

  def index(rawPhrase:String, doHead:Boolean=false, allowEmpty:Boolean=false)
           (implicit contains:String=>Boolean, wordIndexer:String=>Int, unkProvider:String=>String) :(Array[Int],Int) = {
    if (!allowEmpty && rawPhrase.trim.equals("")) { return (Array[Int](), 0) }
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
        if (length > 1 && !found) {
          // Try to title-case (if it was title cased to begin with)
          val candidate:String = phrase.slice(start, start+length)
            .map( (w:String) => if (w.length <= 1) w.toUpperCase
          else w.substring(0, 1).toUpperCase + w.substring(1) )
            .mkString(" ")
          if (rawPhrase.contains(candidate) &&  // not _technically_ sufficient, but close enough
            contains(candidate)) {
            val index = wordIndexer(candidate)
            for (i <- start until start + length) { indexResult(i) = index; }
            found = true
          }
          if (!found) {
            // Try to lower-case
            if (contains(candidate.toLowerCase)) {
              val index = wordIndexer(candidate.toLowerCase)
              for (i <- start until start + length) { indexResult(i) = index; }
              found = true
            }
          }
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
      if (indexResult(i) < 0) { indexResult(i) = wordIndexer(unkProvider(phrase(i))) }
      if (indexResult(i) != lastElem) {
        lastElem = indexResult(i)
        rtn = indexResult(i) :: rtn
      }
    }
    (rtn.toArray, headWordIndexed)
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
    log("read " + count + " words")
    endTrack("Reading Word Index")
    val reverseIndex = new Array[String](wordIndexer.size())
    wordIndexer.forEachEntry(new TObjectIntProcedure[String] {
      override def execute(p1: String, p2: Int): Boolean = { reverseIndex(p2) = p1; true }
    })
    (wordIndexer, reverseIndex)
  }

}

object TruthValue extends Enumeration {
  type TruthValue = Value
  val TRUE    = Value(0,  "true")
  val FALSE   = Value(1,  "false")
  val UNKNOWN = Value(2,  "unknown")
  val INVALID = Value(3,  "invalid")

}

object EdgeType extends Enumeration {
  type EdgeType = Value
  val WORDNET_UP                     = Value(0,  "wordnet_up")
  val WORDNET_DOWN                   = Value(1,  "wordnet_down")
  val WORDNET_NOUN_ANTONYM           = Value(2,  "wordnet_noun_antonym")
  val WORDNET_NOUN_SYNONYM           = Value(3,  "wordnet_noun_synonym")
  val WORDNET_VERB_ANTONYM           = Value(4,  "wordnet_verb_antonym")
  val WORDNET_ADJECTIVE_ANTONYM      = Value(5,  "wordnet_adjective_antonym")
  val WORDNET_ADVERB_ANTONYM         = Value(6,  "wordnet_adverb_antonym")
  val WORDNET_ADJECTIVE_PERTAINYM    = Value(7,  "wordnet_adjective_pertainym")
  val WORDNET_ADVERB_PERTAINYM       = Value(8,  "wordnet_adverb_pertainym")
  val WORDNET_ADJECTIVE_RELATED      = Value(9,  "wordnet_adjective_related")
  
  val ANGLE_NEAREST_NEIGHBORS        = Value(10,  "angle_nn")
  
  val FREEBASE_UP                    = Value(11, "freebase_up")
  val FREEBASE_DOWN                  = Value(12, "freebase_down")

  val ADD_NOUN                       = Value(13, "add_noun")
  val ADD_VERB                       = Value(14, "add_verb")
  val ADD_ADJ                        = Value(15, "add_adj")
  val ADD_NEGATION                   = Value(16, "add_negation")
  val ADD_EXISTENTIAL                = Value(17, "add_existential")
  val ADD_QUANTIFIER_OTHER           = Value(18, "add_quantifier_other")
  val ADD_UNIVERSAL                  = Value(19, "add_universal")
  val ADD_OTHER                      = Value(20, "add_?")

  val DEL_NOUN                       = Value(21, "del_noun")
  val DEL_VERB                       = Value(22, "del_verb")
  val DEL_ADJ                        = Value(23, "del_adj")
  val DEL_NEGATION                   = Value(24, "del_negation")
  val DEL_EXISTENTIAL                = Value(25, "del_existential")
  val DEL_QUANTIFIER_OTHER           = Value(26, "del_quantifier_other")
  val DEL_UNIVERSAL                  = Value(27, "del_universal")
  val DEL_OTHER                      = Value(28, "del_?")

  // Quantifiers
  val QUANTIFIER_UP                  = Value(29, "quantifier_up")
  val QUANTIFIER_DOWN                = Value(30, "quantifier_down")
  // --------
  // NOTE: Everything under here is monotonicity agnostic
  // --------
  val MONOTONE_INDEPENDENT_BEGIN:Int = 31
  val QUANTIFIER_NEGATE              = Value(31, "quantifier_negate")
  val QUANTIFIER_REWORD              = Value(32, "quantifier_reword")

  // Could in theory be subdivided: tense, plurality, etc.
  val MORPH_FUDGE_NUMBER             = Value(33, "morph_fudge_number")

  // Word Sense Disambiguation
  val SENSE_REMOVE                   = Value(34, "sense_remove")
  val SENSE_ADD                      = Value(35, "sense_add")
}
