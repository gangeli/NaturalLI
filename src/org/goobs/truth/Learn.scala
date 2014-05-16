package org.goobs.truth

import edu.stanford.nlp.stats.{Counters, ClassicCounter, Counter}
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.EdgeType.EdgeType
import org.goobs.truth.Messages._
import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.util.{Sets, Execution}
import java.io.{PrintWriter, File}
import java.util.Properties
import com.typesafe.config.{Config, ConfigFactory}
import scala.collection.JavaConversions._
import org.goobs.truth.TruthValue.TruthValue

import scala.language.implicitConversions
import scala.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration


object Learn extends Client {
  type WeightVector = Counter[String]

  def monoUp_stateTrue(edge:EdgeType):String   = "^,T" + "::" + edge
  def monoDown_stateTrue(edge:EdgeType):String = "v,T" + "::" + edge
  def monoFlat_stateTrue(edge:EdgeType):String = "-,T" + "::" + edge
  def monoAny_stateTrue(edge:EdgeType):String  = "*,T" + "::" + edge

  def monoUp_stateFalse(edge:EdgeType):String   = "^,F" + "::" + edge
  def monoDown_stateFalse(edge:EdgeType):String = "v,F" + "::" + edge
  def monoFlat_stateFalse(edge:EdgeType):String = "-,F" + "::" + edge
  def monoAny_stateFalse(edge:EdgeType):String  = "*,F" + "::" + edge

  def feature(edge:EdgeType, truth:Boolean, mono:Monotonicity):String = {
    truth match {
      case true => mono match {
        case Monotonicity.UP             => monoUp_stateTrue(edge)
        case Monotonicity.DOWN           => monoDown_stateTrue(edge)
        case Monotonicity.FLAT           => monoFlat_stateTrue(edge)
        case Monotonicity.ANY_OR_INVALID => monoAny_stateTrue(edge)
      }
      case false => mono match {
        case Monotonicity.UP             => monoUp_stateFalse(edge)
        case Monotonicity.DOWN           => monoDown_stateFalse(edge)
        case Monotonicity.FLAT           => monoFlat_stateFalse(edge)
        case Monotonicity.ANY_OR_INVALID => monoAny_stateFalse(edge)
      }
    }
  }

  /** Implicit to convert from a weight counter to a weight vector */
  implicit def flattenWeights(w:WeightVector):Array[Double] = {
    (w.getCount("bias") :: w.getCount("resultCount") ::
      (for (e <- EdgeType.values.toList;
         truth <- Array[Boolean](true, false);
         mono <- Monotonicity.values().toArray) yield {
      w.getCount(feature(e, truth, mono))
      })).toArray
  }

  /** Implicit to convert from a weight vector to a weight counter */
  def inflateWeights(w:Array[Double], ignoreValue:Double):WeightVector = {
    val feats = new ClassicCounter[String]
    if (w(0) != ignoreValue) { feats.incrementCount("bias", w(0)) }
    if (w(1) != ignoreValue) { feats.incrementCount("resultCount", w(1)) }
    var i:Int = 2
    for (e <- EdgeType.values.toList;
          truth <- Array[Boolean](true, false);
          mono <- Monotonicity.values().toArray) {
      if (w(i) != ignoreValue) { feats.setCount(feature(e, truth, mono), w(i)) }
      i += 1
    }
    feats
  }
  implicit def inflateWeights(w:Array[Double]):WeightVector = inflateWeights(w, 0.0)

  /** Write a weight vector to a print writer (usually, a file). */
  def serialize(w:WeightVector, p:PrintWriter):Unit = {
    for ( (key, value) <- Counters.asMap(w) ) {
      p.println(key + "\t" + value)
    }
  }

  /** Write a weight vector to a file. Returns the same file that we wrote to */
  def serialize(w:WeightVector, f:File):File = { Utils.printToFile(f)( Learn.serialize(w, _) ); f }

