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

object Edges {
  val WORDNET_NOUN_HYPERNYM = "wordnet_noun_hypernym"
  val WORDNET_NOUN_INSTANCE_HYPERNYM = "wordnet_noun_instance_hypernym"
  val WORDNET_NOUN_HYPONYM = "wordnet_noun_hyponym"
  val WORDNET_NOUN_INSTANCE_HYPONYM = "wordnet_noun_instance_hyponym"
  val WORDNET_VERB_HYPERNYM = "wordnet_verb_hypernym"
  val WORDNET_VERB_HYPONYM = "wordnet_verb_hyponym"
  val WORDNET_ADJECTIVE_RELATED = "wordnet_jj_related"
  val WORDNET_ADJECTIVE_PERTAINYM = "wordnet_jj_pertainym"
  val WORDNET_ADJECTIVE_PARTICIPLE = "wordnet_jj_participle"
  val WORDNET_ADVERB_PERTAINYM = "wordnet_rb_pertainym"
}
