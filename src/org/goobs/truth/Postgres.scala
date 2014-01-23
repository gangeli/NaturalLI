package org.goobs.truth

import java.sql._
import java.net.InetAddress
import java.net.UnknownHostException

import edu.stanford.nlp.util.logging.Redwood.Util._

/**
 *
 * The entry point for constructing a graph between fact arguments.
 * 
 * Start the server with:
 *   /u/nlp/packages/pgsql/bin/pg_ctl -D /john0/scr2/pgdata_truth -l /john0/scr2/pgdata_truth/psql.log start
 *
 * @author Gabor Angeli
 */
object Postgres {

  def withConnection(callback:Connection=>Unit) = {
    val uri = "jdbc:postgresql://" + Props.PSQL_HOST + ":" + Props.PSQL_PORT + "/" + Props.PSQL_DB + "?characterEncoding=utf8"
    val psql = DriverManager.getConnection(uri, Props.PSQL_USERNAME, Props.PSQL_PASSWORD)
    try {
      psql.setAutoCommit(false)
      callback(psql)
      if (!psql.getAutoCommit) psql.commit
    } catch {
      case (e:SQLException) => err(e); err(e.getNextException)
    } finally {
      try {
        psql.close
      } catch {
        case (e:SQLException) => err(e); err(e.getNextException)
      }
    }
  }

  def slurpTable[E](tableName:String, callback:ResultSet=>Any):Unit = {
    withConnection{ (psql:Connection) =>
      val stmt = psql.createStatement
      stmt.setFetchSize(1000000)
      val results = stmt.executeQuery(s"SELECT * FROM $tableName")
      while (results.next) callback(results)
    }
  }

  /**
   * Lazy indexers, which rather than reading the database into memory issue psql queries
   * per indexing request.
   */
  lazy val (indexerContains, indexerGet, indexerGloss):(String=>Boolean,String=>Int,Int=>String) = {
    val uri = "jdbc:postgresql://" + Props.PSQL_HOST + ":" + Props.PSQL_PORT + "/" + Props.PSQL_DB + "?characterEncoding=utf8"
    val psql = DriverManager.getConnection(uri, Props.PSQL_USERNAME, Props.PSQL_PASSWORD)
    val statement = psql.prepareStatement(s"SELECT * FROM $TABLE_WORD_INTERN WHERE gloss=?;")
    val reverse = psql.prepareStatement(s"SELECT * FROM $TABLE_WORD_INTERN WHERE index=?;")

    (
      // contains()
      {(gloss:String) =>
        statement.setString(1, gloss)
        val results = statement.executeQuery()
        results.next()
      },
      // indexOf()
      {(gloss:String) =>
        statement.setString(1, gloss)
        val results = statement.executeQuery()
        if (!results.next()) {
          throw new NoSuchElementException("No such word in index: " + gloss)
        }
        results.getInt("index")
      },
      // get()
      {(index:Int) =>
        reverse.setInt(1, index)
        val results = reverse.executeQuery()
        if (!results.next()) {
          throw new NoSuchElementException("No such index in indexer: " + index)
        }
        results.getString("gloss")
      }
    )
  }


  val TABLE_WORD_INTERN:String = "word_indexer"
  val TABLE_EDGE_TYPE_INTERN:String = "edge_type_indexer"
  val TABLE_FACTS:String = "fact"
  val TABLE_EDGES:String = "edge"
}
