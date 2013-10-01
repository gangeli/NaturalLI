package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.collection.mutable.HashSet
import scala.io.Source

import gnu.trove.map.TObjectIntMap
import gnu.trove.map.TLongFloatMap
import gnu.trove.map.TObjectFloatMap
import gnu.trove.map.hash.TObjectIntHashMap
import gnu.trove.map.hash.TLongFloatHashMap
import gnu.trove.map.hash.TObjectFloatHashMap
import gnu.trove.procedure.TObjectFloatProcedure
import gnu.trove.TCollections._

import java.io._
import java.util.zip.GZIPInputStream
import java.sql.Connection
import java.sql.PreparedStatement
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
  import scala.language.implicitConversions
  
  private val logger = Redwood.channels("Facts")

  case class MinimalFact(leftArg:Array[Int], rel:Array[Int], rightArg:Array[Int]) {
    override def hashCode:Int = {
      leftArg.foldLeft(31) { case (h:Int, term:Int) => h ^ (31 * term); } ^
        rel.foldLeft(31) { case (h:Int, term:Int) => h ^ (31 * term); } ^
        rightArg.foldLeft(31) { case (h:Int, term:Int) => h ^ (31 * term); }
    }

    def equals(a:Array[Int], b:Array[Int]):Boolean = {
      if (a.length != b.length) { return false; }
      for (i <- 0 until a.length) {
        if (a(i) != b(i)) { return false; }
      }
      return true;
    }

    override def equals(other:Any):Boolean = {
      other match {
        case (o:MinimalFact) =>
          equals(leftArg, o.leftArg) && equals(rel, o.rel) && equals(rightArg, o.rightArg)
        case _ => false
      }
    }

    def hash:Long = {
      val fact = leftArg ++ rel ++ rightArg
      val (hash, shift) = fact.foldLeft( (0, 0) ) { 
            case ( (hash, shift), word ) =>
          (hash ^ (word << shift), shift + (64 / fact.length))
        }
      hash
    }
  }
  implicit def fn2troveFloatFn[E](fn:(E,Float)=>Any):TObjectFloatProcedure[E] = {
    new TObjectFloatProcedure[E]() {
      override def execute(key:E, value:Float):Boolean = {
        fn( key, value )
        true
      }
    }
  }

  def index(rawPhrase:String, allowEmpty:Boolean=false)
           (implicit wordIndexer:TObjectIntMap[String]):Option[Array[Int]] = {
    if (!allowEmpty && rawPhrase.trim.equals("")) { return None }
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
      
  var factCumulativeWeight = List( new TLongFloatHashMap )
  
  def updateWeight(fact:MinimalFact, confidence:Float,
                   allInserts:HashSet[MinimalFact]):(Float,Boolean) = {
    val key:Long = fact.hash
    this.synchronized {
      // Find the map which likely contains this key
      val candidateMap = factCumulativeWeight
        .collectFirst{ case (map:TLongFloatMap) if (map.containsKey(key)) => map }
        .getOrElse(factCumulativeWeight.head)
      try {
        // Try adding the weight
        val newConfidence = candidateMap.adjustOrPutValue(key, confidence, confidence)
        if (newConfidence == confidence) { allInserts.add(fact) }
        (newConfidence, newConfidence == confidence)
      } catch {
        case (e:IllegalStateException) =>
          // Could nto add; try making more maps (can overflow hashmap?)
          warn("Could not write to hash map (" + e.getMessage + ")")
          factCumulativeWeight = new TLongFloatHashMap :: factCumulativeWeight;
          try {
            // Add to new hash map
            val newConfidence = factCumulativeWeight.head.adjustOrPutValue(key, confidence, confidence)
            if (newConfidence == confidence) { allInserts.add(fact) }
            (newConfidence, newConfidence == confidence)
          } catch {
            // Still can't write? Give up.
            case (e:IllegalStateException) => throw new RuntimeException(e)
          }
      }
    }
  }

  def getWeight(key:Long):Float = {
    factCumulativeWeight
      .collectFirst{ case (map:TLongFloatMap) if (map.containsKey(key)) => map }
      .map{ _.get(key) }.getOrElse(0.0f)
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
      val toInsertAllPending:HashSet[MinimalFact]
        = new HashSet[MinimalFact]
      startTrack("Adding Facts (parallel)")
      for (file <- iterFilesRecursive(Props.SCRIPT_REVERB_RAW_DIR).par) { try {
        val toInsert:TObjectFloatMap[MinimalFact] = new TObjectFloatHashMap[MinimalFact]
        val toUpdate:TObjectFloatMap[MinimalFact] = new TObjectFloatHashMap[MinimalFact]
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
          val fact = Fact(file.getName, line.split("\t"))
          if (fact.confidence >= 0.25) {
            for (leftArg:Array[Int] <- index(fact.leftArg.trim);
                 rightArg:Array[Int] <- index(fact.rightArg.trim, true);  // can be empty
                 relation:Array[Int] <- index(fact.relation.trim)) {
              // vv add fact vv
              // Get cumulative confidence
              val factKey = new MinimalFact(leftArg, relation, rightArg)
              val (weight, isInsert)
                = updateWeight(factKey, fact.confidence.toFloat,
                               toInsertAllPending)
              // Determine whether it's an update or insert
              if (isInsert) {
                // case: insert (for now at least)
                toInsert.put(factKey, weight)
              } else {
                // case: update (but, make sure we're not updating elsewhere)
                if (!toInsertAllPending.contains(factKey)) {
                  toUpdate.adjustOrPutValue(factKey, weight, weight)
                }
              }
              // ^^          ^^
            }
          }
        } catch { case (e:Exception) => e.printStackTrace } }
        withConnection{ (psql:Connection) =>
          val factInsert = psql.prepareStatement(
            "INSERT INTO " + Postgres.TABLE_FACTS +
            " (weight, left_arg, rel, right_arg) VALUES (?, ?, ?, ?);")
          val factUpdate = psql.prepareStatement(
            "UPDATE " + Postgres.TABLE_FACTS +
            " SET weight=? WHERE left_arg=? AND rel=? AND right_arg=?;")
          // Populate Postgres Statements
          def fill(toExecute:PreparedStatement, key:MinimalFact, weight:Float):Unit = {
            // Create postgres-readable arrays
            val leftArgArray = psql.createArrayOf("int4", key.leftArg.map( x => x.asInstanceOf[Object]))
            val relArray = psql.createArrayOf("int4", key.rel.map( x => x.asInstanceOf[Object]))
            val rightArgArray = psql.createArrayOf("int4", key.rightArg.map( x => x.asInstanceOf[Object]))
            // Populate fields
            toExecute.setFloat(1, weight)
            if (key.leftArg.length == 1 && key.leftArg(0) == 0) {
              toExecute.setNull(2, java.sql.Types.ARRAY)
            } else {
              toExecute.setArray(2, leftArgArray)
            }
            toExecute.setArray(3, relArray)
            if (key.rightArg.length == 1 && key.rightArg(0) == 0) {
              toExecute.setNull(4, java.sql.Types.ARRAY)
            } else {
              toExecute.setArray(4, rightArgArray)
            }
            toExecute.addBatch
          }
          // Run population
          toUpdate.forEachEntry{ (key:MinimalFact, weight:Float) =>
            fill(factUpdate, key, weight)
          }
          toInsert.forEachEntry{ (key:MinimalFact, weight:Float) =>
            fill(factInsert, key, getWeight(key.hash))  // get weight in case it changed from future updates
          }
          // Run Updates
          factInsert.executeBatch
          psql.commit
          factUpdate.executeBatch
          psql.commit
          // Unregister inserted entries
          toInsert.forEachEntry{ (key:MinimalFact, weight:Float) =>
            toInsertAllPending.remove(key);
          }
          logger.log("finished [" + toInsert.size + " ins. " + toUpdate.size + " up.]: " + file)
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
  case class Fact(file:String, fields:Array[String]) {
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
