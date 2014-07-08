package org.goobs.naturalli.scripts

import scala.collection.JavaConversions._

import edu.stanford.nlp.util.logging.Redwood.Util._

import edu.smu.tspell.wordnet._

import org.goobs.sim._

import org.goobs.naturalli._
import org.goobs.naturalli.Postgres._
import org.goobs.naturalli.Implicits._
  
  
object FindQuantifiers {
  def main(args:Array[String]) = {
    Props.exec(() => {
      val wordnet = Ontology.load(Props.SCRIPT_WORDNET_PATH)
      for (synset <- wordnet.ontology.values.flatten.map( _.synset )) {
        if (synset.getType == SynsetType.ADJECTIVE &&
            synset.getDefinition.toLowerCase.contains("quantifier")) {
          log(synset.getWordForms.mkString(", "))
        }
      }
    }, args)
  }
}
