package org.goobs.naturalli.scripts

import org.goobs.naturalli._

import edu.stanford.nlp.Sentence

/**
 * TODO(gabor) JavaDoc
 *
 * @author gabor
 */
object PruneClosedClassNN {

  def prune() {
    if (EdgeType.ANGLE_NEAREST_NEIGHBORS.id != 10) {
      throw new IllegalStateException("Just in case -- double check and update me if it's ok to continue")
    }
    Postgres.withConnection{ (psql) =>
      val stmt = psql.prepareStatement(s"DELETE FROM ${Postgres.TABLE_EDGES} WHERE type=${EdgeType.ANGLE_NEAREST_NEIGHBORS.id} AND (source=? OR sink=?);")
      var count = 0
      for (gloss:String <- Utils.wordGloss) {
        // Filter by POS
        val posChar:Char = new Sentence(gloss).pos.map( _.charAt(0).toLower ).groupBy(identity).maxBy(_._2.size)._1
        posChar match {
          case 'n' => /* do nothing */
          case 'v' => /* do nothing */
          case 'j' => /* do nothing */
          case 'r' => /* do nothing */
          case 'f' => /* do nothing */
          case _ =>
            val posChar2:Char = new Sentence(gloss(0).toUpper + gloss.substring(1)).pos.map( _.charAt(0).toLower ).groupBy(identity).maxBy(_._2.size)._1
            posChar2 match {
              case 'n' => /* do nothing */
              case 'v' => /* do nothing */
              case 'j' => /* do nothing */
              case 'r' => /* do nothing */
              case 'f' => /* do nothing */
              case _ =>
                count += 1
                val index = Utils.wordIndexer.get(gloss)
                stmt.setInt(1, index)
                stmt.setInt(2, index)
                stmt.addBatch()
            }
        }
        // Filter by closed class
        if (Utils.AUXILLIARY_VERBS.contains(gloss.toLowerCase) || Utils.INTENSIONAL_ADJECTIVES.contains(gloss.toLowerCase)) {
          count += 1
          val index = Utils.wordIndexer.get(gloss)
          stmt.setInt(1, index)
          stmt.setInt(2, index)
          stmt.addBatch()
        }
      }
      println(s"$count words pruned")
      println("flushing...")
      stmt.executeBatch()
      println("done")
    }
  }

  def main(args:Array[String]):Unit = {
    prune()
  }

}
