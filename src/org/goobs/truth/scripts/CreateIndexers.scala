package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection

import edu.stanford.nlp.io.IOUtils._
import edu.stanford.nlp.util.HashIndex

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._

/**
 *
 * The entry point for constructing the nodes of a graph, indexed to a compact
 * form.
 *
 * @author Gabor Angeli
 */

object CreateGraph {

  def facts:Iterator[Fact] = {
    var offset:Long = 0;
    for (file <- iterFilesRecursive(Props.REVERB_RAW_DIR).iterator;
         line <-
           try {
            { if (Props.REVERB_RAW_GZIP) Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))
              else Source.fromFile(file) }.getLines
           } catch {
             case (e:java.util.zip.ZipException) =>
               try {
                 Source.fromFile(file).getLines
               } catch {
                 case (e:Exception) => Nil
               }
           }
         ) yield Fact(file.getName, offset,
                      {offset += line.length + 1; offset},
                      line.split("\t"))
  }

  def main(args:Array[String]) = {
    val wordIndexer = new HashIndex[String]
    val phraseIndexer = new HashIndex[String]
    var factI = 0;

    Props.exec(() => {
      withConnection{ (psql:Connection) =>
        // Create Statements
        val wordInsert = psql.prepareStatement("INSERT INTO " + Postgres.TABLE_WORD_INTERN + " (key, gloss) VALUES (?, ?);")
        val phraseInsert = psql.prepareStatement("INSERT INTO " + Postgres.TABLE_PHRASE_INTERN + " (key, words) VALUES (?, ?);")
        val factInsert = psql.prepareStatement("INSERT INTO " + Postgres.TABLE_FACT_INTERN + " (key, file, begin_index, end_index, left_arg, rel, right_arg) VALUES (?, ?, ?, ?, ?, ?, ?);")
        wordInsert.setInt(1, wordIndexer.indexOf("", true))
        wordInsert.setString(2, "")
        wordInsert.executeUpdate
        // Iterate over facts
        for (fact <- facts) {
          val phrases = for (phrase <- fact.elements) yield {
            var phraseIndex = phraseIndexer.indexOf(phrase)
            if (phraseIndex < 0) {
              phraseIndex = phraseIndexer.indexOf(phrase, true)
              // Add words
              val words = for (word <- phrase.split("""\s+""")) yield {
                  var index = wordIndexer.indexOf(word);
                  if (index < 0) {
                    index = wordIndexer.indexOf(word, true)
                    wordInsert.setInt(1, index)
                    wordInsert.setString(2, word)
                    wordInsert.executeUpdate
                  }
                  index
                }
              // Add phrase
              val array = psql.createArrayOf("int4", words.map( x => x.asInstanceOf[Object]))
              phraseInsert.setInt(1, phraseIndex)
              phraseInsert.setArray(2, array)
              phraseInsert.executeUpdate
            }
            phraseIndex
          }
          // Add fact
          factInsert.setInt(1, factI)
          factI += 1;
          factInsert.setBytes(2, fact.file.getBytes("UTF-8"))
          factInsert.setLong(3, fact.begin)
          factInsert.setLong(4, fact.end)
          factInsert.setInt(5, phrases(0))
          factInsert.setInt(6, phrases(1))
          factInsert.setInt(7, phrases(2))
          factInsert.executeUpdate
          if (factI % 10000 == 0) {
            psql.commit
            println("added " + factI + " facts")
          }
        }
      }
    }, args)

  }

  /**
   * An embodiment of a fact.
   *
   * @author Gabor Angeli
   */
  case class Fact(file:String, begin:Long, end:Long, fields:Array[String]) {
    assert(fields.length == 18 || fields.length == 17,
           "Invalid length line\n" + fields.zipWithIndex.map{ case (a, b) => (b.toInt + 1) + " " + a }.mkString("\n"))
  
    def leftArg :String = fields(15)
    def relation:String = fields(16)
    def rightArg:String = if (fields.length == 18) fields(17) else ""
  
    def confidence:Double = fields(11).toDouble

    def words:Iterable[String] = leftArg.split("""\s+""") ++ relation.split("""\s+""") ++ rightArg.split("""\s+""")
    def elements:Array[String] = Array[String](leftArg, relation, rightArg)
  
    override def toString:String = leftArg + " ~ " + relation + " ~ " + rightArg
  }
}


