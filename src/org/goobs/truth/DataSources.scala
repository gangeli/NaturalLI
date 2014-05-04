package org.goobs.truth

import org.goobs.truth.Messages.Query
import edu.stanford.nlp.util.logging.Redwood.Util._

import scala.collection.JavaConversions._
import org.goobs.truth.TruthValue._


object DataSource {
  type Datum = (Query.Builder, TruthValue)
  type DataStream = Stream[Datum]
}

/**
 * A specification of a data source; the requirement is to be able to provide
 * a stream of training examples from an input location (specified with a String)
 */
trait DataSource {
  import DataSource._
  /**
   * Read queries from the specified abstract path.
   * @param path The path to read the queries from. The meaning of this path can vary depending on the particular extractor.
   * @return A stream of examples, annotated with their truth value.
   */
  def read(path:String):DataStream
}

/**
 * Read examples from the FraCaS test suite found at: http://www-nlp.stanford.edu/~wcmac/downloads/fracas.xml
 */
object FraCaS extends DataSource {
  import DataSource._
  /**
   * A filter for selecting queries which have only a single antecedent
   */
  val isSingleAntecedent:(Datum) => Boolean = {
    case (query: Query.Builder, truth: TruthValue) => query.getKnownFactCount == 1
  }

  /**
   * A filter for selecting queries which are considered "valid" for NatLog inference
   */
  val isApplicable:(Datum) => Boolean = {
    case (query: Query.Builder, truth: TruthValue) =>
      isSingleAntecedent( (query, truth) ) &&
        (query.getId match {
          case x if   0 to  80 contains x => true
          case x if 197 to 219 contains x => true
          case x if 220 to 250 contains x => true
          case _ => false
        })
  }

  override def read(xmlPath:String):DataStream = {
    // Parse XML
    val xml = scala.xml.XML.loadFile(xmlPath)
    (xml \ "problem").par.toStream.map { problem =>
      val unkProvider = Utils.newUnkProvider
      (Query.newBuilder()
        // Antecedents
        .addAllKnownFact((problem \ "p").flatMap ( x => NatLog.annotate(x.text, unkProvider) ))
        // Consequent
        .setQueryFact(NatLog.annotate((problem \ "h").head.text, unkProvider).head)
        .setId((problem \ "@id").text.toInt),
        // Gold annotation
        (problem \ "@fracas_answer").text.trim match {
          case "yes"     => TRUE
          case "no"      => FALSE
          case "unknown" => UNKNOWN
          case "undef"   => INVALID
          case _         =>
            fail("Unknown answer: " + (problem \ "a") + " (parsed from " + (problem \ "@fracas_answer") + ")")
            INVALID
        }
    )}.filter{ case (query, truth) => query.getQueryFact.getWordCount > 0 && query.getKnownFactCount > 0 && truth != INVALID}
  }

  def main(args:Array[String]):Unit = {
    Props.SERVER_PORT = 4001
    Props.SEARCH_TIMEOUT = 250000
    System.exit(Client.startMockServer(() =>
      Test.evaluate(read(Props.DATA_FRACAS_PATH.getPath) filter isApplicable, NatLog.hardNatlogWeights,
        List( ("single antecedent", isSingleAntecedent), ("NatLog Valid", isApplicable) ),
      isApplicable),
    printOut = false))
  }
}
