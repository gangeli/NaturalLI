package org.goobs.truth

import Learn._
import org.goobs.truth.EdgeType._
import edu.stanford.nlp.stats.ClassicCounter
import org.goobs.truth.Messages._
import edu.stanford.nlp.util.logging.Redwood.Util._
import edu.stanford.nlp.{natlog, Sentence}
import edu.smu.tspell.wordnet.{Synset, WordNetDatabase, SynsetType}
import scala.collection.mutable
import edu.stanford.nlp.natlog.GaborMono

object NatLog {
  lazy val wordnet:WordNetDatabase = WordNetDatabase.getFileInstance

  /**
   * The actual implementing call for soft and hard NatLog weights.
   */
  def natlogWeights(strictNatLog:Double, similarity:Double, wordnet:Double,
                    insertionOrDeletion:Double,
                    verbInsertOrDelete:Double,
                    morphology:Double, wsd:Double,
                    okQuantifier:Double,
                    synonyms:Double,
                    antonym:Double,
                    default:Double):WeightVector = {
    val weights = new ClassicCounter[String]
    if (strictNatLog > 0) { throw new IllegalArgumentException("Weights must always be negative (strictNatLog is not)"); }
    if (similarity > 0) { throw new IllegalArgumentException("Weights must always be negative (similarity is not)"); }
    if (wordnet > 0) { throw new IllegalArgumentException("Weights must always be negative (wordnet is not)"); }
    if (morphology > 0) { throw new IllegalArgumentException("Weights must always be negative (morphology is not)"); }
    if (wsd > 0) { throw new IllegalArgumentException("Weights must always be negative (wsd is not)"); }
    if (okQuantifier > 0) { throw new IllegalArgumentException("Weights must always be negative (okQuantifier is not)"); }
    if (insertionOrDeletion > 0) { throw new IllegalArgumentException("Weights must always be negative (insertionOrDeletion is not)"); }
    if (verbInsertOrDelete > 0) { throw new IllegalArgumentException("Weights must always be negative (verbInsertOrDelete is not)"); }
    if (antonym > 0) { throw new IllegalArgumentException("Weights must always be negative (antonym is not)"); }
    if (default > 0) { throw new IllegalArgumentException("Weights must always be negative (default is not)"); }
    // Set negative weight
    weights.setDefaultReturnValue(default)
    // Set OK weights

    def setCounts(edge:EdgeType, trueMono:Monotonicity, weight:Double) {
      trueMono match {
        case Monotonicity.UP =>
          weights.setCount(monoUp_stateTrue(    edge ), weight)
          weights.setCount(monoDown_stateFalse( edge ), weight)
        case Monotonicity.DOWN =>
          weights.setCount(monoDown_stateTrue(  edge ), weight)
          weights.setCount(monoUp_stateFalse(   edge ), weight)
      }
    }

    // (unigrams)
    setCounts(WORDNET_UP, Monotonicity.UP, wordnet)
    setCounts(FREEBASE_UP, Monotonicity.UP, wordnet)
    setCounts(WORDNET_DOWN, Monotonicity.DOWN, wordnet)
    setCounts(FREEBASE_DOWN, Monotonicity.DOWN, wordnet)
    // (additions/deletions)
    setCounts(ADD_ADJ, Monotonicity.DOWN, insertionOrDeletion)
    setCounts(DEL_ADJ, Monotonicity.UP, insertionOrDeletion)
    setCounts(ADD_NOUN, Monotonicity.DOWN, insertionOrDeletion)
    setCounts(DEL_NOUN, Monotonicity.UP, insertionOrDeletion)
    setCounts(ADD_OTHER, Monotonicity.DOWN, insertionOrDeletion)
    setCounts(DEL_OTHER, Monotonicity.UP, insertionOrDeletion)
    setCounts(ADD_OTHER, Monotonicity.UP, insertionOrDeletion)
    setCounts(DEL_OTHER, Monotonicity.DOWN, insertionOrDeletion)
    // (negation is always ok to add/remove)
    setCounts(ADD_NEGATION, Monotonicity.UP, insertionOrDeletion)
    setCounts(ADD_NEGATION, Monotonicity.DOWN, insertionOrDeletion)
    setCounts(DEL_NEGATION, Monotonicity.UP, insertionOrDeletion)
    setCounts(DEL_NEGATION, Monotonicity.DOWN, insertionOrDeletion)
    // (quantifiers we have to worry about...)
    // INVALID: add existential, monotone down (can weaken to universal)
    setCounts(ADD_EXISTENTIAL, Monotonicity.DOWN, default)
    // VALID: add existential, monotone up (weakened from generic, e.g., 'most')
    setCounts(ADD_EXISTENTIAL, Monotonicity.UP, insertionOrDeletion)
    // VALID del existential, monotone up ('cats have a tail' -> 'cats have tails')
    setCounts(DEL_EXISTENTIAL, Monotonicity.UP, insertionOrDeletion)
    // INVALID del existential, monotone down (I can only think of pathological cases where you would do this)
    setCounts(DEL_EXISTENTIAL, Monotonicity.DOWN, default)
    // INVALID: add universal, monotone down (causes problems TODO think through why)
    setCounts(ADD_UNIVERSAL, Monotonicity.DOWN, default)
    // INVALID: add universal, monotone up (cannot strengthen from generic, e.g., 'most')
    setCounts(ADD_UNIVERSAL, Monotonicity.UP, default)
    // VALID del universal, monotone up (by reverse of add_universal_down)
    setCounts(DEL_UNIVERSAL, Monotonicity.UP, insertionOrDeletion)
    // INVALID del universal, monotone down (cannot weaken to generic, e.g., 'most')
    setCounts(DEL_UNIVERSAL, Monotonicity.DOWN, default)
    // (more fishy insertions or deletions)
    setCounts(DEL_VERB, Monotonicity.UP, verbInsertOrDelete)
    setCounts(DEL_VERB, Monotonicity.DOWN, verbInsertOrDelete)
    // (ok quantifier swaps)
    setCounts(QUANTIFIER_UP,   Monotonicity.UP, okQuantifier)
    setCounts(QUANTIFIER_DOWN, Monotonicity.DOWN, okQuantifier)
    weights.setCount(monoAny_stateTrue(  QUANTIFIER_REWORD ), okQuantifier)
    weights.setCount(monoAny_stateFalse( QUANTIFIER_REWORD ), okQuantifier)
    weights.setCount(monoAny_stateTrue(  QUANTIFIER_NEGATE ), okQuantifier)
    weights.setCount(monoAny_stateFalse( QUANTIFIER_NEGATE ), okQuantifier)
    // (synonyms)
    setCounts(WORDNET_NOUN_SYNONYM, Monotonicity.UP, synonyms)
    setCounts(WORDNET_ADJECTIVE_RELATED, Monotonicity.UP, synonyms)
//    setCounts(WORDNET_ADVERB_PERTAINYM, Monotonicity.UP, synonyms)  // Careful: we're moving to the unknown POS class
    setCounts(WORDNET_NOUN_SYNONYM, Monotonicity.DOWN, synonyms)
    setCounts(WORDNET_ADJECTIVE_RELATED, Monotonicity.DOWN, synonyms)
//    setCounts(WORDNET_ADVERB_PERTAINYM, Monotonicity.DOWN, synonyms)  // Careful: we're moving to the unknown POS class
    // (antonyms)
    setCounts(WORDNET_NOUN_ANTONYM, Monotonicity.UP, antonym)
    setCounts(WORDNET_NOUN_ANTONYM, Monotonicity.DOWN, antonym)
    setCounts(WORDNET_ADJECTIVE_ANTONYM, Monotonicity.UP, antonym)
    setCounts(WORDNET_ADJECTIVE_ANTONYM, Monotonicity.DOWN, antonym)
    setCounts(WORDNET_VERB_ANTONYM, Monotonicity.UP, antonym)
    setCounts(WORDNET_VERB_ANTONYM, Monotonicity.DOWN, antonym)
    // Set "don't care" weights
    weights.setCount(monoAny_stateTrue( MORPH_FUDGE_NUMBER), morphology)
    weights.setCount(monoAny_stateFalse( MORPH_FUDGE_NUMBER), morphology)
    // Set weights we only care about a bit
    weights.setCount(monoUp_stateTrue(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(monoDown_stateTrue(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(monoFlat_stateTrue(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(monoUp_stateFalse(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(monoDown_stateFalse(ANGLE_NEAREST_NEIGHBORS), similarity)
    weights.setCount(monoFlat_stateFalse(ANGLE_NEAREST_NEIGHBORS), similarity)
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
    insertionOrDeletion = -0.01,
    verbInsertOrDelete = -0.01,
    morphology = -0.1,
    wsd = Double.NegativeInfinity,
    okQuantifier = -0.01,
    synonyms = -0.015,
    antonym = -0.015,
    default = Double.NegativeInfinity)

  def hardNatlogNoMutations:WeightVector = natlogWeights(
    strictNatLog = -0.0,
    similarity = Double.NegativeInfinity,
    wordnet = Double.NegativeInfinity,
    insertionOrDeletion = -0.01,
    verbInsertOrDelete = -0.01,
    morphology = Double.NegativeInfinity,
    wsd = Double.NegativeInfinity,
    okQuantifier = -0.01,
    synonyms = Double.NegativeInfinity,
    antonym = Double.NegativeInfinity,
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
    verbInsertOrDelete = -0.25,
    morphology = -0.01,
    wsd = -0.2,
    okQuantifier = -0.01,
    synonyms = -0.2,
    antonym = -0.2,
    default = -2.0)

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
  def getWordSense(word:String, ner:String, sentence:Sentence, pos:Option[SynsetType]):Int = {
    val synsets:Array[Synset] = wordnet.getSynsets(word)
    if (synsets == null || synsets.size == 0 || !pos.isDefined || ner != "O" || Quantifier.quantifierGlosses.contains(word)) {
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

  private def annotate(inputSentence:Sentence, gloss:String, unkProvider:String=>String=(x:String)=>Utils.WORD_UNK):Fact = {
    if (inputSentence.length == 0) {
      return Fact.newBuilder().setGloss("").setToString("<EMPTY FACT>").build()
    }
    // Enforce punctuation (for parser, primarily)
    val sentence = if (inputSentence.word.last != "." && inputSentence.word.last != "?" && inputSentence.word.last != "!") {
      new Sentence(inputSentence.toString() + " .")
    } else { inputSentence }

    // Tokenize
    val index:String=>Array[Int] = {(arg:String) =>
      if (Props.NATLOG_INDEXER_LAZY) {
        Utils.index(arg, doHead = false, allowEmpty = false)(Postgres.indexerContains, Postgres.indexerGet, unkProvider)._1
      } else {
        Utils.index(arg, doHead = false, allowEmpty = false)((s:String) => Utils.wordIndexer.containsKey(s), (s:String) => Utils.wordIndexer.get(s), unkProvider)._1
      }}
    val tokens = index(sentence.words.take(sentence.length - 1).mkString(" "))

    // POS tag
    val (pos, ner, monotone):(Array[Option[String]], Array[String], Array[Monotonicity]) = {
      // (get variables)
      val pos:Array[String] = {
        val candidate = sentence.pos
        // Fix up things like "running shoes"
        for ( i <- 0 until (candidate.length - 1)) {
          if (candidate(i) == "VBG" && candidate(i + 1).startsWith("N")) { candidate(i) = "JJVBG" }
        }
        candidate
      }
      val ner:Array[String] = sentence.ner
      val monotonicity:Array[Monotonicity] = GaborMono.getInstance().annotate(sentence.parse).map {
        case natlog.Monotonicity.UP => Monotonicity.UP
        case natlog.Monotonicity.DOWN => Monotonicity.DOWN
        case natlog.Monotonicity.NON => Monotonicity.FLAT
      }
      val chunkedWords:Array[String] = tokens.map ( (w:Int) => if (Props.NATLOG_INDEXER_LAZY) Postgres.indexerGloss(w) else Utils.wordGloss(w) )
      // (regexps)
      val NOUN = "(N.*)".r
      val VERB = "(V.*)".r
      val ADJ  = "(J.*)".r
      // (find synset POS)
      var tokenI = 0
      val synsetPOS:Array[Option[String]] = Array.fill[Option[String]](chunkedWords.size)( None )
      val synsetNER:Array[String] = Array.fill[String](chunkedWords.size)( "O" )
      val synsetMonotone:Array[Option[Monotonicity]] = Array.fill[Option[Monotonicity]](chunkedWords.size)( None )
      for (i <- 0 until chunkedWords.size) {
        val tokenStart = tokenI
        val tokenEnd = tokenI + chunkedWords(i).replaceAll("\\s+", " ").count( p => p == ' ' ) + 1
        if (Quantifier.quantifierGlosses.contains(chunkedWords(i))) {
          synsetPOS(i) = Some(Quantifier.get(chunkedWords(i)).closestMeaning match {
            case Quantifier.LogicalQuantifier.FORALL => "a"
            case Quantifier.LogicalQuantifier.EXISTS => "e"
            case Quantifier.LogicalQuantifier.MOST => "m"
            case Quantifier.LogicalQuantifier.NONE => "g"
            case _ => throw new NoSuchElementException("Could not find logical meaning: " + Quantifier.get(chunkedWords(i)).closestMeaning)
          })
        }
        for (k <- (tokenEnd - 1) to tokenStart by -1) {
          synsetPOS(i) = synsetPOS(i).orElse(pos(k) match {
            case NOUN(_) => Some("n")
            case VERB(_) => Some("v")
            case ADJ(_) => Some("j")
            case _ => None
          })
          synsetMonotone(i) = synsetMonotone(i).orElse(Some(monotonicity(k)))
          synsetNER(i) = ner(k)
        }
        tokenI = tokenEnd
      }
      // (filter unknown)
      (synsetPOS, synsetNER, synsetMonotone.map {
        case Some(x) => x
        case None => Monotonicity.FLAT
      })
    }

    // Create Protobuf Words
    val protoWords:Seq[Word] = tokens.zip(monotone).zip(ner).zip(pos).map {
      case (((word: Int, monotonicity: Monotonicity), ner:String), pos: Option[String]) =>
        val gloss = if (Props.NATLOG_INDEXER_LAZY) Postgres.indexerGloss(word) else Utils.wordGloss(word)
        Word.newBuilder()
          .setWord(word)
          .setGloss(gloss)
          .setPos(pos.getOrElse("?"))
          .setSense(getWordSense(gloss, ner, sentence, pos match {
            case Some("n") => Some(SynsetType.NOUN)
            case Some("v") => Some(SynsetType.VERB)
            case Some("j") => Some(SynsetType.ADJECTIVE)
            case Some("r") => Some(SynsetType.ADVERB)
            case _ => None
          }))
          .setMonotonicity(monotonicity).build()
    }

    // Create Protobuf Fact
    val fact: Fact.Builder = Fact.newBuilder()
    for (word <- protoWords) { fact.addWord(word) }
    fact.setGloss(sentence.toString())
      .setToString(gloss)
      .setMonotoneBoundary({
        val candidate:Int = pos.indexWhere( (y:Option[String]) => y.isDefined && (y.get == "V" || y.get == "v") )
        if (candidate < 0) pos.length - 1 else candidate
      }).build()

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
  def annotate(leftArg:String, rel:String, rightArg:String, unkProvider:String=>String):Fact = {
    val sentence:Sentence = new Sentence(leftArg + " " + rel + " " + rightArg)
    annotate(sentence, "[" + rel + "](" + leftArg + ", " + rightArg + ")", unkProvider)
  }

  def annotate(leftArg:String, rel:String, rightArg:String):Fact = annotate(leftArg, rel, rightArg, x => Utils.WORD_UNK)

  /**
   * An instantiation of the Ollie processing pipeline; this is only called on demand
   * to avoid loading models.
   */
  lazy val ollie:(String=>Iterable[(String, String, String)]) = {
    import java.io._
    import java.net._
    import scala.sys.process._

    // Ollie Daemon
    val ollie = new Thread() {
      override def run() {
        log("starting Ollie (server)...")
        List("java", "-mx1g", "-XX:+UseConcMarkSweepGC", "-jar", Props.NATLOG_OLLIE_JAR.getPath,
          "--encoding", "UTF8", "--threshold", "0.0", "--malt-model", Props.NATLOG_OLLIE_MALT_PATH.getPath,
          "--ignore-errors", "--output-format", "tabbed", "--server", Props.NATLOG_OLLIE_PORT.toString) ! ProcessLogger( log("ollie", _) )
      }
    }
    ollie.setDaemon(true)
    ollie.start()

    // Wait for connection
    var connected:Boolean = false
    while (!connected) {
      try {
        new Socket("localhost", Props.NATLOG_OLLIE_PORT).close()
        connected = true
      } catch {
        case (e:java.net.ConnectException) => Thread.sleep(1000)
      }
    }
    log("connection valid to Ollie.")

    // Ollie function
    (input: String) => {
      val socket = new Socket("localhost", Props.NATLOG_OLLIE_PORT)
      // Write request
      new PrintWriter(socket.getOutputStream).print(input)
      socket.getOutputStream.flush()
      socket.shutdownOutput()
      // Read response
      val response = scala.io.Source.fromInputStream(socket.getInputStream).getLines().map{ (line:String) =>
        println("## " + line)
        val fields = line.substring(2).split("\t")
        (fields(1), fields(2), fields(3))
      }.toList
      // Clean up and return
      socket.close()
      response
    }
  }

  def annotate(query:String, unkProvider:String=>String):Iterable[Fact] = {
    val sentence:Sentence = new Sentence(query)
    Seq(annotate(sentence, query, unkProvider))
  }

  def annotate(query:String):Iterable[Fact] = annotate(query, x => Utils.WORD_UNK)
}
