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
    val uri = "jdbc:postgresql://" + Props.PSQL_HOST + ":" + Props.PSQL_PORT + "/" + Props.PSQL_DB;
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

  def TABLE_FACT_INTERN:String = "fact_indexer";
  def TABLE_PHRASE_INTERN:String = "phrase_indexer";
  def TABLE_WORD_INTERN:String = "word_indexer";
}
