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
                    unknownInsertionOrDeletion:Double,
                    morphology:Double, wsd:Double,
                    okQuantifier:Double,
                    synonyms:Double,
                    default:Double):WeightVector = {
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
    weights.setCount(unigramDown( ADD_NOUN      ), insertionOrDeletion)
    weights.setCount(unigramUp(   DEL_NOUN      ), insertionOrDeletion)
    weights.setCount(unigramUp(   ADD_OTHER     ), insertionOrDeletion)
    weights.setCount(unigramDown( ADD_OTHER     ), insertionOrDeletion)
    weights.setCount(unigramUp(   DEL_OTHER     ), insertionOrDeletion)
    weights.setCount(unigramDown( DEL_OTHER     ), insertionOrDeletion)
    weights.setCount(unigramDown( ADD_EXISTENTIAL ), insertionOrDeletion)
    weights.setCount(unigramUp(   ADD_EXISTENTIAL ), insertionOrDeletion)
    weights.setCount(unigramDown( DEL_UNIVERSAL ), insertionOrDeletion)
    weights.setCount(unigramUp(   DEL_UNIVERSAL ), insertionOrDeletion)
    // (more fishy insertions or deletions)
    weights.setCount(unigramDown( ADD_VERB      ), unknownInsertionOrDeletion)
    weights.setCount(unigramUp(   DEL_VERB      ), unknownInsertionOrDeletion)
    // (ok quantifier swaps)
    weights.setCount(unigramAny( QUANTIFIER_WEAKEN ), okQuantifier)
    weights.setCount(unigramAny( QUANTIFIER_STRENGTHEN ), okQuantifier)
    weights.setCount(unigramAny( QUANTIFIER_REWORD ), okQuantifier)
    // (synonyms)
    weights.setCount(unigramUp( WORDNET_ADJECTIVE_RELATED ), synonyms)
    weights.setCount(unigramUp( WORDNET_ADVERB_PERTAINYM ), synonyms)
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
    insertionOrDeletion = -0.01,
    unknownInsertionOrDeletion = -0.25,
    morphology = -0.1,
    wsd = Double.NegativeInfinity,
    okQuantifier = -0.01,
    synonyms = -0.02,
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
    wsd = -0.2,
    okQuantifier = -0.01,
    synonyms = -0.2,
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
      new Sentence(inputSentence.toString() + ".")
    } else { inputSentence }

    // Tokenize
    val index:String=>Array[Int] = {(arg:String) =>
      if (Props.NATLOG_INDEXER_LAZY) {
        Utils.index(arg, doHead = false, allowEmpty = false)(Postgres.indexerContains, Postgres.indexerGet, unkProvider)._1
      } else {
        Utils.index(arg, doHead = false, allowEmpty = false)((s:String) => Utils.wordIndexer.containsKey(s), (s:String) => Utils.wordIndexer.get(s), unkProvider)._1
      }}
    val tokens = index(sentence.words.mkString(" "))

    // POS tag
    val (pos, ner, monotone):(Array[Option[String]], Array[String], Array[Monotonicity]) = {
      // (get variables)
      val pos:Array[String] = sentence.pos
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
      val ADV  = "(R.*)".r
      // (find synset POS)
      var tokenI = 0
      val synsetPOS:Array[Option[String]] = Array.fill[Option[String]](chunkedWords.size)( None )
      val synsetNER:Array[String] = Array.fill[String](chunkedWords.size)( "O" )
      val synsetMonotone:Array[Option[Monotonicity]] = Array.fill[Option[Monotonicity]](chunkedWords.size)( None )
      for (i <- 0 until chunkedWords.size) {
        val tokenStart = tokenI
        val tokenEnd = tokenI + chunkedWords(i).replaceAll("\\s+", " ").count( p => p == ' ' ) + 1
        if (Quantifier.quantifierGlosses.contains(chunkedWords(i))) {
          synsetPOS(i) = Some("Q")
        }
        for (k <- (tokenEnd - 1) to tokenStart by -1) {
          synsetPOS(i) = synsetPOS(i).orElse(pos(k) match {
            case NOUN(_) => Some("N")
            case VERB(_) => Some("V")
            case ADJ(_) => Some("J")
            case ADV(_) => Some("R")
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
            case Some("N") => Some(SynsetType.NOUN)
            case Some("V") => Some(SynsetType.VERB)
            case Some("J") => Some(SynsetType.ADJECTIVE)
            case Some("R") => Some(SynsetType.ADVERB)
            case _ => None
          }))
          .setMonotonicity(monotonicity).build()
    }.dropRight(1)  // drop period at the end

    // Create Protobuf Fact
    val fact: Fact.Builder = Fact.newBuilder()
    for (word <- protoWords) { fact.addWord(word) }
    fact.setGloss(sentence.toString())
      .setToString(gloss)
      .setMonotoneBoundary({
        val candidate:Int = pos.indexOf( (x:String) => x == "V")
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
