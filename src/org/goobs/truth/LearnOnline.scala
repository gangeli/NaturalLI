package org.goobs.truth

import edu.stanford.nlp.util.logging.Redwood.Util._
import org.goobs.truth.Messages._
import org.goobs.truth.DataSource.DataStream
import java.io.File
import org.goobs.truth.TruthValue.TruthValue

import scala.language.implicitConversions
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Future, Await}
import scala.collection.immutable.IndexedSeq
import scala.collection.JavaConversions._


object LearnOnline extends Client {
  import Implicits.flattenWeights


  /**
   * A loss function over the squared loss on the most peaked inference. That is, the inference with the furthest
   * search score from 0.5.
   * @param guesses The inference paths we are guessing
   * @param goldTruth The ground truth for the query associated with this loss.
   */
  class OptimisticSquaredLoss(guesses:Iterable[Inference], goldTruth:TruthValue) extends SquaredLoss {
    override def gold: Double = goldTruth match {
        case TruthValue.TRUE => 1.0
        case TruthValue.FALSE => 0.0
        case TruthValue.UNKNOWN => 0.5
        case _ => throw new IllegalArgumentException("Invalid truth value: " + goldTruth)
      }

    override def guess(wVector: Array[Double]): Double = probability(guesses, wVector)

    override def guessGradient(wVector: Array[Double]): Array[Double] = {
      val gradient = probabilitySubgradient(guesses, wVector)
      if (!gradient.forall ( x => math.abs(x) < 1e-3)) {
        import Implicits.inflateWeights
        LearnOnline.synchronized {
          startTrack("Gradient Update")
          for (entry <- gradient.entrySet().filter(_.getValue != 0.0)) {
            debug(s"${entry.getKey}: ${entry.getValue}")
          }
          endTrack("Gradient Update")
        }
      }
      gradient
    }
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
    import scala.concurrent.ExecutionContext.Implicits.global
    Props.exec(Implicits.fn2execInput1(() => {
      val trainData:DataStream = Props.LEARN_TRAIN.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar:DataStream, elem:DataStream) => soFar ++ elem }
      val testData:DataStream = Props.LEARN_TEST.map(mkDataset).foldLeft(Stream.Empty.asInstanceOf[DataStream]) { case (soFar:DataStream, elem:DataStream) => soFar ++ elem }

      // Initialize Optimization
      val regularizer = OnlineRegularizer.adagrad(Props.LEARN_ONLINE_SGD_NU)
      def modelName(iteration:Int):String = "model." + regularizer.name + "_" + Props.LEARN_ONLINE_SGD_NU.toString.replaceAll("\\.", "_") + "."+(math.max(0, iteration) / 1000) + "k.tab"
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
      evaluate(testData, optimizer.weights, x => LearnOnline.synchronized { log(BOLD,YELLOW, "[Pre-learning] " + x) })
      endTrack("Evaluating (pre-learning)")

      // Learn
      forceTrack("Learning")
      // (create balanced data stream)
      val trueData  = DataSource.loop(trainData.filter( x => x._2 == TruthValue.TRUE && !x._1.headOption.fold(false)(_.getForceFalse) ))
      val falseData = DataSource.loop(trainData.filter( x => x._2 != TruthValue.TRUE && !x._1.headOption.fold(false)(_.getForceFalse) ))
      def mkIter = trueData.zip(falseData).map{ case (a, b) => Stream(a, b) }.flatten.iterator
      var iter: Iterator[DataSource.Datum] = mkIter
      val iterCounter = new AtomicInteger(0)
      val futures: IndexedSeq[Iterable[Future[Any]]] = for (index <-  (Props.LEARN_MODEL_START  - (Props.LEARN_MODEL_START % 1000)) to Props.LEARN_ONLINE_ITERATIONS) yield {
        val (queries, gold) = LearnOnline.synchronized {
          if (!iter.hasNext) { iter = mkIter }
          iter.next()
        }
        for (query: Query.Builder <- queries) yield {
          for (guessValue: Iterable[Inference] <- guess(query, optimizer.weights)) yield {
            // vv Enter "search done" Monad
            val loss = new OptimisticSquaredLoss(guessValue, gold)
            val lossValue = optimizer.update(loss, new ZeroOneMaxLoss(guessValue, gold, optimizer.weights))
            LearnOnline.synchronized{ debug(s"[$index] loss: ${Utils.df.format(lossValue)}; p(true)=${Utils.df.format(loss.guess(optimizer.weights))}  '${query.getQueryFact.getGloss}'") }
            val iteration :Int = iterCounter.incrementAndGet()
            if (iteration % 10 == 0) {
              LearnOnline.synchronized{ log(BOLD, "[" + iterCounter.get + "] REGRET: " + Utils.df.format(optimizer.averageRegret) + "  ERROR: " + Utils.percent.format(optimizer.averagePerformance)) }
            }
            if (iteration % 1000 == 0 && Props.LEARN_MODEL_DIR.exists()) {
              optimizer.serializePartial(new File(Props.LEARN_MODEL_DIR + File.separator + modelName(iteration)))
            }
            // ^^ Leave "search done" Monad
          }
        }
      }
      val learningDone: Future[IndexedSeq[Any]] = Future.sequence(futures.flatten)
      Await.result(learningDone, scala.concurrent.duration.Duration.Inf)
      endTrack("Learning")

      // Save Model
      if (Props.LEARN_MODEL_DIR.exists()) {
        optimizer.serializePartial(new File(Props.LEARN_MODEL_DIR + File.separator + modelName(Props.LEARN_ONLINE_ITERATIONS)))
      }

      // Evaluate Model
      startTrack("Evaluating")
      evaluate(testData, optimizer.weights, x => LearnOnline.synchronized { log(BOLD,BLUE, "[Post-learning] " + x) }, Props.LEARN_PRCURVE)
      endTrack("Evaluating")
    }),args)
  }
}



