package kr.ac.kaist.ir.deep.trainer

import kr.ac.kaist.ir.deep.function._
import kr.ac.kaist.ir.deep.network.{AutoEncoder, Network}

import scala.annotation.tailrec

/**
 * Trainer : Stochastic-Style
 * @param net to be trained
 * @param algorithm to be applied
 * @param error to compute loss
 * @param corrupt to corrupt input
 * @param param of training criteria
 * @param stops of stopping criteria
 */
class BasicTrainer(private val net: Network,
                   private val algorithm: WeightUpdater,
                   private val error: Objective = SquaredErr,
                   private val corrupt: Corruption = NoCorruption,
                   private val param: TrainingCriteria = TrainingCriteria(),
                   protected override val stops: StoppingCriteria = StoppingCriteria(),
                   private val debug: Boolean = false)
  extends Trainer {
  /** Training Set */
  protected var trainingSet: Int ⇒ Seq[(ScalarMatrix, ScalarMatrix)] = null
  /** Validation Set */
  protected var testSet: Int ⇒ Seq[(ScalarMatrix, ScalarMatrix)] = null
  /** Best Parameter History */
  protected var bestParam: Seq[ScalarMatrix] = null
  protected var bestIter: Int = 0

  /**
   * Train given sequence, and validate with another sequence.
   *
   * @param set to be used for training (Randomized Sequence Generator)
   * @param validation to be used for validation (Sequence Generator)
   * @return Training error (loss)
   */
  override def train(set: Int ⇒ Seq[(ScalarMatrix, ScalarMatrix)], validation: Int ⇒ Seq[(ScalarMatrix, ScalarMatrix)]) = {
    trainingSet = set
    testSet = validation

    saveParams()
    val err = trainBatch()
    restoreParams()
    printValidation()

    err
  }

  /**
   * Train autoencoder with given sequence.
   * @param set to be used for input & reconstruction. (Randomized Sequence Generator)
   * @return Training error (loss)
   */
  override def trainAutoencoder(set: Int ⇒ Seq[ScalarMatrix]): Scalar = {
    trainingSet = set andThen { seq ⇒ seq map { item ⇒ item → item}}
    testSet = trainingSet

    saveParams()
    val err = trainBatch(isAutoEncoder = true)
    restoreParams()
    printValidation(isAutoEncoder = true)

    err
  }

  /**
   * Store best parameters
   */
  protected final def saveParams() = {
    bestParam = net.W map {
      _.copy
    }
  }

  /**
   * Restore best parameters
   */
  protected final def restoreParams() = {
    bestParam.indices.par foreach {
      id ⇒ net.W(id) := bestParam(id)
    }
  }

  /**
   * Calculate validation error
   * @param isAutoEncoder true if it is autoencoder training
   * @return validation error
   */
  protected def validationError(isAutoEncoder: Boolean = false) = {
    val t = testSet(param.validationSize)
    t.foldLeft(0.0) { (err, item) ⇒ {
      val in = item._1
      val out = if (isAutoEncoder) in >>: net else net(in)
      if (debug) {
        println(s"IN ${in.mkString} : EXP ${item._2.mkString} = OUT : ${out.mkString}")
      }
      err + error(item._2, out)
    }
    } / t.size
  }

  /**
   * Print validation result
   * @param isAutoEncoder true if it is autoencoder training
   */
  protected def printValidation(isAutoEncoder: Boolean = false) = {
    println(s"BEST ITERATION $bestIter : W = ${net.W map (_.mkString) mkString " | "}")

    val t = testSet(param.validationSize)
    t.par foreach {
      item ⇒ {
        val in = item._1
        val out = if (isAutoEncoder) net.asInstanceOf[AutoEncoder].reconstruct(in) else net(in)
        println(s"IN ${in.mkString} : EXP ${item._2.mkString} = OUT : ${out.mkString}")
      }
    }
  }


  /**
   * Tail Recursive : Train each batch
   * @param iter indicates current iteration
   * @param prevloss indicates previous loss
   * @param patience indicates current patience
   * @param isAutoEncoder is a flag for autoencoder.
   * @return Total Loss when train is finished
   */
  @tailrec
  protected final def trainBatch(iter: Int = 0,
                                 prevloss: Double = Double.MaxValue,
                                 patience: Int = stops.patience,
                                 isAutoEncoder: Boolean = false): Scalar = {
    trainingSet(param.miniBatch) foreach {
      pair ⇒ {
        val out = corrupt(pair._1) >>: net
        val err: ScalarMatrix = error.derivative(pair._2, out) / param.miniBatch.toDouble
        net ! err
      }
    }
    algorithm(net.dW, net.W)

    var nPatience = patience

    val nLoss = if ((iter + 1) % stops.validationFreq == 0) {
      if (debug) {
        println(s"ITERATION $iter : W = ${net.W map (_.mkString) mkString " | "}")
      }
      val train = validationError(isAutoEncoder)
      val weight = algorithm loss net.W
      if (train + weight < prevloss * stops.improveThreshold) {
        nPatience = Math.max(patience, iter * stops.patienceStep)
        bestIter = iter
        saveParams()
        println(f"Iteration $iter%6d, Validation = $train%.5f, WeightLoss = $weight%.5f")
        train + weight
      } else {
        prevloss
      }
    } else {
      prevloss
    }

    if (iter < param.miniBatch * stops.maxIter && nPatience > iter && nLoss > stops.lossThreshold) {
      trainBatch(iter + 1, nLoss, nPatience, isAutoEncoder)
    } else {
      println(f"Finished $iter%6d, Error = $nLoss%.5f")
      nLoss
    }
  }
}
