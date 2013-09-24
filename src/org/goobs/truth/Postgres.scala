package org.goobs.truth

import java.sql._
import java.net.InetAddress
import java.net.UnknownHostException

/**
 *
 * The entry point for constructing a graph between fact arguments.
 *
 * @author Gabor Angeli
 */
object Postgres {

  def withConnection(callback:Connection=>Unit) = {
    val uri = "jdbc:postgresql://" + Props.PSQL_HOST + ":" + Props.PSQL_PORT + "/" + Props.PSQL_DB + "?characterEncoding=utf8";
    val psql = DriverManager.getConnection(uri, Props.PSQL_USERNAME, Props.PSQL_PASSWORD);
    try {
      psql.setAutoCommit(false)
      callback(psql)
      psql.commit
      psql.close
    } catch {
      case (e:SQLException) => throw new RuntimeException(e);
    }
  }

  def slurpTable[E](tableName:String, callback:ResultSet=>Any):Unit = {
    withConnection{ (psql:Connection) =>
      val stmt = psql.createStatement
      stmt.setFetchSize(10000)
      val results = stmt.executeQuery(s"SELECT * FROM $tableName")
      while (results.next) callback(results)
    }
  }

  val TABLE_WORD_INTERN:String = "word_indexer";
  val TABLE_FACT_INTERN:String = "fact_indexer";
  val TABLE_EDGE_TYPE_INTERN:String = "edge_type_indexer";
  val TABLE_EDGES:String = "edges";
}
