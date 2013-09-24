package org.goobs.truth

import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._
import edu.stanford.nlp.TokensRegex
import edu.stanford.nlp.TokensRegex._

import edu.smu.tspell.wordnet.SynsetType


import org.goobs.truth.Implicits._

object Dictionary {
  private val lotOrLittle
    = word("""lot[s]?|load|plenty|heap[s]?|ton[s]?|few|little|bit|couple""")

  val quantifiersAndDeterminers:Set[TokensRegex] = Set[TokensRegex](
    ( tag("""PRP.*""") ),  // his, your, their, this
    ( tag("""DT""") ),     // a, the, every
    ( tag("""WP.*""") ),   // whose
    (word("[Aa]")) (lotOrLittle) (word("of")),
    (lotOrLittle) (word("of")),
    (word("[Aa]")) (lotOrLittle)
  )
}
