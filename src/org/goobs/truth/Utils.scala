package org.goobs.truth

import edu.stanford.nlp._
import edu.stanford.nlp.sequences.SeqClassifierFlags
import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import edu.smu.tspell.wordnet.SynsetType

import org.goobs.truth.Implicits._

object Utils {
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

  private def tokenizeWithCaseImpl(phrase:Array[String], sentence:Sentence,
                                   beginOffset:Int):Array[String] = {
    phrase.zipWithIndex
      .map{ case (word:String, i:Int) =>
        if ((Roman_Numeral findFirstIn word).nonEmpty) {
          // case: special case roman numerals
          word.toUpperCase
        } else if (word.length < 2) {
          // case: single character (take verbatim)
          word
        } else if (Character.isUpperCase(word(0)) &&
                   Character.isLowerCase(word(1))) {
          // case: normalize upper case
          Character.toUpperCase(word(0)).toString + word.substring(1)
        } else {
          // case: run NER
          if (sentence.pos(i + beginOffset) == "NNP") {
            // case: a proper noun
            if (word.toUpperCase == word) { word }  // maybe an acronym
            else {
              // force title case
              Character.toUpperCase(word(0)).toString + word.substring(1)
            }
          } else {
            // lowercase all non-proper-nouns
            word.toLowerCase
          }
        }
      }
  }
  
  def tokenizeWithCaseImpl(phrase:Array[String], headWord:Option[String=>Any]=None):Array[String] = {
    import java.lang.Character
    // Construct a fake sentence
    val lowercaseWords = phrase.map( _.toLowerCase )
    val lowercaseSent = new Array[String](lowercaseWords.length + 3)
    lowercaseSent(0) = "the"
    System.arraycopy(lowercaseWords, 0, lowercaseSent, 1, lowercaseWords.length)
    lowercaseSent(1 + lowercaseWords.length + 0) = "is"
    lowercaseSent(1 + lowercaseWords.length + 1) = "blue"
    val sentence = Sentence(lowercaseSent)
    for (fn <- headWord) { fn(sentence.headWord(0, phrase.length)) }
    // Tokenize
    if (lowercaseWords.length == 0) { 
      lowercaseSent
    } else if (lowercaseWords.length == 1 && !sentence.pos(0).startsWith("N")) {
      lowercaseWords
    } else {
      tokenizeWithCaseImpl(phrase, sentence, 1)
    }
  }
  
  def tokenizeWithCase(phrase:Array[String]):Array[String] = {
    tokenizeWithCaseImpl(phrase, None)
  }
  
  def tokenizeWithCase(phrase:String, headWord:Option[String=>Any]=None):Array[String] = {
    tokenizeWithCase(Sentence(phrase).words)
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
}
