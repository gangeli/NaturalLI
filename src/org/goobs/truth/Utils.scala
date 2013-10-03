package org.goobs.truth

import edu.stanford.nlp._
import edu.stanford.nlp.sequences.SeqClassifierFlags
import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import edu.smu.tspell.wordnet.SynsetType

import org.goobs.truth.Implicits._

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
    import java.lang.Character
    // Construct a fake sentence
    val offset = 3
    val lowercaseWords = phrase.map( _.toLowerCase )
    val lowercaseSent = new Array[String](lowercaseWords.length + 6)
    System.arraycopy(lowercaseWords, 0, lowercaseSent, offset, lowercaseWords.length)
    lowercaseSent(0) = "we"
    lowercaseSent(1) = "see"
    lowercaseSent(2) = "that"
    lowercaseSent(lowercaseWords.length + offset + 0) = "is"
    lowercaseSent(lowercaseWords.length + offset + 0) = "blue"
    lowercaseSent(lowercaseWords.length + offset + 0) = "."
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
}
