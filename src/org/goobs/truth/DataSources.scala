package org.goobs.truth

import org.goobs.truth.Messages.{Fact, Query}
import edu.stanford.nlp.util.logging.Redwood.Util._

import scala.collection.JavaConversions._
import org.goobs.truth.TruthValue._
import edu.stanford.nlp.util.Execution
import java.sql.{ResultSet, Connection}
import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.io.IOUtils


object DataSource {
  type Datum = (Iterable[Query.Builder], TruthValue)
  type DataStream = Stream[Datum]

  def loop(dataset:DataStream):DataStream = {
    if (dataset.isEmpty) { throw new IllegalArgumentException("Cannot loop empty dataset") }
    var iter: Iterator[(Iterable[Query.Builder], TruthValue)] = dataset.iterator
    Stream.continually[Datum]({
      dataset.synchronized {
        if (iter.hasNext) {
          iter.next()
        } else {
          iter = dataset.iterator
          iter.next()
        }
      }
    })
  }
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
 * Read the Regression Test example, given a URI (file / classpath / etc.)
 */
object RegressionDataSource extends DataSource {
  /**
   * Read queries from the specified abstract path.
   * @param path The path to read the queries from. The meaning of this path can vary depending on the particular extractor.
   * @return A stream of examples, annotated with their truth value.
   */
  override def read(path: String): DataStream = {
    val INPUT = """\s*\[([^\]]+)\]\s*\(([^,]+),\s*([^\)]+)\)\s*""".r
    val reader = IOUtils.getBufferedReaderFromClasspathOrFileSystem(path)
    var line:String = reader.readLine()
    var examples:List[(Seq[Fact],TruthValue)] = Nil

    while ( line != null ) {
      line = line.replaceAll("\\s*#.*", "")
      if (!line.trim.equals("")) {
        // Parse Line
        // (truth value)
        val truth: TruthValue = line.charAt(0) match {
          case '✔' => TruthValue.TRUE
          case '✘' => TruthValue.FALSE
          case '?' => TruthValue.UNKNOWN
        }
        // (unk mapping)
        val unkProvider = Utils.newUnkProvider
        // (parse fact)
        val facts: Seq[Fact] = line.substring(1).split("\t").map {
          case INPUT(rel, subj, obj) => NatLog.annotate(subj, rel, obj, unkProvider)
          case _ => throw new IllegalArgumentException("Could not parse fact: " + line)
        }
        // (add fact)
        examples = (facts, truth) :: examples
      }
      line = reader.readLine()
    }

    examples.toStream.map{ case (facts: Seq[Fact], gold:TruthValue) =>
      (List(Query.newBuilder()
        // Antecedents
        .addAllKnownFact(facts.take(facts.length-1))
        .setUseRealWorld(false)
        // Consequent
        .setQueryFact(facts.last)
      ), gold)
    }
  }
}

/**
 * Read examples from the FraCaS test suite found at: http://www-nlp.stanford.edu/~wcmac/downloads/fracas.xml
 */
object FraCaS extends DataSource with Client {
  import DataSource._
  /**
   * A filter for selecting queries which have only a single antecedent
   */
  val isSingleAntecedent:(Datum) => Boolean = {
    case (query: Seq[Query.Builder], truth: TruthValue) => query.head.getKnownFactCount == 1
  }

  /**
   * A filter for selecting queries which are considered "valid" for NatLog inference
   */
  val isApplicable07:(Datum) => Boolean = { (datum:Datum) =>
      isSingleAntecedent(datum) && (isSection(1)(datum) || isSection(5)(datum) || isSection(6)(datum))
  }

  /**
   * A filter for selecting queries which are considered "valid" for NatLog inference
   */
  val isApplicable:(Datum) => Boolean = { (datum:Datum) =>
    isSingleAntecedent(datum) && (isSection(1)(datum) || isSection(2)(datum) || isSection(5)(datum) || isSection(6)(datum) || isSection(9)(datum))
  }

  def isSection(section:Int):(Datum) => Boolean = { case (query:Seq[Query.Builder], truth: TruthValue) =>
    (section, query.head.getId) match {
      case (1, x) if   0 to  80 contains x => true
      case (2, x) if  81 to 113 contains x => true
      case (3, x) if 114 to 141 contains x => true
      case (4, x) if 142 to 196 contains x => true
      case (5, x) if 197 to 219 contains x => true
      case (6, x) if 220 to 250 contains x => true
      case (7, x) if 251 to 325 contains x => true
      case (8, x) if 326 to 333 contains x => true
      case (9, x) if 334 to 347 contains x => true
      case (_, x) if x > 347 => throw new IllegalStateException("Unknown id: " + x)
      case _ => false
    }

  }

  override def read(xmlPath:String):DataStream = {
    // Parse XML
    val xml = scala.xml.XML.loadFile(xmlPath)
    (xml \ "problem").par.toStream.map { problem =>
      val unkProvider = Utils.newUnkProvider
      (List(Query.newBuilder()
        // Antecedents
        .addAllKnownFact((problem \ "p").flatMap ( x => NatLog.annotate(x.text, unkProvider) ))
        .setUseRealWorld(false)
        // Consequent
        .setQueryFact(NatLog.annotate((problem \ "h").head.text, unkProvider).head)
        .setId((problem \ "@id").text.toInt)),
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
    )}.filter{ case (queries, truth) => queries.head.getQueryFact.getWordCount > 0 && queries.head.getKnownFactCount > 0 && truth != INVALID}
  }

