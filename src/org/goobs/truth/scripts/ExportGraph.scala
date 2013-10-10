package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import scala.io.Source

import java.io.File
import java.io.PrintWriter
import java.sql.ResultSet

import org.goobs.truth._
import org.goobs.truth.Postgres._
import org.goobs.truth.Implicits._
import org.goobs.truth.EdgeType._
import org.goobs.truth.Utils._

object ExportGraph {
  def main(args:Array[String]):Unit = {
    // Dump Words
    printToFile(new File("words.tab")){ (pw:PrintWriter) =>
      slurpTable(Postgres.TABLE_WORD_INTERN, (rs:ResultSet) => {
        val key:Int = rs.getInt("index")
        val gloss:String = rs.getString("gloss")
        pw.println(key + "\t" + gloss);
      })
    }
    
  }
}
