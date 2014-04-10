package org.goobs.truth

import org.goobs.truth.Messages.Query
import edu.stanford.nlp.util.logging.Redwood.Util._

import scala.collection.JavaConversions._
import org.goobs.truth.TruthValue._


/**
 * A specification of a data source; the requirement is to be able to provide
 * a stream of training examples from an input location (specified with a String)
 */
trait DataSource {
  def read(path:String):Stream[(Query.Builder,TruthValue)]
}

/**
 * Read examples from the FraCaS test suite found at: http://www-nlp.stanford.edu/~wcmac/downloads/fracas.xml
 */
object FraCaS extends DataSource {

  override def read(xmlPath:String):Stream[(Query.Builder,TruthValue)] = {
    // Parse XML
    val xml = scala.xml.XML.loadFile(xmlPath)
    (xml \ "problem").toStream.map { problem => (
      Query.newBuilder()
        // Antecedents
        .addAllKnownFact((problem \ "p").flatMap ( x => NatLog.annotate(x.text) ))
        // Consequent
        .setQueryFact(NatLog.annotate((problem \ "h").head.text).head),
        // Gold annotation
        (problem \ "a").head.text.trim match {
            // Variants of YES
          case "Yes"          => TRUE
          case "?Yes"         => TRUE
          case "Many"         => TRUE
          case "At most two"  => TRUE
          case "Yes, on one reading"                 => TRUE
          case "Yes, on one reading of the premise"  => TRUE
          case "Yes, on one possible reading"        => TRUE
          case "Yes, on one possible reading/parse"  => TRUE
          case "??: Yes for a mouse; ?? No for an animal" => TRUE
          case "Yes (for a former university student)" => TRUE
          case "Yes, if both commissioners are female; otherwise there are more than two commissioners." => TRUE
          case "Yes, on a distributive reading of &quot;Smith and Jones&quot;." => TRUE
          case "Yes, on a distributive reading of the question." => TRUE
            // Variants of NO
          case "No"              => FALSE
          case "No, just one"    => FALSE
          case "Not many"        => FALSE
          case "No / don't know" => FALSE
            // Variants of UNKNOWN
          case "Don't know"   => UNKNOWN
          case "Don't know, on one reading of the premise" => UNKNOWN
          case "Don't know, on one reading" => UNKNOWN
          case "Don't know, on one possible reading" => UNKNOWN
          case ""             => UNKNOWN
          case _     =>
            fail("Unknown answer: " + (problem \ "a"))
            UNKNOWN
        }
    )}
  }

}