  /** Read a weight vector from a list of entries (usually, lines of a file) */
  def deserialize(lines:Seq[String]):WeightVector = {
    lines.foldLeft(new ClassicCounter[String]){ case (w:ClassicCounter[String], line:String) =>
      val fields = line.split("\t")
      w.setCount(fields(0), fields(1).toDouble)
      w
    }
  }

  /** Read a weight vector from a file */
  def deserialize(f:File):WeightVector = deserialize(io.Source.fromFile(f).getLines().toArray)

  /**
   * Serialize a weight vector into a protobuf to send to the server.
   * Note that the semantics of the serialized weights is the negative of the weights
   * used on the client.
   *
   * @param weights The weight vector to serialize
   * @return A protobuf message encoding the <b>cost</b> of each weight
   */
  def weightsToCosts(weights:WeightVector):Costs = {
    // Function to set the unlexicalized weights
    def unlexicalizedWeights(stateTrue:EdgeType=>String, stateFalse:EdgeType=>String):UnlexicalizedCosts = {
      val builder = UnlexicalizedCosts.newBuilder()
      for (from <- EdgeType.values.toArray.sortWith({ (a:EdgeType.Value, b:EdgeType.Value) => a.id < b.id })) {
        val trueWeight = weights.getCount(stateTrue(from)).toFloat
        val falseWeight = weights.getCount(stateFalse(from)).toFloat
        assert(trueWeight <= 0, "trueWeight=" + trueWeight)
        assert(falseWeight <= 0, "falseWeight=" + falseWeight)
        builder.addTrueCost(-trueWeight)
        builder.addFalseCost(-falseWeight)
      }
      builder.build()
    }

    Costs.newBuilder()
      .setUnlexicalizedMonotoneUp(unlexicalizedWeights(monoUp_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneDown(unlexicalizedWeights(monoDown_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneFlat(unlexicalizedWeights(monoFlat_stateTrue, monoFlat_stateFalse))
      .setUnlexicalizedMonotoneAny(unlexicalizedWeights(monoAny_stateTrue, monoAny_stateFalse))
      .setBias(weights.getCount("bias").toFloat)
      .build()
  }

  def featurize(path:Inference):WeightVector = {
    // Recursive call
    val features
      = if (path.hasImpliedFrom) featurize(path.getImpliedFrom)
        else new ClassicCounter[String]
    if (path.hasIncomingEdgeType && path.getIncomingEdgeType != 63) {
      // Variables for score
      val edge: EdgeType.Value = EdgeType(path.getIncomingEdgeType)
      val state: CollapsedInferenceState
      = if (path.hasImpliedFrom && path.getImpliedFrom.hasState) path.getImpliedFrom.getState
        else CollapsedInferenceState.TRUE
      val monotonicity: Monotonicity = if (path.hasMonotoneContext) path.getMonotoneContext else Messages.Monotonicity.UP
      // Compute score
      val useTrueWeights:Boolean = state == CollapsedInferenceState.TRUE
      val edgeCost:Double = if (path.hasIncomingEdgeCost) path.getIncomingEdgeCost else 1.0
      val featureKey:String = feature(edge, useTrueWeights, monotonicity)
      assert(!edgeCost.isNaN)
      assert(!edgeCost.isInfinite)
      features.incrementCount(featureKey, edgeCost)
    }
    // Return
    features
  }

  private def dotProduct(a:WeightVector, b:WeightVector):Double = {
    var product = 0.0
    for (key <- Sets.union(a.keySet, b.keySet())) {
      val aCount = a.getCount(key)
      val bCount = b.getCount(key)
      assert(!aCount.isNaN)
      assert(!bCount.isNaN)
      if (aCount != 0 && bCount != 0) {
        product += a.getCount(key) * b.getCount(key)
        assert(!product.isNaN, "Product became NaN on key '" + key + "': " + aCount + " * " + bCount)
      }
    }
    assert (!product.isNaN)
    product
  }

  /**
   * toString() an inference path, primarily for debugging.
   * @param node The path to print.
   * @return The string format of the path.
   */
  def recursivePrint(node:Inference):String = {
    if (node.hasImpliedFrom) {
      val incomingType = EdgeType.values.find( _.id == node.getIncomingEdgeType).getOrElse(node.getIncomingEdgeType)
      if (incomingType != 63) {
        s"${node.getFact.getGloss} -[$incomingType]-> ${recursivePrint(node.getImpliedFrom)}"
      } else {
        recursivePrint(node.getImpliedFrom)
      }
    } else {
      s"${node.getFact.getGloss}"
    }
  }

  /** @see Learn#recursivePrint(Inference) */
  def recursivePrint(node:Option[Inference]):String = node match {
    case Some(n) => recursivePrint(n)
    case None => "<no paths>"
  }

  /**
   * An abstract class for computing a given loss over just the single most peaked prediction.
   * @param guesses The inference paths we are guessing
   * @param goldTruth The ground truth for the query associated with this loss.
   */
  class MaxTypeLoss(guesses:Iterable[Inference], goldTruth:TruthValue) {
    val guessPath:Option[Inference] = if (guesses.isEmpty) None else Some(guesses.maxBy( x => math.abs(0.5 - x.getScore) ))
    val features:Array[Double] = {
      val f = if (guessPath.isDefined) featurize(guessPath.get) else new ClassicCounter[String]
      f.incrementCount("resultCount", guesses.size)
      f
    }
    val goldPolarity = goldTruth match {
      case TruthValue.TRUE => 1.0
      case TruthValue.FALSE => -1.0
      case TruthValue.UNKNOWN => 0.0
      case TruthValue.INVALID => throw new IllegalArgumentException("Invalid truth value")
    }
    val goldProbability = goldTruth match {
      case TruthValue.TRUE => 1.0
      case TruthValue.FALSE => 0.0
      case TruthValue.UNKNOWN => 0.5
      case TruthValue.INVALID => throw new IllegalArgumentException("Invalid truth value")
    }

    def prediction(wVector:Array[Double]):Double = {
      assert (wVector.drop(2).forall( _ <= 0.0 ))
      assert (features.forall( _ >= 0.0 ))
      assert (features.forall( !_.isNaN ))
      val p = dotProduct(wVector, features)
      if (p > 0.0) 0.0 else p
    }
    def probability(wVector:Array[Double]):Double = {
      val state:Double = guessPath.fold(CollapsedInferenceState.UNKNOWN)(_.getState) match {
        case CollapsedInferenceState.TRUE => 1.0
        case CollapsedInferenceState.FALSE => -1.0
        case CollapsedInferenceState.UNKNOWN => 0.0
      }
      val logistic = 1.0 / (1.0 + math.exp(-prediction(wVector) * state))
      val prob = 0.5*state + logistic
      assert (!prob.isNaN)
      assert (prob >= 0.0, "Not a probability: " + prob + " from x=" + (prediction(wVector) * state) + " => sigmoid=" + logistic)
      assert (prob <= 1.0, "Not a probability: " + prob + " from x=" + (prediction(wVector) * state) + " => sigmoid=" + logistic)
      prob
    }
  }

  /**
   * A loss function over the squared loss on the most peaked inference. That is, the inference with the furthest
   * search score from 0.5.
   * @param guesses The inference paths we are guessing
   * @param goldTruth The ground truth for the query associated with this loss.
   */
  class SquaredMaxLoss(guesses:Iterable[Inference], goldTruth:TruthValue) extends MaxTypeLoss(guesses,goldTruth)  with SquaredLoss {
    override def gold: Double = goldProbability
    override def guess(wVector: Array[Double]): Double = {
      val prob = probability(wVector)
      prob
    }
    override def guessGradient(wVector: Array[Double]): Array[Double] = {
      val gradient = guessPath.fold(CollapsedInferenceState.UNKNOWN)(_.getState) match {
        case CollapsedInferenceState.TRUE =>
          val sigmoid = 1.0 / (1.0 + math.exp(-prediction(wVector)))
          features.map(x => sigmoid * (1.0 - sigmoid) * x)
        case CollapsedInferenceState.FALSE =>
          val sigmoid = 1.0 / (1.0 + math.exp(prediction(wVector)))
          features.map(x => sigmoid * (1.0 - sigmoid) * x)
        case CollapsedInferenceState.UNKNOWN => wVector.map( _ => 0.0 )
        case _ => throw new RuntimeException("Unknown inference state")
      }
      // Some sanity checks
      // (the gradient is real-valued)
      assert( gradient.forall( x => !x.isNaN && !x.isInfinite ) )
      // (the gradient is pushing weights up if true, and down if false)
      assert ( gradient.forall( x => x >= 0.0 ) || gradient.forall( x => x <= 0.0 ),
        "took a fishy gradient update: " + guess(wVector) + " vs " + goldTruth + "; gradient=" + gradient.mkString(" ")
      )
      // Debug
      if (!gradient.forall ( x => x == 0.0)) {
        Learn.synchronized {
          startTrack("Gradient Update")
          for (entry <- gradient.entrySet().filter(_.getValue != 0.0)) {
            debug(s"${entry.getKey}: ${entry.getValue}")
          }
          endTrack("Gradient Update")
        }
      }
      // Return the gradient
      gradient
    }
  }

  class ZeroOneMaxLoss(guesses:Iterable[Inference], goldTruth:TruthValue) extends MaxTypeLoss(guesses,goldTruth) with ZeroOneLoss {
    override def isCorrect: Boolean = guessPath.fold(CollapsedInferenceState.UNKNOWN)(_.getState) match {
      case CollapsedInferenceState.TRUE => goldTruth == TruthValue.TRUE
      case CollapsedInferenceState.FALSE => goldTruth == TruthValue.FALSE
      case CollapsedInferenceState.UNKNOWN => if (Props.EVALUATE_ALLOWUNK) goldTruth == TruthValue.UNKNOWN else goldTruth == TruthValue.FALSE
    }
  }

  class ZeroOneAllTrueLoss(guesses:Iterable[Iterable[Inference]], goldTruth:TruthValue, inputs:Option[Iterable[Query.Builder]] = None) extends ZeroOneLoss {
    def probability(weights:Array[Double]):Double = {
      inputs match {
        case Some(input:Iterable[Query.Builder]) =>
          guesses.zip(input).foldLeft(1.0){ case (probability:Double, (guess:Iterable[Inference], input:Query.Builder)) =>
            if (input.getForceFalse) { 0.0 }
            else { probability * new ZeroOneMaxLoss(guess, goldTruth).probability(weights) }
          }
        case None =>
          guesses.foldLeft(1.0){ case (probability:Double, guess:Iterable[Inference]) =>
            probability * new ZeroOneMaxLoss(guess, goldTruth).probability(weights)
          }
      }
    }

    lazy val isCorrect: Boolean = {
      inputs match {
        case Some(input:Iterable[Query.Builder]) =>
          guesses.zip(input).foldLeft(true){ case (truth:Boolean, (guess:Iterable[Inference], input:Query.Builder)) =>
            if (input.getForceFalse) {
              truth && (goldTruth == TruthValue.FALSE || (Props.EVALUATE_ALLOWUNK && goldTruth == TruthValue.UNKNOWN))
            } else {
              truth && new ZeroOneMaxLoss(guess, goldTruth).isCorrect
            }
          }
        case None =>
          guesses.foldLeft(true){ case (truth:Boolean, guess:Iterable[Inference]) =>
            truth && new ZeroOneMaxLoss(guess, goldTruth).isCorrect }
      }
    }
    lazy val guessTruthValue:Boolean = (isCorrect && goldTruth == TruthValue.TRUE) || (!isCorrect && goldTruth != TruthValue.TRUE)
  }

  /**
   * A little helper for when we just want p(TRUE) rather than any structured loss
   * @param guesses The inference paths we are guessing
   */
  class ProbabilityOfTruth(guesses:Iterable[Inference], verbose:Boolean=true) extends MaxTypeLoss(guesses, TruthValue.TRUE) {
    def apply(wVector:Array[Double]):Double = {
      for (inference <- guesses) {
        val prob = new MaxTypeLoss(List(inference), TruthValue.TRUE).probability(wVector)
        assert(!prob.isNaN)
        if (verbose) log({
          if (prob >= 0.5) "p(true)=" + prob + ": "
          else "p(true)=" + prob + ": "
        } + recursivePrint(inference))
      }
      // Compute score
      super.probability(wVector)
    }
  }

  /**
   * Learn a model.
   */
  def main(args:Array[String]):Unit = {
    // Initialize Options
    if (args.length == 1) {
      val props:Properties = new Properties
      val config:Config = ConfigFactory.parseFile(new File(args(0))).resolve()
      for ( entry <- config.entrySet() ) {
        props.setProperty(entry.getKey, entry.getValue.unwrapped.toString)
      }
      Execution.fillOptions(classOf[Execution], props)
      Execution.fillOptions(classOf[Props], props)
    } else {
      Execution.fillOptions(classOf[Execution], args)
      Execution.fillOptions(classOf[Props], args)
    }

    // Initialize Data
    def mkDataset(corpus:Props.Corpus):DataStream = {
      corpus match {
        case Props.Corpus.HELD_OUT => HoldOneOut.read("")
        case Props.Corpus.FRACAS => FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter(FraCaS.isSingleAntecedent)
        case Props.Corpus.FRACAS_NATLOG => FraCaS.read(Props.DATA_FRACAS_PATH.getPath).filter(FraCaS.isApplicable)
        case Props.Corpus.AVE_2006 => AVE.read(Props.DATA_AVE_PATH("2006").getPath)
        case Props.Corpus.AVE_2007 => AVE.read(Props.DATA_AVE_PATH("2007").getPath)
        case Props.Corpus.AVE_2008 => AVE.read(Props.DATA_AVE_PATH("2008").getPath)
        case Props.Corpus.MTURK_TRAIN => MTurk.read(Props.DATA_MTURK_TRAIN.getPath)
        case Props.Corpus.MTURK_TEST => MTurk.read(Props.DATA_MTURK_TEST.getPath)
        case _ => throw new IllegalArgumentException("Unknown dataset: " + corpus)
      }
    }
    val trainData:DataStream = Props.LEARN_TRAIN.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar:DataStream, elem:DataStream) => soFar ++ elem }
    val testData:DataStream = Props.LEARN_TEST.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar:DataStream, elem:DataStream) => soFar ++ elem }

    // Initialize Optimization
    val regularizer = OnlineRegularizer.adagrad(Props.LEARN_SGD_NU)
    def modelName(iteration:Int):String = "model." + regularizer.name + "_" + Props.LEARN_SGD_NU.toString.replaceAll("\\.", "_") + "."+(math.max(0, iteration) / 1000) + "k.tab"
    val modelFile:Option[File] =
      if (Props.LEARN_MODEL_START > 0 && Props.LEARN_MODEL_DIR.exists && Props.LEARN_MODEL_DIR.isDirectory) {
        val modelFile = new File(Props.LEARN_MODEL_DIR + File.separator + modelName(Props.LEARN_MODEL_START))
        if (modelFile.exists() && modelFile.canRead) { Some(modelFile) } else { None }
      } else { None }
    if (!Props.LEARN_MODEL_DIR.exists() && !Props.LEARN_MODEL_DIR.mkdirs()) { warn(YELLOW, "Could not create model directory: " + Props.LEARN_MODEL_DIR) }
    val projection = (i:Int, w:Double) => if (i < 2) w else math.min(-1e-4, w)
    val optimizer = modelFile match {
      case Some(file) => OnlineOptimizer.deserialize(file, regularizer, project = projection)
      case None =>
        val optimizer = OnlineOptimizer(NatLog.softNatlogWeights, regularizer)(project = projection)
        optimizer.serializePartial(new File(Props.LEARN_MODEL_DIR + File.separator + modelName(0)))
        optimizer
    }

    // Define evaluation
    def guess(query:Query.Builder, weights:Array[Double]):Future[Iterable[Inference]] = {
      asyncQuery(Messages.Query.newBuilder(query.build())
        .setTimeout(Props.SEARCH_TIMEOUT)
        .setCosts(Learn.weightsToCosts(weights))
        .setSearchType("ucs")
        .setCacheType("bloom").build(), quiet = true)
    }
    def evaluate(print:String=>Unit, prFile:File=new File("/dev/null")):Unit = {
      // Baseline
      val (bGuessed, bCorrect, bShouldHaveGuessed, bAccuracyNumer, bAccuracyDenom)
        = testData.par.foldLeft( (0, 0, 0, 0, 0) ) { case ((g:Int, c:Int, s:Int, an:Int, ad:Int), (queries: Iterable[Query.Builder], gold: TruthValue)) =>
        val guess = !queries.forall( _.getForceFalse )
        ( g + (if (guess) 1 else 0),
          c + (if (guess && gold == TruthValue.TRUE) 1 else 0),
          s + (if (gold == TruthValue.TRUE) 1 else 0),
          an + (if ((guess && gold == TruthValue.TRUE) || (!guess && gold != TruthValue.TRUE)) 1 else 0),
          ad + 1 )
        }
      print( "Error (baseline)  : " + Utils.percent.format(bAccuracyNumer.toDouble / bAccuracyDenom.toDouble))
      print(s"      (baseline) P: ${Utils.percent.format(bCorrect.toDouble / bGuessed.toDouble)} R:${Utils.percent.format(bCorrect.toDouble / bShouldHaveGuessed.toDouble)} F1: ${Utils.percent.format(Utils.f1(bGuessed, bCorrect, bShouldHaveGuessed))}")
      // Evaluate
      val guessed = new AtomicInteger(0)
      val correct = new AtomicInteger(0)
      val shouldHaveGuessed = new AtomicInteger(0)
      val examplesAnnotated = new AtomicInteger(0)
      case class ResultEntry(guess:Boolean, gold:Boolean, prob:Double)
      // Compute futures
      val lossesFuture: Future[Stream[(ZeroOneAllTrueLoss, ResultEntry)]]
        = Future.sequence(testData.map{ case (queries:Iterable[Query.Builder], gold:TruthValue) =>
          val allowedTrue = !queries.forall( _.getForceFalse )
          Future.sequence(queries.map( guess(_, optimizer.weights) )).map { (guesses: Iterable[Iterable[Inference]]) =>
            // vv Enter "search done" Monad
            val loss: ZeroOneAllTrueLoss = new ZeroOneAllTrueLoss(guesses, gold, inputs=Some(queries))
            if (loss.guessTruthValue && allowedTrue) {
              Learn.synchronized { debug(if (loss.isCorrect) GREEN else RED, s"[true]: ${queries.head.getQueryFact.getGloss}: ${recursivePrint(guesses.headOption.flatMap( _.headOption ))}") }
              guessed.incrementAndGet()
            } else if (!loss.isCorrect) {
              debug(RED, s"[false]: ${queries.head.getQueryFact.getGloss}")
            }
            if (loss.isCorrect && loss.guessTruthValue) correct.incrementAndGet()
            if (gold == TruthValue.TRUE) shouldHaveGuessed.incrementAndGet()
            if (examplesAnnotated.incrementAndGet() % 100 == 0) {
              Learn.synchronized{ log(s"[${examplesAnnotated.get()}]  P: ${Utils.percent.format(correct.get.toDouble / guessed.get.toDouble)} R:${Utils.percent.format(correct.get.toDouble / shouldHaveGuessed.get.toDouble)} F1: ${Utils.percent.format(Utils.f1(guessed.get, correct.get, shouldHaveGuessed.get))}") }
            }
            (loss, ResultEntry(loss.guessTruthValue && allowedTrue, gold == TruthValue.TRUE, loss.probability(optimizer.weights)))
            // ^^ Leave "search done" Monad
          }
        })
      val (losses, prCurve) = Await.result(lossesFuture, Duration.Inf).unzip
      // Print error
      val error = optimizer.error(losses)
      print( "Error: " + Utils.percent.format(error))
      print(s"    P: ${Utils.percent.format(correct.get.toDouble / guessed.get.toDouble)} R:${Utils.percent.format(correct.get.toDouble / shouldHaveGuessed.get.toDouble)} F1: ${Utils.percent.format(Utils.f1(guessed.get, correct.get, shouldHaveGuessed.get))}")
      // Save PR curve
      Utils.printToFile(prFile) { out =>
        out.println("#gold\tguess\tprob")
        for (entry <- prCurve.sortBy(_.prob)) { out.println(s"${entry.gold}\t${entry.guess}\t${entry.prob}")}
      }
      // Optimal F1
      val sortedPR = prCurve.sortBy( _.prob )
      print("Optimal F1: " + (0 until sortedPR.length).map{ (falseUntil:Int) =>
        Utils.f1(sortedPR.take(falseUntil).map{ case ResultEntry(guess, gold, prob) => (false, gold) }.toList :::
          sortedPR.drop(falseUntil).map{ case ResultEntry(guess, gold, prob) => (guess, gold) }.toList)
      }.max)
    }

    // Pre-Evaluate Model
    forceTrack("Evaluating (pre-learning)")
//    evaluate(x => log(BOLD,YELLOW, "[Pre-learning] " + x))
    endTrack("Evaluating (pre-learning)")

    // Learn
    startTrack("Learning")
    // (create balanced data stream)
    val trueData  = DataSource.loop(trainData.filter( x => x._2 == TruthValue.TRUE && !x._1.headOption.fold(false)(_.getForceFalse) ))
    val falseData = DataSource.loop(trainData.filter( x => x._2 != TruthValue.TRUE && !x._1.headOption.fold(false)(_.getForceFalse) ))
    def mkIter = trueData.zip(falseData).map{ case (a, b) => Stream(a, b) }.flatten.iterator
    var iter: Iterator[DataSource.Datum] = mkIter
    val iterCounter = new AtomicInteger(0)
    for (index <-  (Props.LEARN_MODEL_START  - (Props.LEARN_MODEL_START % 1000)) to Props.LEARN_ITERATIONS) {
      val (queries, gold) = Learn.synchronized {
        if (!iter.hasNext) { iter = mkIter }
        iter.next()
      }
      for (query: Query.Builder <- queries) {
        for (guessValue: Iterable[Inference] <- guess(query, optimizer.weights)) {
          // vv Enter "search done" Monad
          val loss = new SquaredMaxLoss(guessValue, gold)
          val lossValue = optimizer.update(loss, new ZeroOneMaxLoss(guessValue, gold))
          Learn.synchronized{ debug(s"[$index] loss: ${Utils.df.format(lossValue)}; p(true)=${Utils.df.format(loss.guess(optimizer.weights))}  '${query.getQueryFact.getGloss}'") }
          val iteration :Int = iterCounter.incrementAndGet()
          if (iteration % 10 == 0) {
            Learn.synchronized{ log(BOLD, "[" + iterCounter.get + "] REGRET: " + Utils.df.format(optimizer.averageRegret) + "  ERROR: " + Utils.percent.format(optimizer.averagePerformance)) }
          }
          if (iteration % 1000 == 0 && Props.LEARN_MODEL_DIR.exists()) {
            optimizer.serializePartial(new File(Props.LEARN_MODEL_DIR + File.separator + modelName(iteration)))
          }
          // ^^ Leave "search done" Monad
        }
      }
    }
    endTrack("Learning")

    // Save Model
    if (Props.LEARN_MODEL_DIR.exists()) {
      optimizer.serializePartial(new File(Props.LEARN_MODEL_DIR + File.separator + modelName(Props.LEARN_ITERATIONS)))
    }

    // Evaluate Model
    startTrack("Evaluating")
    evaluate(x => log(BOLD,BLUE, "[Post-learning] " + x), Props.LEARN_PRCURVE)
    endTrack("Evaluating")
  }
}



