package org.goobs.truth.scripts

import edu.smu.tspell.wordnet.{Synset, WordNetDatabase}
import scala.collection.mutable
import org.goobs.sim.Ontology.RealNode
import org.goobs.sim.Ontology
import org.goobs.truth.Props

/**
 * TODO(gabor) JavaDoc
 *
 * @author gabor
 */
object Sandbox {

  def main(args:Array[String]):Unit = {
    val wordnet       = Ontology.load(Props.SCRIPT_WORDNET_PATH)
    implicit val jaws = WordNetDatabase.getFileInstance
    def getSense(phrase:String, synset:Synset)(implicit jaws:WordNetDatabase):Option[Int] = {
      val synsets:Array[Synset] = jaws.getSynsets(phrase)
      val index = synsets.indexOf(synset)
      if (index < 0) {
        throw new IllegalStateException("Unknown synset: " + synset)
      }
      if (index > 30) None else Some(index + 1)
    }

    case class SynonymPair(a:String, aSense:Option[Int], b:String, bSense:Option[Int])
    val synonyms = new mutable.HashSet[SynonymPair]
    for ( node: RealNode <- wordnet.ontology.values.flatten ) {
      for ( word:String <- node.synset.getWordForms ) {
        for ( synonym:String <- node.synset.getWordForms ) {
          if (word != synonym) {
            synonyms.add(SynonymPair(word, getSense(word, node.synset), synonym, getSense(synonym, node.synset)))
          }
        }
      }
    }
    for ( pair <- synonyms ) {
      if (pair.a == "tail") { println(pair) }
    }

  }

}
