package org.goobs.truth

import edu.stanford.nlp.stats.{Counters, ClassicCounter, Counter}
import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.EdgeType.EdgeType
import org.goobs.truth.Messages._
import org.goobs.truth.DataSource.DataStream
import edu.stanford.nlp.util.Execution
import java.io.{PrintWriter, File}
import java.util.Properties
import com.typesafe.config.{Config, ConfigFactory}
import scala.collection.JavaConversions._
import org.goobs.truth.TruthValue.TruthValue

import scala.language.implicitConversions
import java.util.concurrent.atomic.AtomicInteger
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration


object Learn extends Client {
  type WeightVector = Counter[String]
  import Implicits.{flattenWeights}


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

    import Evaluate._
    Costs.newBuilder()
      .setUnlexicalizedMonotoneUp(unlexicalizedWeights(monoUp_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneDown(unlexicalizedWeights(monoDown_stateTrue, monoUp_stateFalse))
      .setUnlexicalizedMonotoneFlat(unlexicalizedWeights(monoFlat_stateTrue, monoFlat_stateFalse))
      .setUnlexicalizedMonotoneAny(unlexicalizedWeights(monoAny_stateTrue, monoAny_stateFalse))
      .setBias(weights.getCount("bias").toFloat)
      .build()
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
   * A loss function over the squared loss on the most peaked inference. That is, the inference with the furthest
   * search score from 0.5.
   * @param guesses The inference paths we are guessing
   * @param goldTruth The ground truth for the query associated with this loss.
   */
  class OptimisticSquaredLoss(guesses:Iterable[Inference], goldTruth:TruthValue) extends SquaredLoss {
      // Debug
//      if (!gradient.forall ( x => math.abs(x) < 1e-3)) {
//        Learn.synchronized {
//          startTrack("Gradient Update")
//          for (entry <- gradient.entrySet().filter(_.getValue != 0.0)) {
//            debug(s"${entry.getKey}: ${entry.getValue}")
//          }
//          endTrack("Gradient Update")
//        }
//      }
    override def gold: Double = goldTruth match {
        case TruthValue.TRUE => 1.0
        case TruthValue.FALSE => 0.0
        case TruthValue.UNKNOWN => 0.5
        case _ => throw new IllegalArgumentException("Invalid truth value: " + goldTruth)
      }

    override def guess(wVector: Array[Double]): Double = probability(guesses, wVector)

    override def guessGradient(wVector: Array[Double]): Array[Double] = probabilitySubgradient(guesses, wVector)
  }

  class ZeroOneMaxLoss(guess:Iterable[Inference], goldTruth:TruthValue, wVector:Array[Double]) extends ZeroOneLoss {
    override def isCorrect: Boolean = goldTruth match {
      case TruthValue.TRUE => probability(guess, wVector) > 0.5
      case TruthValue.FALSE => probability(guess, wVector) < 0.5
      case TruthValue.UNKNOWN => probability(guess, wVector) == 0.5
      case _ => throw new IllegalArgumentException("Invalid truth value: " + goldTruth)
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
      Execution.fillOptions(classOf[Props], props)
      StanfordRedwoodConfiguration.apply(props)
    } else {
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

    // Pre-Evaluate Model
    forceTrack("Evaluating (pre-learning)")
    evaluate(testData, optimizer.weights, x => Learn.synchronized { log(BOLD,YELLOW, "[Pre-learning] " + x) })
    endTrack("Evaluating (pre-learning)")

    // Learn
    forceTrack("Learning")
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
          val loss = new OptimisticSquaredLoss(guessValue, gold)
          val lossValue = optimizer.update(loss, new ZeroOneMaxLoss(guessValue, gold, optimizer.weights))
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
    evaluate(testData, optimizer.weights, x => Learn.synchronized { log(BOLD,BLUE, "[Post-learning] " + x) }, Props.LEARN_PRCURVE)
    endTrack("Evaluating")
  }
}



