package org.goobs.truth

import Learn._
import org.goobs.truth.EdgeType._
import edu.stanford.nlp.stats.ClassicCounter
import org.goobs.truth.Messages._

import edu.stanford.nlp.util.logging.Redwood.Util._
import edu.stanford.nlp.Sentence
import edu.smu.tspell.wordnet.{Synset, WordNetDatabase, SynsetType}
import scala.collection.mutable


object NatLog {
  lazy val wordnet:WordNetDatabase = WordNetDatabase.getFileInstance

  /**
   * The actual implementing call for soft and hard NatLog weights.
   */
  def natlogWeights(strictNatLog:Double, similarity:Double, wordnet:Double,
                    insertionOrDeletion:Double,
                    unknownInsertionOrDeletion:Double,
                    morphology:Double, wsd:Double, default:Double):WeightVector = {
    val weights = new ClassicCounter[String]
    if (strictNatLog > 0) { throw new IllegalArgumentException("Weights must always be negative (strictNatLog is not)"); }
    if (similarity > 0) { throw new IllegalArgumentException("Weights must always be negative (similarity is not)"); }
    if (wordnet > 0) { throw new IllegalArgumentException("Weights must always be negative (wordnet is not)"); }
    if (morphology > 0) { throw new IllegalArgumentException("Weights must always be negative (morphology is not)"); }
    if (wsd > 0) { throw new IllegalArgumentException("Weights must always be negative (wsd is not)"); }
    if (default > 0) { throw new IllegalArgumentException("Weights must always be negative (default is not)"); }
    // Set negative weight
    weights.setDefaultReturnValue(default)
    // Set positive weights
    // (unigrams)
    weights.setCount(unigramUp(   WORDNET_UP    ), wordnet)
    weights.setCount(unigramUp(   FREEBASE_UP   ), wordnet)
    weights.setCount(unigramDown( WORDNET_DOWN  ), wordnet)
    weights.setCount(unigramDown( FREEBASE_DOWN ), wordnet)
    // (additions/deletions)
    weights.setCount(unigramDown( ADD_ADJ      ), insertionOrDeletion)
    weights.setCount(unigramUp(   DEL_ADJ      ), insertionOrDeletion)
    weights.setCount(unigramDown( ADD_ADV      ), insertionOrDeletion)
    weights.setCount(unigramUp(   DEL_ADV      ), insertionOrDeletion)
    // (more fishy insertions or deletions)
    weights.setCount(unigramDown( ADD_NOUN      ), unknownInsertionOrDeletion)
    weights.setCount(unigramUp(   DEL_NOUN      ), unknownInsertionOrDeletion)
    weights.setCount(unigramDown( ADD_VERB      ), unknownInsertionOrDeletion)
    weights.setCount(unigramUp(   DEL_VERB      ), unknownInsertionOrDeletion)
    weights.setCount(unigramDown( ADD_OTHER     ), unknownInsertionOrDeletion)
    weights.setCount(unigramUp(   DEL_OTHER     ), unknownInsertionOrDeletion)
    // (bigrams)
    weights.setCount(bigramUp( WORDNET_UP, WORDNET_UP ), strictNatLog)
    weights.setCount(bigramUp( WORDNET_UP, FREEBASE_UP ), strictNatLog)
    weights.setCount(bigramUp( FREEBASE_UP, WORDNET_UP ), strictNatLog)
    weights.setCount(bigramUp( FREEBASE_UP, FREEBASE_UP ), strictNatLog)
    weights.setCount(bigramDown( WORDNET_DOWN, WORDNET_DOWN ), strictNatLog)
    weights.setCount(bigramDown( WORDNET_DOWN, FREEBASE_DOWN ), strictNatLog)
    weights.setCount(bigramDown( FREEBASE_DOWN, WORDNET_DOWN ), strictNatLog)
    weights.setCount(bigramDown( FREEBASE_DOWN, FREEBASE_DOWN ), strictNatLog)
    // Set "don't care" weights
    weights.setCount(unigramAny( MORPH_TO_LEMMA ),    morphology)
    weights.setCount(unigramAny( MORPH_FROM_LEMMA ),  morphology)
    weights.setCount(unigramAny( MORPH_FUDGE_NUMBER), morphology)
    // Set weights we only care about a bit
    weights.setCount(unigramUp(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(unigramDown(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(unigramFlat(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(bigramUp(ANGLE_NEAREST_NEIGHBORS, ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(bigramDown(ANGLE_NEAREST_NEIGHBORS, ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(bigramFlat(ANGLE_NEAREST_NEIGHBORS, ANGLE_NEAREST_NEIGHBORS), similarity)
    // Return
    weights
  }

  /**
   * The naive NatLog hard constraint weights
   */
  def hardNatlogWeights:WeightVector = natlogWeights(
    strictNatLog = -0.0,
    similarity = Double.NegativeInfinity,
    wordnet = -0.01,
    insertionOrDeletion = -0.05,
    unknownInsertionOrDeletion = -0.5,
    morphology = -0.1,
    wsd = Double.NegativeInfinity,
    default = Double.NegativeInfinity)

  /**
   * A soft initialization to NatLog weights; this is the same as
   * hardNatlogWeights, but with a soft rather than hard penalty for invalid
   * weights.
   * The goal is to use this to initialize the search.
   */
  def softNatlogWeights:WeightVector = natlogWeights(
    strictNatLog = -0.01,
    similarity = -1.0,
    wordnet = -0.1,
    insertionOrDeletion = -0.1,
    unknownInsertionOrDeletion = -0.25,
    morphology = -0.01,
    wsd = -0.5,
    default = -2.0)

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
   * Compute the Lesk overlap between a synset and a sentence.
   * For example, the value given the sentences [a, b, c, d] and [a, c, d, e]
   * would be 1^2 2^2 = 5 (for 'a' and 'c d').
   */
  def lesk(a:Synset, b:Array[String]):Double = {
    def allEqual(a:Array[String], startA:Int,
                 b:Array[String], startB:Int, length:Int):Boolean = {
      (0 until length).forall{ (i:Int) => a(startA + i) == b(startB + i) }
    }
    def allFalse(mask:Array[Boolean], start:Int, untilVal:Int):Boolean = {
      (start until untilVal).forall( mask(_) == false )
    }
    // (variables)
    val tokensA:Array[String]
    = a.getDefinition.toLowerCase.split("""\s+""")
    val tokensB:Array[String]
    = b.map( _.toLowerCase )
    val tokensShort = if (tokensA.length < tokensB.length) tokensA else tokensB
    val tokensLong = if (tokensA.length < tokensB.length) tokensB else tokensA
    val mask:Array[Boolean]  = tokensShort.map{ _ => false }

    // Run greedy lesk
    var sum:Int = 0
    for (length <- tokensShort.length to 1 by -1;
         shortStart <- 0 to (tokensShort.length - length);
         longStart <- 0 to (tokensLong.length - length)) {
      if (allFalse(mask, shortStart, shortStart + length) &&
        allEqual(tokensShort, shortStart, tokensLong, longStart, length)) {
        for (i <- shortStart until shortStart + length) { mask(i) = true }
        sum += length * length
      }
    }

    // Return
    sum
  }

  /**
   * Get the best matching word sense for the given word; considering the rest of the 'sentence'.
   * @param word The word to get the synset for.
   * @param sentence The 'sentence' -- that is, containing fact -- of the word.
   * @param pos The 'POS tag' -- that is, synset type -- of the word.
   *            This should be [[None]] if no POS tag is known.
   * @return The most likely Synset; if no information is present to disambiguate, this should return the
   *         first synset that matches the POS tag.
   */
  def getWordSense(word:String, sentence:Sentence, pos:Option[SynsetType]):Int = {
    val synsets:Array[Synset] = wordnet.getSynsets(word)
    if (synsets == null || synsets.size == 0) {
      // Case: sensless
      0
    } else {
      // Case: find WordNet sense
      val synsetsConsidered:mutable.HashSet[Synset] = new mutable.HashSet[Synset]
      val (_, argmaxIndex) = synsets.zipWithIndex.maxBy{ case (synset:Synset, synsetIndex:Int) =>
        if (pos.isDefined && synset.getType != pos.get) {
          -1000.0 + synsetIndex.toDouble / 100.0
        } else {
          val leskSim: Double = math.max(0, lesk(synset, sentence.words.filter(_ != word)) - 1)
          val sensePrior:Double = -1.01 * synsetsConsidered.size
          val glossPriority:Double = 2.01 * math.min(-synset.getWordForms.indexOf(word), 0.0)
          synsetsConsidered += synset
          leskSim + sensePrior + glossPriority
        }
      }
      math.min(31, argmaxIndex + 1)
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
    val index:String=>Array[Int] = {(arg:String) =>
      if (Props.NATLOG_INDEXER_LAZY) {
        Utils.index(arg, doHead = false, allowEmpty = false)(Postgres.indexerContains, Postgres.indexerGet).map(_._1).getOrElse({ warn(s"could not index $arg"); Array[Int]() })
      } else {
        Utils.index(arg, doHead = false, allowEmpty = false)((s:String) => Utils.wordIndexer.containsKey(s), (s:String) => Utils.wordIndexer.get(s)).map(_._1).getOrElse({warn(s"could not index $arg"); Array[Int]() })
      }}

    // Compute Monotonicity
    val (arg1Monotonicity, arg2Monotonicity) = monotonicityMarking(leftArg + " " + rel + " " + rightArg)

    // Tokenize
    val tokens:(Array[Int], Array[Int], Array[Int]) = (index(leftArg), index(rel), index(rightArg))
    val monotoneTaggedTokens:Array[(Int, Int)] =
      (tokens._1.map{ (_, arg1Monotonicity) }.toList ::: tokens._2.map{ (_, arg2Monotonicity) }.toList ::: tokens._3.map{ (_, arg2Monotonicity) }.toList).toArray

    // POS tag
    val sentence:Sentence = Sentence((leftArg + " " + rel + " " + rightArg).split("""\s+"""))
    val pos:Array[Option[SynsetType]] = {
      // (get variables)
      val pos:Array[String] = sentence.pos
      val chunkedWords:Array[String] = (tokens._1.toList ::: tokens._2.toList ::: tokens._3.toList).toArray
        .map ( (w:Int) => if (Props.NATLOG_INDEXER_LAZY) Postgres.indexerGloss(w) else Utils.wordGloss(w) )
      // (regexps)
      val NOUN = "(N.*)".r
      val VERB = "(V.*)".r
      val ADJ  = "(J.*)".r
      val ADV  = "(R.*)".r
      // (find synset POS)
      var tokenI = 0
      val synsetPOS:Array[Option[SynsetType]] = Array.fill[Option[SynsetType]](chunkedWords.size)( None )
      for (i <- 0 until chunkedWords.size) {
        val tokenStart = tokenI
        val tokenEnd = tokenI + chunkedWords(i).count( p => p == ' ' ) + 1
        for (k <- (tokenEnd-1) to tokenStart by -1) {
          synsetPOS(i) = synsetPOS(i).orElse(pos(k) match {
            case NOUN(_) =>
              Some(SynsetType.NOUN)
            case VERB(_) =>
              Some(SynsetType.VERB)
            case ADJ(_) =>
              Some(SynsetType.ADJECTIVE)
            case ADV(_) =>
              Some(SynsetType.ADVERB)
            case _ => None
          })
        }
        tokenI = tokenEnd
      }
      // (filter unknown)
      synsetPOS
    }

    // Create Protobuf Words
    val protoWords = monotoneTaggedTokens.zip(pos).map{ case ((word:Int, monotonicity:Int), pos:Option[SynsetType]) =>
      var monotonicityValue = Monotonicity.FLAT
      if (monotonicity > 0) { monotonicityValue = Monotonicity.UP }
      if (monotonicity < 0) { monotonicityValue = Monotonicity.DOWN }
      val gloss = if (Props.NATLOG_INDEXER_LAZY) Postgres.indexerGloss(word) else Utils.wordGloss(word)
      Word.newBuilder()
        .setWord(word)
        .setGloss(gloss)
        .setPos(pos match {
          case Some(SynsetType.NOUN) => "n"
          case Some(SynsetType.VERB) => "v"
          case Some(SynsetType.ADJECTIVE) => "j"
          case Some(SynsetType.ADVERB) => "r"
          case _ => "?"
        }).setSense(getWordSense(gloss, sentence, pos))
        .setMonotonicity(monotonicityValue).build()
    }

    // Create Protobuf Fact
    val fact: Fact.Builder = Fact.newBuilder()
    for (word <- protoWords) { fact.addWord(word) }
    fact.setGloss(leftArg + " " + rel + " " + rightArg)
        .setToString("[" + rel + "](" + leftArg + ", " + rightArg + ")")
        .build()
  }
}
