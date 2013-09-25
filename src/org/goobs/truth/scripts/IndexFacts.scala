package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source
import gnu.trove.map.hash._
import gnu.trove.TCollections

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection
import java.sql.ResultSet

import edu.stanford.nlp.io.IOUtils._
import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._
import org.goobs.truth.EdgeType._
import org.goobs.truth.Utils._

object IndexFacts {
  
  private val logger = Redwood.channels("Facts")

  def index(rawPhrase:Array[String])
           (implicit wordIndexer:TObjectIntHashMap[String]):Array[Int] = {
    val phrase:Array[String] = tokenizeWithCase(rawPhrase)
    // Create object to store result
    val indexResult:Array[Int] = new Array[Int](phrase.length)
    for (i <- 0 until indexResult.length) { indexResult(i) = -1 }

    // Find largest phrases to index into (case sensitive)
    for (length <- phrase.length until 0 by -1;
         start <- 0 to phrase.length - length) {
      if ( (start until (start + length)) forall (indexResult(_) < 0) ) {
        val candidate:String = phrase.slice(start, start+length).mkString(" ")
        if (wordIndexer.containsKey(candidate)) {
          val index = wordIndexer.get(candidate)
          for (i <- start until start + length) { indexResult(i) = index; }
          logger.log( candidate + " -> " + index )
        }
      }
    }
    
    // Find largest phrases to index into (case insensitive)
    for (length <- phrase.length until 0 by -1;
         start <- 0 to phrase.length - length) {
      if ( (start until (start + length)) forall (indexResult(_) < 0) ) {
        val candidate:String = phrase.slice(start, start+length).mkString(" ")
        if (wordIndexer.containsKey(candidate)) {
          val index = wordIndexer.get(candidate)
          for (i <- start until start + length) { indexResult(i) = index; }
          logger.log( candidate + " -> " + index )
        }
      }
    }

    // Create resulting array
    var lastElem:Int = -999
    var rtn = List[Int]()
    for (i <- indexResult.length - 1 to 0 by -1) {
      if (indexResult(i) != lastElem) {
        lastElem = indexResult(i)
        rtn = indexResult(i) :: rtn
      }
    }
    return rtn.toArray
  }


  def main(args:Array[String]):Unit = {
    edu.stanford.nlp.NLPConfig.caseless  // set caseless models
    Props.exec(() => {
      // Read word index
      forceTrack("Reading words")
      implicit val wordInterner = new TObjectIntHashMap[String]
      var count = 0;
      slurpTable(TABLE_WORD_INTERN, {(r:ResultSet) =>
        val key:Int = r.getInt("index")
        val gloss:String = r.getString("gloss")
        wordInterner.put(gloss, key)
        count += 1;
        if (count % 1000000 == 0) { 
          logger.log("read " + (count / 1000000) + "M words; " + (Runtime.getRuntime.freeMemory / 1000000) + " MB of memory free")
        }
      })
      endTrack("Reading words")
      
      // Read facts
      println( index(Whitespace.split("George Bush attended the United Nations talks")) )

      val factCumulativeWeight = TCollections.synchronizedMap(
          new TObjectFloatHashMap[Array[Int]])
      for (file <- iterFilesRecursive(Props.SCRIPT_REVERB_RAW_DIR).par) {
        withConnection{ (psql:Connection) =>
          var offset:Long = 0;
          val factInsert = psql.prepareStatement(
            "INSERT INTO " + Postgres.TABLE_FACTS +
            " (weight, left_arg, rel, right_arg) VALUES (?, ?, ?, ?);")
          val factUpdate = psql.prepareStatement(
            "UPDATE " + Postgres.TABLE_FACTS +
            " SET weight=? WHERE left_arg=? AND rel=? AND right_arg=?;")
          for (line <-
               try {
                { if (Props.SCRIPT_REVERB_RAW_GZIP) Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))
                  else Source.fromFile(file) }.getLines
                } catch {
                  case (e:java.util.zip.ZipException) =>
                    try {
                      Source.fromFile(file).getLines
                    } catch {
                      case (e:Exception) => Nil
                    }
                } ) {
            val fact = Fact(file.getName, offset,
                            {offset += line.length + 1; offset},
                            line.split("\t"))
            if (fact.confidence >= 0.5) {
              // vv add fact vv
              // Index terms
              val leftArg  = index(Whitespace.split(fact.leftArg))
              val relation = index(Whitespace.split(fact.relation))
              val rightArg = index(Whitespace.split(fact.rightArg))
              // Get cumulative confidence
              val key = new Array[Int](leftArg.length + relation.length + rightArg.length)
              System.arraycopy(leftArg,  0, key, 0, leftArg.length)
              System.arraycopy(relation, 0, key, leftArg.length, relation.length)
              System.arraycopy(rightArg, 0, key, leftArg.length + relation.length, rightArg.length)
              val weight = factCumulativeWeight.adjustOrPutValue(key, fact.confidence.toFloat, fact.confidence.toFloat)
              // Create postgres-readable arrays
              val leftArgArray = psql.createArrayOf("int4", leftArg.map( x => x.asInstanceOf[Object]))
              val relArray = psql.createArrayOf("int4", relation.map( x => x.asInstanceOf[Object]))
              val rightArgArray = psql.createArrayOf("int4", rightArg.map( x => x.asInstanceOf[Object]))
              // Write to Postgres
              val toExecute = 
                if (weight != fact.confidence.toFloat) factUpdate else factInsert
              toExecute.setFloat(1, weight)
              toExecute.setArray(2, leftArgArray)
              toExecute.setArray(3, relArray)
              toExecute.setArray(4, rightArgArray)
              toExecute.executeUpdate
              // ^^          ^^
            }
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