  def main(args:Array[String]):Unit = {
    Execution.fillOptions(classOf[Props], args)
    Props.SERVER_MAIN_HOST = "localhost"
    Props.SERVER_MAIN_PORT = 4001
    Props.SEARCH_TIMEOUT = 250000
    System.exit(startMockServer(() =>
      Benchmark.evaluateBenchmark(read(Props.DATA_FRACAS_PATH.getPath) filter isSingleAntecedent, NatLog.hardNatlogWeights,
        subResults=List(
          ("single antecedent", isSingleAntecedent),
          ("NatLog Valid (07)", isApplicable07),
          ("NatLog Valid (08)", isApplicable),
          ("1. Quantifiers   ", isSection(1)),
          ("2. Plurals       ", isSection(2)),
          ("3. Anaphora      ", isSection(3)),
          ("4. Ellipses      ", isSection(4)),
          ("5. Adjectives    ", isSection(5)),
          ("6. Comparatives  ", isSection(6)),
          ("7. Temporal      ", isSection(7)),
          ("8. Verbs         ", isSection(8)),
          ("9. Attitudes     ", isSection(9))
        ),
      remember=isApplicable),
    printOut = false))
  }
}

trait FromPostgres {
  import DataSource._
  /**
   * Read queries from the specified abstract path.
   * @param query The query to run.
   * @param truth The truth value to annotate the fact with
   * @return A stream of examples, annotated with their truth value.
   */
  def read(query:String, truth:TruthValue): DataStream = {
    Postgres.withConnection( (psql:Connection) => {
      val stmt = psql.createStatement()
      stmt.setFetchSize(1000)
      psql.setAutoCommit(false)
      val results: ResultSet = stmt.executeQuery(query)
      def readResult(r:ResultSet, index:Int):Stream[Datum] = {
        if (r.next()) {
          val fact:Array[String] = r.getArray("gloss").getArray.asInstanceOf[Array[Any]]
            .map( _.toString.toInt )
            .map( Utils.wordGloss )
          val datum:Datum = (List(Query.newBuilder()
              // Consequent
              .setQueryFact(NatLog.annotate(fact.mkString(" ")).head)
              .setUseRealWorld(true)
              .setAllowLookup(false)
              .setId(index)),
              truth
            )
          Stream.cons(datum, readResult(r, index + 1))
        } else {
          Stream.Empty
        }
      }
      readResult(results, 0)
    }).getOrElse(Stream.Empty)
  }
}

object HoldOneOut extends DataSource with FromPostgres {
  /**
   * Read queries from the specified abstract path.
   * @param path Ignored in this case
   * @return A stream of examples, annotated with their truth value.
   */
  override def read(path: String): DataStream = {
    val trueQuery = s"SELECT * FROM ${Postgres.TABLE_FACTS} ORDER BY weight DESC"
    val falseQuery = s"SELECT * FROM ${Postgres.TABLE_FACTS} ORDER BY weight ASC"
    read(trueQuery, TruthValue.TRUE)
      .zip(read(falseQuery, TruthValue.FALSE))
      .map{ case (a, b) => Stream(a, b) }
      .flatten
  }
}

object AVE extends DataSource {
  /**
   * Read queries from the specified abstract path.
   * @param path The path to read the queries from. This should be a *.tab file (canonically, in etc/ave/YEAR.tab).
   * @return A stream of examples, annotated with their truth value.
   */
  override def read(path: String): DataStream = {
    io.Source.fromFile(path).getLines().toStream.filter(_.split("\t").size > 3).map{ (line:String) =>
      val fields :Array[String]         = line.split("\t")
      val id: Int                       = fields(0).toInt
      val gold: TruthValue.Value        = fields(1).toBoolean match { case true => TruthValue.TRUE; case false => TruthValue.FALSE }
      val preFiltered: Boolean          = !fields(2).toBoolean
      val queries:Seq[Fact]             = fields.drop(3).map( (fact:String) => NatLog.annotate(fact.replaceAll(":::", " "))).map( _.head )
      (queries.map{ (fact:Fact) =>
        Query.newBuilder().setQueryFact(fact).setId(id).setForceFalse(preFiltered).setAllowLookup(true).setUseRealWorld(true)
      }, gold)
    }
  }
}

object MTurk extends DataSource {
  /**
   * Read queries from the specified abstract path.
   * @param path The path to read the queries from. This should be a *.tab file (canonically, in etc/mturk/reverb_[train|test].tab).
   * @return A stream of examples, annotated with their truth value.
   */
  override def read(path: String): DataStream = {
    io.Source.fromFile(path).getLines().toStream.map{ (line:String) =>
      val fields :Array[String]         = line.split("\t")
      val gold: TruthValue.Value        = fields(0).toBoolean match { case true => TruthValue.TRUE; case false => TruthValue.FALSE }
      val queries:Seq[Fact]             = List(NatLog.annotate(fields.drop(1).mkString(" ")).head)
      (queries.map{ (fact:Fact) =>
        Query.newBuilder().setQueryFact(fact).setAllowLookup(false).setUseRealWorld(true)
      }, gold)
    }
  }
}
