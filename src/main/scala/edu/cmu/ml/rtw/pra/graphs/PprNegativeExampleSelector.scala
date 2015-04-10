package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.config.JsonHelper

import edu.cmu.graphchi.ChiVertex
import edu.cmu.graphchi.EdgeDirection
import edu.cmu.graphchi.EmptyType
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.util.IdCount
import edu.cmu.graphchi.walks.DrunkardContext;
import edu.cmu.graphchi.walks.DrunkardDriver;
import edu.cmu.graphchi.walks.DrunkardJob;
import edu.cmu.graphchi.walks.DrunkardMobEngine;
import edu.cmu.graphchi.walks.IntDrunkardContext
import edu.cmu.graphchi.walks.IntDrunkardFactory
import edu.cmu.graphchi.walks.IntWalkArray
import edu.cmu.graphchi.walks.WalkArray
import edu.cmu.graphchi.walks.WalkUpdateFunction
import edu.cmu.graphchi.walks.distributions.IntDrunkardCompanion

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable

import java.util.Random

class PprNegativeExampleSelector(
    params: JValue,
    val graphFile: String,
    val numShards: Int,
    random: Random = new Random) extends WalkUpdateFunction[EmptyType, Integer] {
  implicit val formats = DefaultFormats
  val paramKeys = Seq("reset probability", "walks per source", "iterations",
    "negative to positive ratio")
  JsonHelper.ensureNoExtras(params, "split -> negative instances", paramKeys)

  val resetProbability = JsonHelper.extractWithDefault(params, "reset probability", 0.15)
  val walksPerSource = JsonHelper.extractWithDefault(params, "walks per source", 250)
  val iterations = JsonHelper.extractWithDefault(params, "iterations", 4)
  val negativesPerPositive = JsonHelper.extractWithDefault(params, "negative to positive ratio", 3)

  // This is how many times we should try sampling the right number of negatives for each positive
  // before giving up, in case a (source, target) pair is isolated from the graph, for instance.
  val maxAttempts = negativesPerPositive * 10

  /**
   * Returns a new Dataset that includes the input data and negative instances sampled according to
   * PPR from the positive examples in the input data.
   */
  def selectNegativeExamples(data: Dataset, allowedSources: Set[Int], allowedTargets: Set[Int]): Dataset = {
    val pprValues = computePersonalizedPageRank(data, allowedSources, allowedTargets)
    val negativeExamples = sampleByPrr(data, pprValues)

    val negativeData = new Dataset.Builder()
      .setNegativeSources(negativeExamples.map(x => Integer.valueOf(x._1)).asJava)
      .setNegativeTargets(negativeExamples.map(x => Integer.valueOf(x._2)).asJava)
      .build
    data.merge(negativeData)
  }

  def computePersonalizedPageRank(data: Dataset, allowedSources: Set[Int], allowedTargets: Set[Int]) = {
    val engine = new DrunkardMobEngine[EmptyType, Integer](graphFile, numShards, new IntDrunkardFactory())
    engine.setEdataConverter(new IntConverter());
    val companion = new IntDrunkardCompanion(4, Runtime.getRuntime.maxMemory() / 3) {
      def waitForFinish() {
        while (pendingQueue.size() > 0) Thread.sleep(100)
        while (outstanding.get() > 0) Thread.sleep(100)
      }
      override def getTop(vertexId: Int, nTop: Int): Array[IdCount] = {
        waitForFinish()
        super.getTop(vertexId, nTop)
      }
    }
    val job = engine.addJob("ppr", EdgeDirection.IN_AND_OUT_EDGES, this, companion)
    val translate = engine.getVertexIdTranslate;
    val walkSources = (data.getPositiveSources().asScala ++ data.getPositiveTargets().asScala).toSet
    val translatedSources = walkSources.map(x => translate.forward(x)).toList.sorted
    val javaTranslatedSources = new java.util.ArrayList[Integer]
    for (s <- translatedSources) {
      javaTranslatedSources.add(s)
    }
    job.configureWalkSources(javaTranslatedSources, walksPerSource)

    engine.run(iterations)
    val sources = data.getPositiveSources.asScala.toSet
    val targets = data.getPositiveTargets.asScala.toSet
    val pprValues = translatedSources.map(s => {
      val originalSource = translate.backward(s).toInt
      val allowed = if (sources.contains(originalSource)) allowedSources else allowedTargets
      val top: Array[IdCount] = try {
        companion.getTop(s, 100)
      } catch {
        case e: ArrayIndexOutOfBoundsException => Array[IdCount]()
      }
      val counts = top.map(idcount =>
          (translate.backward(idcount.id), idcount.count)).toMap
      if (allowed != null) {
        (originalSource, counts.filter(n => allowed.contains(n._1)))
      } else {
        (originalSource, counts)
      }
    }).toMap.withDefaultValue(Map())
    companion.close()
    pprValues
  }

  def sampleByPrr(data: Dataset, pprValues: Map[Int, Map[Int, Int]]): Seq[(Int, Int)] = {
    val positive_instances = data.getPositiveInstances.asScala.map(x => (x.getLeft.toInt, x.getRight.toInt))
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

  // This tells GraphChi that there are some node pairs we don't want to compute PPR for.  We could
  // use this to restrict the statistics collected to only nodes whose type is the same as the
  // source (or target) node.  The trouble with this is that we'd have to return a list of _every
  // node in the graph_ here minus the ones of a particular type.  That's huge, and it totally
  // breaks the assumptions made in GraphChi's implementation of "avoidance distributions".  So
  // instead we just handle the type restrictions in post-processing, after we've done our walks.
  override def getNotTrackedVertices(vertex: ChiVertex[EmptyType, Integer]): Array[Int] = {
    new Array[Int](0)
  }

  override def processWalksAtVertex(
      walkArray: WalkArray,
      vertex: ChiVertex[EmptyType, Integer],
      context_ : DrunkardContext,
      random: Random) {
    val walks = walkArray.asInstanceOf[IntWalkArray].getArray()
    val context = context_.asInstanceOf[IntDrunkardContext]
    val numWalks = walks.length
    val numEdges = vertex.numOutEdges + vertex.numInEdges
    val numOutEdges = vertex.numOutEdges

    // Advance each walk through a random edge (if any)
    if (numEdges > 0) {
      for(walk <- walks) {
        // Reset?
        if (random.nextDouble < resetProbability) {
          context.resetWalk(walk, false)
        } else {
          val edgeNum = random.nextInt(numEdges);
          val nextHop = if (edgeNum < numOutEdges) vertex.getOutEdgeId(edgeNum)
            else vertex.inEdge(edgeNum - numOutEdges).getVertexId

          // Optimization to tell the manager that walks that have just been started
          // need not to be tracked.
          context.forwardWalkTo(walk, nextHop, true)
        }
      }
    } else {
      // Reset all walks -- no where to go from here
      for(walk <- walks) {
        context.resetWalk(walk, false)
      }
    }
  }
}
