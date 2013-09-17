package org.goobs.truth.scripts

import scala.collection.JavaConversions._
import scala.io.Source

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileWriter
import java.util.zip.GZIPInputStream

import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import java.sql.ResultSet

import org.goobs.truth.Props
import org.goobs.truth.Implicits._
import org.goobs.truth.Postgres._

object GrokFreebase {
  private val logger = Redwood.channels("Edges")

  val interestingRelations:Set[String] = Set[String](
    "ns:people.person.place_of_birth",
    "ns:people.person.nationality",
    "ns:people.person.gender",
    "ns:people.person.profession",
    "ns:people.person.religion",
    "ns:people.person.ethnicity",
    "ns:people.person.education",
    "ns:people.person.places_lived",
    "ns:organization.organization.geographic_scope",
    "ns:organization.organization.sectors",
    "ns:organization.organization.mailing_address",
    "ns:organization.organization.organization_type",
    "ns:organization.organization.location",
    "ns:business.company_name_change",
    "ns:location.location.contains",
    "ns:location.location.containedby",
    "ns:location.location.partiallycontains",
    "ns:location.location.partially_contained_by",
    "ns:location.location.adjoin_s",
    "ns:music.recording.artist",
    "ns:common.notable_for.display_name"
  )

  val nameRelations:Set[String] = Set[String](
    "ns:common.topic.alias"
  )

  def main(args:Array[String]):Unit = {
    Props.exec(() => {
      // Get word interner
      forceTrack("Reading words")
      var wordInterner = new scala.collection.mutable.HashMap[String, Int]
      slurpTable(TABLE_WORD_INTERN, {(r:ResultSet) =>
        val key:Int = r.getInt("key")
        val gloss:String = r.getString("gloss")
        wordInterner(gloss) = key
      })
      endTrack("Reading words")

      // Get names as strings
      forceTrack("Gathering names")
      val id2name = new scala.collection.mutable.HashMap[String,List[String]]()
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.FREEBASE_RAW_PATH)))).getLines.drop(7)) {
        val fields = line.split("\t")
        if (fields.length == 3 && nameRelations.contains(fields(1)) && fields(2).endsWith("@en.")) {
          val id = fields(0)
          val name = fields(2).substring(1, fields(2).length - 5)
          val existing = 
            if (!id2name.contains(id)) { Nil } else { id2name(id) }
          id2name(id) = name :: existing
          logger.log(id + " -> " + name)
        }
      }
      endTrack("Gathering names")

      // Read the relation
      forceTrack("Gathering relations")
      val writer = new FileWriter(Props.FREEBASE_PATH)
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.FREEBASE_RAW_PATH)))).getLines.drop(7)) {
        val fields = line.split("\t")
        if (fields.length == 3 && interestingRelations.contains(fields(1))) {
          // Read relation
          val leftArgs:Seq[String] = id2name(fields(0))
          val relation:String = fields(1)
          val rightArgs:Seq[String] = {
            val raw = fields(2)
            id2name.get(raw) match {
              case Some(x) => x
              case None => raw match {
                case r""".?"([^\"]+)${text}"@en.?""" => List(text)
                case r""".?"([^\"]+)${text}".*.?""" => List(text)
                case _ => List(raw)
              }

            }
          }
          // Save relation
          for (leftArg <- leftArgs; rightArg <- rightArgs) {
            try {
              writer.write(leftArg + "\t" + relation + "\t" + rightArg)
            } catch { case (e:Exception) => throw new RuntimeException(e) }
          }
        }
      }
      writer.close
      endTrack("Gathering relations")

      // Get interesting relations
    }, args)
  }
}
