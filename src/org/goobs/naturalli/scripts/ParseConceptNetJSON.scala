package org.goobs.naturalli.scripts

import com.google.gson.Gson
import java.io.{File, PrintWriter}
import org.goobs.naturalli.Utils

/**
 * Parse the ConceptNet flat JSON file into a query file
 *
 * @author gabor
 */
object ParseConceptNetJSON {

  case class Entry( rel:String,
                    dataset:String,
                    source_uri:String,
                    start:String,
                    surfaceText:String,
                    features:Array[String],
                    license:String,
                    end:String,
                    id:String,
                    weight:Double) { }

  val VALID_RELATIONS = Set[String](
  "/r/MemberOf",
  "/r/HasA",
  "/r/UsedFor",
  "/r/CapableOf",
  "/r/Causes",
  "/r/HasProperty",
  "/r/Desires",
  "/r/CreatedBy"
  )

  def main(args:Array[String]):Unit = {
    val gson = new Gson()
    Utils.printToFile(new File("etc/conceptnet/extractions.tab")){ (out:PrintWriter) =>
      var i = 0
      for (file <- new File("/home/gabor/tmp/assertions/").listFiles();
           line <-io.Source.fromFile(file).getLines()) {
        val entry:Entry = gson.fromJson(line, classOf[Entry])
        if (entry.surfaceText != null && entry.weight > 1.0 && entry.start.startsWith("/c/en/") &&
          VALID_RELATIONS(entry.rel) && entry.surfaceText.split("""\s+""").length < 10) {
          val gloss = entry.surfaceText.replaceAll("""\[\[""", "").replaceAll("""\]\]""", "")
          out.println(s"${entry.weight}\t$gloss")
        }
        i += 1
        if (i % 1000000 == 0) {
          println(s"processed ${i / 1000000}M facts")
        }
      }
    }
    println("DONE")
  }

}
