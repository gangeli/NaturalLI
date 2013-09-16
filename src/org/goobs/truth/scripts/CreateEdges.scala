
package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection
import java.sql.ResultSet

import edu.stanford.nlp.io.IOUtils._
import edu.stanford.nlp.util.HashIndex

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._

/**
 *
 * The entry point for constructing graph edges, once the node list
 * is fully populated.
 *
 * @author Gabor Angeli
 */
object CreateEdges {
  def handleWords:Map[Int, String] = {
    var map = new scala.collection.mutable.HashMap[Int, String]
    slurpTable(TABLE_WORD_INTERN, {(r:ResultSet) =>
      val key:Int = r.getInt("key")
      val gloss:String = r.getString("gloss")
      map(key) = gloss
    })
    map.toMap
  }

  def handlePhrases(wordIndex:Map[Int, String]):Unit = {
    slurpTable(TABLE_PHRASE_INTERN, {(r:ResultSet) =>
      val phrase:Array[Int] = r.getArray("words").getArray.asInstanceOf[Array[Integer]].map( x => x.asInstanceOf[Int] );
      println(phrase.map( wordIndex(_) ).mkString(" "))
    })
  }

  def main(args:Array[String]) = {
    Props.exec(() => {
      val wordIndex:Map[Int, String] = handleWords
      handlePhrases(wordIndex)
    }, args)
  }
}
