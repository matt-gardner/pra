package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.JsonHelper

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

import scala.util.Random

trait PprComputer {
  // The return value here is a map from node ids to (node id, score) pairs.  We will use all
  // sources and targets in the dataset.  For each source, this will find _other sources_ (as
  // defined by allowedSources) that are close in terms of PPR, and the same for targets.  This
  // _will not_ find, e.g., targets that are close in PPR score to the source node.  If you want to
  // do that, pass your set of allowable targets in as allowedSources, and just give a dummy value
  // for all of the targets in the dataset Instances.
  //
  // The score is an Int, because I'm computing PPR values with actual random walks, and it's
  // simpler to not normalize them here.  Note that the node ids that are keys in the map are
  // _both_ the sources and the targets in the given data set, with the keys in the nested map
  // filtered by allowedTargets and allowedSources, respectively, if they are not null.
  //
  // Note also that this whole concept assumes we have a single graph, not one graph for each
  // instance in the dataset.  So, data.instances(x).graph should equal the graph passed to
  // PprComputerCreator for all x, or bad things might happen.  TODO(matt): There's no reason that
  // we should require this, actually.  I could just move the graph to be an argument here, and
  // things would still work.  It would potentially make it inefficient with GraphChi, but I don't
  // use that anymore.
  def computePersonalizedPageRank(
    sources: Set[Int],
    targets: Set[Int],
    allowedSources: Option[Set[Int]],
    allowedTargets: Option[Set[Int]]
  ): Map[Int, Map[Int, Int]]
}

object PprComputer {
  def create(params: JValue, graph: Graph, outputter: Outputter, random: Random): PprComputer = {
    val pprComputerType = JsonHelper.extractWithDefault(params, "type", "InMemoryPprComputer")
    pprComputerType match {
      case "InMemoryPprComputer" => new InMemoryPprComputer(params, graph, outputter, random)
      case "GraphChiPprComputer" => new GraphChiPprComputer(
        params, graph.asInstanceOf[GraphOnDisk], random)
      case "Fake" => new PprComputer {
        override def computePersonalizedPageRank(
          sources: Set[Int],
          targets: Set[Int],
          allowedSources: Option[Set[Int]],
          allowedTargets: Option[Set[Int]]
        ) = {
          Map[Int, Map[Int, Int]]()
        }
      }
      case other => throw new IllegalStateException("Unrecognized PprComputer")
    }
  }
}

class InMemoryPprComputer(
  params: JValue,
  graph: Graph,
  outputter: Outputter,
  random: Random = new Random
) extends PprComputer {
  implicit val formats = DefaultFormats
  val paramKeys = Seq("type", "reset probability", "walks per source", "num steps", "log level")
  JsonHelper.ensureNoExtras(params, "split -> negative instances -> ppr computer", paramKeys)

  val resetProbability = JsonHelper.extractWithDefault(params, "reset probability", 0.15)
  val walksPerSource = JsonHelper.extractWithDefault(params, "walks per source", 250)
  val numSteps = JsonHelper.extractWithDefault(params, "num steps", 4)
  val logLevel = JsonHelper.extractWithDefault(params, "log level", 3)

  override def computePersonalizedPageRank(
    sources: Set[Int],
    targets: Set[Int],
    allowedSources: Option[Set[Int]],
    allowedTargets: Option[Set[Int]]
  ) = {
    outputter.outputAtLevel(s"Computing PPR with ${sources.size} sources and ${targets.size} targets", logLevel)
    (sources.par.map(source => (source, pprFromNode(source, allowedSources))) ++
      targets.par.map(target => (target, pprFromNode(target, allowedTargets)))).seq.toMap
  }

  // This method is getting called multiple times in parallel, so I'm just going to do everything
  // sequentially inside here.
  def pprFromNode(node: Int, allowed: Option[Set[Int]]): Map[Int, Int] = {
    val targetCounts = new mutable.HashMap[Int, Int].withDefaultValue(0)
    if (allowed.size == 0) return targetCounts.toMap

    for (walkNum <- 1 to walksPerSource) {
      var currentNode = node
      for (stepNum <- 1 to numSteps) {
        // Step to a connected node.
        val nextNodes = graph.getNode(currentNode).getAllConnectedNodes().toSeq
        val index = random.nextInt(nextNodes.size)
        currentNode = nextNodes(index)
        // Check to see if we're at a node we want to keep track of.
        allowed match {
          case None => {
            if (currentNode != node) {
              targetCounts.update(currentNode, targetCounts(currentNode) + 1)
            }
          }
          case Some(a) => {
            if (a.contains(currentNode) && currentNode != node) {
              targetCounts.update(currentNode, targetCounts(currentNode) + 1)
            }
          }
        }
        // And reset with probability resetProbability.
        if (random.nextDouble() < resetProbability) {
          currentNode = node
        }
      }
    }
    targetCounts.toMap
  }
}


class GraphChiPprComputer(
    params: JValue,
    graph: GraphOnDisk,
    random: Random = new Random) extends WalkUpdateFunction[EmptyType, Integer] with PprComputer {
  implicit val formats = DefaultFormats
  val paramKeys = Seq("type", "reset probability", "walks per source", "iterations")
  JsonHelper.ensureNoExtras(params, "split -> negative instances -> ppr computer", paramKeys)

  val resetProbability = JsonHelper.extractWithDefault(params, "reset probability", 0.15)
  val walksPerSource = JsonHelper.extractWithDefault(params, "walks per source", 250)
  val iterations = JsonHelper.extractWithDefault(params, "iterations", 4)
  val graphFile = graph.graphFile
  val numShards = graph.numShards

  def computePersonalizedPageRank(
    sources: Set[Int],
    targets: Set[Int],
    allowedSources: Option[Set[Int]],
    allowedTargets: Option[Set[Int]]
  ) = {
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
    val walkSources = sources ++ targets
    val translatedSources = walkSources.map(x => translate.forward(x)).toList.sorted
    val javaTranslatedSources = new java.util.ArrayList[Integer]
    for (s <- translatedSources) {
      javaTranslatedSources.add(s)
    }
    job.configureWalkSources(javaTranslatedSources, walksPerSource)

    engine.run(iterations)
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
      allowed match {
        case None => (originalSource, counts)
        case Some(allowed) => (originalSource, counts.filter(n => allowed.contains(n._1)))
      }
    }).toMap.withDefaultValue(Map())
    companion.close()
    pprValues
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
      random: java.util.Random) {
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
