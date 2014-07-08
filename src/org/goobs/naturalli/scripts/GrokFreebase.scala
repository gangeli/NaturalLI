package org.goobs.naturalli.scripts

import scala.collection.JavaConversions._
import scala.io.Source

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileWriter
import java.util.zip.GZIPInputStream

import edu.stanford.nlp.util.logging.Redwood
import edu.stanford.nlp.util.logging.Redwood.Util._

import java.sql.ResultSet

import org.goobs.naturalli.Props
import org.goobs.naturalli.Implicits._
import org.goobs.naturalli.Postgres._

object GrokFreebase {
  private val logger = Redwood.channels("Edges")

  val interestingRelations:Set[String] = Set[String](
    "fb:people.person.place_of_birth",
    "fb:people.person.nationality",
    "fb:people.person.gender",
    "fb:people.person.profession",
    "fb:people.person.religion",
    "fb:people.person.ethnicity",
    "fb:people.person.education",
    "fb:people.person.places_lived",
    "fb:organization.organization.geographic_scope",
    "fb:organization.organization.sectors",
    "fb:organization.organization.mailing_address",
    "fb:organization.organization.organization_type",
    "fb:organization.organization.location",
    "fb:business.company_name_change",
    "fb:location.location.contains",
    "fb:location.location.containedby",
    "fb:location.location.partiallycontains",
    "fb:location.location.partially_contained_by",
    "fb:location.location.adjoin_s",
    "fb:music.recording.artist",
    "fb:common.notable_for.display_name"
  )

  val nameRelations:Set[String] = Set[String](
    "fb:common.topic.alias",
    "fb:type.object.name"
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
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.SCRIPT_FREEBASE_RAW_PATH)))).getLines.drop(7)) {
        val fields = line.split("\t")
        if (fields.length == 3 && nameRelations.contains(fields(1)) && fields(2).endsWith("@en.")) {
          val id = fields(0)
          val name = fields(2).substring(1, fields(2).length - 5)
          val existing = 
            if (!id2name.contains(id)) { Nil } else { id2name(id) }
          id2name(id) = name :: existing
          logger.debug(id + " -> " + name)
        }
      }
      logger.log(id2name.size + " names found")
      endTrack("Gathering names")

      // Read the relation
      forceTrack("Gathering relations")
      val writer = new FileWriter(Props.SCRIPT_FREEBASE_PATH)
      for (line <- Source.fromInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(Props.SCRIPT_FREEBASE_RAW_PATH)))).getLines.drop(7)) {
        val fields = line.split("\t")
        if (fields.length == 3 && interestingRelations.contains(fields(1))) {
          // Read relation
          val leftArgs:Seq[String] = id2name.get(fields(0)).getOrElse(List[String]())
          val relation:String = fields(1)
          val rightArgs:Seq[String] = {
            val raw = fields(2)
            id2name.get(raw)
                .orElse(id2name.get(raw.substring(0, raw.length-1))) match {
              case Some(x) => x
              case None => raw match {
                case r""".?"([^\"]+)${text}"@en.?""" => List(text)
                case r""".?"([^\"]+)${text}"@[a-z]{2}.?""" => List()
                case r""".?"([^\"]+)${text}".*.?""" => List(text)
                case _ => logger.debug("unknown value: " + raw); List[String]()
              }

            }
          }
          // Save relation
          for (leftArg <- leftArgs; rightArg <- rightArgs) {
            try {
              writer.write(leftArg + "\t" + relation + "\t" + rightArg + "\n")
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
