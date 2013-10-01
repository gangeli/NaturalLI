package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source
import gnu.trove.map.TObjectIntMap
import gnu.trove.map.TObjectFloatMap
import gnu.trove.map.hash.TObjectIntHashMap
import gnu.trove.map.custom_hash.TObjectFloatCustomHashMap
import gnu.trove.strategy.HashingStrategy
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


//
// SQL prerequisite statements for this script:
// CREATE TABLE facts ( left_arg INTEGER[], rel INTEGER[], right_arg INTEGER[], weight REAL );
// CREATE INDEX index_fact_left_arg on "facts" USING GIN ("left_arg");
// CREATE INDEX index_fact_right_arg on "facts" USING GIN ("right_arg");
// CREATE INDEX index_fact_rel on "facts" USING GIN ("rel");
//

/**
 * The entry point for reading and indexing the ReVerb facts.
 *
 * @author Gabor Angeli
 */
object IndexFacts {
  
  private val logger = Redwood.channels("Facts")

  class IntArrayStrategy extends HashingStrategy[Array[Int]] { 
    var last:Array[Int] = null;
    var lastCode:Int = -1;
    override def computeHashCode(o:Array[Int]):Int = {
      if (o eq last) { 
        lastCode
      } else {
        last = o;
        lastCode = o.foldLeft(31) { case (h:Int, term:Int) => h ^ (31 * term); }
        lastCode
      }
    }
  
    override def equals(a:Array[Int], b:Array[Int]):Boolean = {
      if (a.length != b.length) { return false; }
      for (i <- 0 until a.length) {
        if (a(i) != b(i)) { return false; }
      }
      return true;
    }
  } 


  def index(rawPhrase:Array[String])
           (implicit wordIndexer:TObjectIntMap[String]):Option[Array[Int]] = {
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
        }
      }
    }

    // Create resulting array
    var lastElem:Int = -999
    var rtn = List[Int]()
    for (i <- indexResult.length - 1 to 0 by -1) {
      if (indexResult(i) < 0) { return None }
      if (indexResult(i) != lastElem) {
        lastElem = indexResult(i)
        rtn = indexResult(i) :: rtn
      }
    }
    return Some(rtn.toArray)
  }
      
  var factCumulativeWeight = List( new TObjectFloatCustomHashMap[Array[Int]](new IntArrayStrategy) )
  
  def updateWeight(key:Array[Int], confidence:Float):Float = {
    this.synchronized {
      // Find the map which likely contains this key
      val candidateMap = factCumulativeWeight
        .collectFirst{ case (map:TObjectFloatMap[Array[Int]]) if (map.containsKey(key)) => map }
        .getOrElse(factCumulativeWeight.head)
      try {
        // Try adding the weight
        candidateMap.adjustOrPutValue(key, confidence, confidence)
      } catch {
        case (e:IllegalStateException) =>
          // Could nto add; try making more maps (can overflow hashmap?)
          warn("Could not write to hash map (" + e.getMessage + ")")
          factCumulativeWeight = new TObjectFloatCustomHashMap[Array[Int]](new IntArrayStrategy) :: factCumulativeWeight;
          try {
            // Add to new hash map
            factCumulativeWeight.head.adjustOrPutValue(key, confidence, confidence)
          } catch {
            // Still can't write? Give up.
            case (e:IllegalStateException) => e.printStackTrace
            confidence
          }
      }
    }
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
      startTrack("Adding Facts (parallel)")
      for (file <- iterFilesRecursive(Props.SCRIPT_REVERB_RAW_DIR).par) { try {
        withConnection{ (psql:Connection) =>
          var offset:Long = 0;
          val factInsert = psql.prepareStatement(
            "INSERT INTO " + Postgres.TABLE_FACTS +
            " (weight, left_arg, rel, right_arg) VALUES (?, ?, ?, ?);")
          val factUpdate = psql.prepareStatement(
            "UPDATE " + Postgres.TABLE_FACTS +
            " SET weight=? WHERE left_arg=? AND rel=? AND right_arg=?;")
          var numInserts = 0
          var numUpdates = 0
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
                } ) { try {
            val fact = Fact(file.getName, offset,
                            {offset += line.length + 1; offset},
                            line.split("\t"))
            if (fact.confidence >= 0.25) {
              for (leftArg:Array[Int] <- index(Whitespace.split(fact.leftArg));
                   rightArg:Array[Int] <- index(Whitespace.split(fact.rightArg));
                   relation:Array[Int] <- index(Whitespace.split(fact.relation))) {
                // vv add fact vv
                // Get cumulative confidence
                val key = new Array[Int](leftArg.length + relation.length + rightArg.length)
                System.arraycopy(leftArg,  0, key, 0, leftArg.length)
                System.arraycopy(relation, 0, key, leftArg.length, relation.length)
                System.arraycopy(rightArg, 0, key, leftArg.length + relation.length, rightArg.length)
                val weight = updateWeight(key, fact.confidence.toFloat)
                // Create postgres-readable arrays
                val leftArgArray = psql.createArrayOf("int4", leftArg.map( x => x.asInstanceOf[Object]))
                val relArray = psql.createArrayOf("int4", relation.map( x => x.asInstanceOf[Object]))
                val rightArgArray = psql.createArrayOf("int4", rightArg.map( x => x.asInstanceOf[Object]))
                // Write to Postgres
                val toExecute = 
                  if (weight != fact.confidence.toFloat) {
                    numUpdates += 1
                    factUpdate
                  } else {
                    numInserts += 1
                    factInsert
                  }
                toExecute.setFloat(1, weight)
                if (leftArg.length == 1 && leftArg(0) == 0) {
                  toExecute.setNull(2, java.sql.Types.ARRAY)
                } else {
                  toExecute.setArray(2, leftArgArray)
                }
                toExecute.setArray(3, relArray)
                if (rightArg.length == 1 && rightArg(0) == 0) {
                  toExecute.setNull(4, java.sql.Types.ARRAY)
                } else {
                  toExecute.setArray(4, rightArgArray)
                }
                toExecute.addBatch
                // ^^          ^^
              }
            }
          } catch { case (e:Exception) => e.printStackTrace } }
          factInsert.executeBatch
          psql.commit
          factUpdate.executeBatch
          psql.commit
          logger.log("finished [" + numInserts + " ins. " + numUpdates + " up.]: " + file)
        }
      } catch { case (e:Exception) => e.printStackTrace } }
      endTrack("Adding Facts (parallel)")
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
