package org.goobs.truth

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
