package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.config.JsonHelper

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable

import java.util.Random

class PprNegativeExampleSelector(
    params: JValue,
    val graph: Graph,
    random: Random = new Random) {
  implicit val formats = DefaultFormats
  val paramKeys = Seq("ppr computer", "negative to positive ratio")
  JsonHelper.ensureNoExtras(params, "split -> negative instances", paramKeys)

  val negativesPerPositive = JsonHelper.extractWithDefault(params, "negative to positive ratio", 3)
  val pprComputer = PprComputerCreator.create(params \ "ppr computer", graph, random)

  // This is how many times we should try sampling the right number of negatives for each positive
  // before giving up, in case a (source, target) pair is isolated from the graph, for instance.
  val maxAttempts = negativesPerPositive * 10

  /**
   * Returns a new Dataset that includes the input data and negative instances sampled according to
   * PPR from the positive examples in the input data.
   */
  def selectNegativeExamples(data: Dataset, allowedSources: Set[Int], allowedTargets: Set[Int]): Dataset = {
    println("Selecting negative examples by PPR score")
    val graph = data.instances(0).graph
    val pprValues = pprComputer.computePersonalizedPageRank(data, allowedSources, allowedTargets)
    val negativeExamples = sampleByPrr(data, pprValues)

    val negativeData = new Dataset(negativeExamples.map(x => new Instance(x._1, x._2, false, graph)))
    data.merge(negativeData)
  }

  def sampleByPrr(data: Dataset, pprValues: Map[Int, Map[Int, Int]]): Seq[(Int, Int)] = {
    val positive_instances = data.getPositiveInstances.map(instance => (instance.source, instance.target))
    // The amount of weight in excess of 1 here goes to the original source or target.
    val base_weight = 1.25
    positive_instances.par.flatMap(instance => {
      val source_weights = pprValues(instance._1).toArray
      val total_source_weight = source_weights.map(_._2).sum
      val target_weights = pprValues(instance._2).toArray
      val total_target_weight = target_weights.map(_._2).sum
      val negative_instances = new mutable.HashSet[(Int, Int)]
      var attempts = 0
      while (negative_instances.size < negativesPerPositive && attempts < maxAttempts) {
        attempts += 1
        val new_source = weightedSample(source_weights, base_weight * total_source_weight, instance._1)
        val new_target = weightedSample(target_weights, base_weight * total_target_weight, instance._2)
        val new_pair = (new_source, new_target)
        if (!positive_instances.contains(new_pair)) {
          negative_instances += new_pair
        }
      }
      negative_instances.toSet
    }).seq.toSet.toSeq
  }

  // The default value here is because total_weight can be more than weight_list.map(_._2).sum.  If
  // we sample a number higher than that sum, we return the default.  This is a bit of a hackish
  // way of adding one item to the list without needing to reconstruct a bunch of objects.
  def weightedSample(weight_list: Array[(Int, Int)], total_weight: Double, default: Int): Int = {
    var value = random.nextDouble * total_weight
    var index = -1
    while (value >= 0 && index < weight_list.size - 1) {
      index += 1
      value -= weight_list(index)._2
    }
    if (value >= 0)
      default
    else {
      weight_list(index)._1
    }
  }
}
