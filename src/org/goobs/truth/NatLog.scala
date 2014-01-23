package org.goobs.truth

import Learn._
import org.goobs.truth.EdgeType._
import edu.stanford.nlp.stats.ClassicCounter
import org.goobs.truth.Messages._


object NatLog {
  /**
   * The actual implementing call for soft and hard NatLog weights.
   */
  def natlogWeights(positiveWeight:Double, negativeWeight:Double, dontCareWeight:Double):WeightVector = {
    val weights = new ClassicCounter[String]
    // Set negative weight
    weights.setDefaultReturnValue(negativeWeight)
    // Set positive weights
    // (unigrams)
    weights.setCount(unigramUp(   WORDNET_UP    ), positiveWeight)
    weights.setCount(unigramUp(   FREEBASE_UP   ), positiveWeight)
    weights.setCount(unigramDown( WORDNET_DOWN  ), positiveWeight)
    weights.setCount(unigramDown( FREEBASE_DOWN ), positiveWeight)
    // (bigrams)
    weights.setCount(bigramUp( WORDNET_UP, WORDNET_UP ), positiveWeight)
    weights.setCount(bigramUp( WORDNET_UP, FREEBASE_UP ), positiveWeight)
    weights.setCount(bigramUp( FREEBASE_UP, WORDNET_UP ), positiveWeight)
    weights.setCount(bigramUp( FREEBASE_UP, FREEBASE_UP ), positiveWeight)
    weights.setCount(bigramDown( WORDNET_DOWN, WORDNET_DOWN ), positiveWeight)
    weights.setCount(bigramDown( WORDNET_DOWN, FREEBASE_DOWN ), positiveWeight)
    weights.setCount(bigramDown( FREEBASE_DOWN, WORDNET_DOWN ), positiveWeight)
    weights.setCount(bigramDown( FREEBASE_DOWN, FREEBASE_DOWN ), positiveWeight)
    // Set "don't care" weights
    weights.setCount(unigramAny( MORPH_TO_LEMMA ),    0.0)
    weights.setCount(unigramAny( MORPH_FROM_LEMMA ),  0.0)
    weights.setCount(unigramAny( MORPH_FUDGE_NUMBER), 0.0)
    // Set weights we only care about a bit
    weights.setCount(unigramUp(ANGLE_NEAREST_NEIGHBORS), dontCareWeight)
    weights.setCount(unigramDown(ANGLE_NEAREST_NEIGHBORS), dontCareWeight)
    weights.setCount(unigramFlat(ANGLE_NEAREST_NEIGHBORS), dontCareWeight)
    weights.setCount(bigramUp(ANGLE_NEAREST_NEIGHBORS, ANGLE_NEAREST_NEIGHBORS), dontCareWeight)
    weights.setCount(bigramDown(ANGLE_NEAREST_NEIGHBORS, ANGLE_NEAREST_NEIGHBORS), dontCareWeight)
    weights.setCount(bigramFlat(ANGLE_NEAREST_NEIGHBORS, ANGLE_NEAREST_NEIGHBORS), dontCareWeight)
    // Return
    weights
  }

  /**
   * The naive NatLog hard constraint weights
   */
  def hardNatlogWeights:WeightVector = natlogWeights(0.0, Double.NegativeInfinity, Double.NegativeInfinity)

  /**
   * A soft initialization to NatLog weights; this is the same as
   * hardNatlogWeights, but with a soft rather than hard penalty for invalid
   * weights.
   * The goal is to use this to initialize the search.
   */
  def softNatlogWeights:WeightVector = natlogWeights(1.0, -1.0, -0.25)

  /**
   * Determine the monotonicity of a sentence, according to the quantifier it starts with.
   * @param sentence The sentence, in surface form. It should start with the quantifier (for now).
   * @return A pair of integers, one for each of the two quantifier arguments, for positive (>0), negative (<0) or flat(=0)
   *         monotonicity.
   */
  private def monotonicityMarking(sentence:String):(Int, Int) = {
    val lower = sentence.toLowerCase.replaceAll( """\s+""", " ")
    if (lower.startsWith("all ") ||
        lower.startsWith("any ") ||
        lower.startsWith("every ")    ) {
      (-1, +1)
    } else if (lower.startsWith("most ") ||
               lower.startsWith("enough ") ||
               lower.startsWith("few ") ||
               lower.startsWith("lots of ")) {
      (0, +1)
    } else if (lower.contains(" dont ") ||
               lower.contains(" don't ") ||
               lower.contains(" do not ") ||
               lower.contains(" are not ") ||
               lower.startsWith("no ") ||
               lower.startsWith("none of ")) {
      (-1, -1)
    } else if (lower.startsWith("some ") ||
               lower.startsWith("there are ") ||
               lower.startsWith("there exists ") ||
               lower.startsWith("there exist ")) {
      (+1, +1)
    } else {
      (-1, +1)  // default to something like "almost all", approximated as "all"
    }
  }

  /**
   * Annotate a given fact according to monotonicity (and possibly other flags).
   * This output is ready to send as a query to the search server.
   *
   * @param leftArg Arg 1 of the triple.
   * @param rel The plain-text relation of the triple
   * @param rightArg Arg 2 of the triple.
   * @return An annotated query fact, marked with monotonicity.
   */
  def annotate(leftArg:String, rel:String, rightArg:String):Fact = {
    var fact:Fact.Builder = Fact.newBuilder()
    // Compute Monotonicity
    val (arg1Monotonicity, arg2Monotonicity) = monotonicityMarking(leftArg + " " + rel + " " + rightArg)
    // Tokenize
    val words:List[Word] = List((leftArg, arg1Monotonicity), (rel, arg2Monotonicity), (rightArg, arg2Monotonicity)).flatMap{ case (arg:String, monotonicity:Int) =>
      val option:Option[(Array[Int], Int)] =
        if (Props.NATLOG_INDEXER_LAZY) {
          Utils.index(arg, doHead = false, allowEmpty = false)(Postgres.indexerContains, Postgres.indexerGet)
        } else {
          Utils.index(arg, doHead = false, allowEmpty = false)((s:String) => Utils.wordIndexer.containsKey(s), (s:String) => Utils.wordIndexer.get(s))
        }
      if (option.isDefined) {
        for (token <- option.get._1 ) yield {
          Word.newBuilder()
            .setWord(token)
            .setGloss(if (Props.NATLOG_INDEXER_LAZY) Postgres.indexerGloss(token) else Utils.wordGloss(token))
            .setMonotonicity(monotonicity).build()
        }
      } else {
        Nil
      }
    }
    // Create fact
    for (word <- words) { fact.addWord(word) }
    fact.setGloss(leftArg + " " + rel + " " + rightArg)
        .setToString("[" + rel + "](" + leftArg + ", " + rightArg + ")")
        .build()
  }
}
