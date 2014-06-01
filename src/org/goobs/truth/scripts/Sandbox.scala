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

    org.goobs.truth.Utils.printToFile(new java.io.File("/home/gabor/tmp/sharon.txt")) { out =>
    for ( (phrase, synsets) <- wordnet.ontology.filter{ case (p, sn) => sn.exists( _.count > 10000) }.map( _._1 ).map{ x => (x, jaws.getSynsets(x.mkString(" "))) }.toArray.sortBy{ case (p, s) => if (s == null) 1000 else s.length }) {
        out.println(phrase.mkString(" "))
    }
    }
  }

}
