package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.JsonHelper

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable

import scala.util.Random

class PprNegativeExampleSelector(
    params: JValue,
    val graph: Graph,
    outputter: Outputter,
    random: Random = new Random) {
  implicit val formats = DefaultFormats
  val paramKeys = Seq("ppr computer", "negative to positive ratio", "max potential predictions")
  JsonHelper.ensureNoExtras(params, "split -> negative instances", paramKeys)

  val negativesPerPositive = JsonHelper.extractWithDefault(params, "negative to positive ratio", 3)
  val maxPotentialPredictions = JsonHelper.extractWithDefault(params, "max potential predictions", 1000)
  val pprComputer = PprComputer.create(params \ "ppr computer", graph, outputter, random)

  // This is how many times we should try sampling the right number of negatives for each positive
  // before giving up, in case a (source, target) pair is isolated from the graph, for instance.
  val maxAttempts = negativesPerPositive * 10

  /**
   * Returns a collection of negative instances sampled according to PPR from the positive examples
   * in the input data.  The does _NOT_ merge the newly created negative instances with the
   * original dataset.  The caller must do that, if they wish.
   */
  def selectNegativeExamples(
    data: Dataset[NodePairInstance],
    otherPositiveInstances: Seq[NodePairInstance],
    allowedSources: Option[Set[Int]],
    allowedTargets: Option[Set[Int]]
  ): Dataset[NodePairInstance] = {
    outputter.info(s"Selecting negative examples by PPR score (there are ${data.instances.size} positive instances)")

    val start = compat.Platform.currentTime
    outputter.info("Computing PPR scores...")
    val sources = data.instances.map(_.source).toSet
    val targets = data.instances.map(_.target).toSet
    val pprValues = pprComputer.computePersonalizedPageRank(sources, targets, allowedSources, allowedTargets)
    val end = compat.Platform.currentTime
    val seconds = (end - start) / 1000.0
    outputter.info(s"  took ${seconds} seconds")
    val negativeExamples = sampleByPrr(data, otherPositiveInstances, pprValues)

    val negativeData = new Dataset[NodePairInstance](negativeExamples.map(x =>
        new NodePairInstance(x._1, x._2, false, graph)))
    negativeData
  }

  /**
   * This one is similar to selectNegativeExamples, but instead of looking specifically for
   * _training_ examples that are close to the given positive examples, we look across the whole
   * domain and range of a relation to find things to score.  The point of this is to actually
   * perform KB completion, instead of just training a model or doing cross validation.  So this
   * method is used to generate possible predictions for NELL's ongoing run, for instance.
   */
  def findPotentialPredictions(
    domain: Set[Int],
    range: Set[Int],
    knownPositives: Dataset[NodePairInstance]
  ): Dataset[NodePairInstance] = {
    outputter.info("Finding potential predictions to add to the KB")
    val sourcesToUse = random.shuffle(domain).take(maxPotentialPredictions)
    val data = new Dataset[NodePairInstance](sourcesToUse.map(entity =>
        new NodePairInstance(entity, -1, true, graph)).toSeq)
    outputter.info(s"There are ${data.instances.size} potential sources, and ${range.size} potential targets")

    val start = compat.Platform.currentTime
    // By using computePersonalizedPageRank this way, we will get a map from entities in the domain
    // to entities in the range, ranked by PPR score.  We'll filter the known positives out of this
    // and create a set of potential predictions.
    outputter.info("Computing PPR scores for the sources...")
    val sources = data.instances.map(_.source).toSet
    val targets = data.instances.map(_.target).toSet
    val pprValues = pprComputer.computePersonalizedPageRank(sources, targets, Some(range), Some(Set[Int]()))
    val end = compat.Platform.currentTime
    val seconds = (end - start) / 1000.0
    outputter.info(s"  took ${seconds} seconds")
    val potentialPredictions = pickPredictionsByPpr(pprValues, knownPositives)

    new Dataset[NodePairInstance](potentialPredictions.map(x =>
        new NodePairInstance(x._1, x._2, false, graph)))
  }

  /**
   * Like the above, but just for one source node at a time.
   */
  def findPotentialPredictions(
    source: Int,
    range: Set[Int],
    knownPositives: Dataset[NodePairInstance]
  ): Set[Int] = {
    outputter.debug("Finding potential predictions to add to the KB (for a single source)")
    val data = new Dataset[NodePairInstance](Seq(new NodePairInstance(source, -1, true, graph)))

    val pprValues = pprComputer.computePersonalizedPageRank(Set(source), Set(), Some(range), Some(Set[Int]()))
    pprValues(source).toSeq.sortBy(-_._2).take(negativesPerPositive).map(_._1).toSet
  }

  def sampleByPrr(
    data: Dataset[NodePairInstance],
    otherPositiveInstances: Seq[NodePairInstance],
    pprValues: Map[Int, Map[Int, Int]]
  ): Seq[(Int, Int)] = {
    val dataPositives = data.getPositiveInstances.map(instance => (instance.source, instance.target))
    val positiveInstances = dataPositives ++ otherPositiveInstances.map(i => (i.source, i.target))
    // The amount of weight in excess of 1 here goes to the original source or target.
    val baseWeight = 1.25
    dataPositives.par.flatMap(instance => {
      val sourceWeights = pprValues(instance._1).toArray
      val totalSourceWeight = sourceWeights.map(_._2).sum
      val targetWeights = pprValues(instance._2).toArray
      val totalTargetWeight = targetWeights.map(_._2).sum
      val negativeInstances = new mutable.HashSet[(Int, Int)]
      var attempts = 0
      while (negativeInstances.size < negativesPerPositive && attempts < maxAttempts) {
        attempts += 1
        val new_source = weightedSample(sourceWeights, baseWeight * totalSourceWeight, instance._1)
        val new_target = weightedSample(targetWeights, baseWeight * totalTargetWeight, instance._2)
        val new_pair = (new_source, new_target)
        if (!positiveInstances.contains(new_pair)) {
          negativeInstances += new_pair
        }
      }
      negativeInstances.toSet
    }).seq.toSet.toSeq
  }

  def pickPredictionsByPpr(
    pprValues: Map[Int, Map[Int, Int]],
    knownPositives: Dataset[NodePairInstance]
  ): Seq[(Int, Int)] = {
    // We'll use the negative to positive ratio to set how many targets we'll sample per entity in
    // the domain, then cap it at maxPotentialPredictions.
    val knownPositiveSet = knownPositives.instances.map(
      instance => (instance.source, instance.target)).toSet
    val potentialPredictions = pprValues.par.flatMap(entry => {
      val source = entry._1
      val sortedTargets = entry._2.toSeq.sortBy(-_._2)
      val selectedTargets = new mutable.HashSet[(Int, Int)]
      var index = 0
      // Instead of sampling by PPR, just pick the targets with the highest PPR value that aren't
      // already known positives.
      while (selectedTargets.size < negativesPerPositive && index < sortedTargets.size) {
        val target = sortedTargets(index)._1
        val pair = (source, target)
        if (target != -1 && !knownPositiveSet.contains(pair)) {
          selectedTargets += pair
        }
        index += 1
      }
      selectedTargets.toSet
    }).seq.toSet.toSeq
    random.shuffle(potentialPredictions).take(maxPotentialPredictions)
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
