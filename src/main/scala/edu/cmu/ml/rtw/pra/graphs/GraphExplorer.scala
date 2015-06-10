package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.RandomWalkPathFinder
import edu.cmu.ml.rtw.pra.features.PathType
import edu.cmu.ml.rtw.pra.features.PathTypePolicy
import edu.cmu.ml.rtw.pra.features.SingleEdgeExcluder
import edu.cmu.ml.rtw.users.matt.util.Pair

import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.native.JsonMethods._

/**
 * This is similar to a FeatureGenerator, in that it does the same thing as the first step of PRA,
 * but it does not actually produce a feature matrix.  The idea here is just to see what
 * connections there are between a set of nodes in a graph, and that's it.
 */
class GraphExplorer(params: JValue, config: PraConfig) {
  val paramKeys = Seq("walks per source", "path finding iterations")
  JsonHelper.ensureNoExtras(params, "pra parameters -> explore", paramKeys)

  val walksPerSource = JsonHelper.extractWithDefault(params, "walks per source", 100)
  val numIters = JsonHelper.extractWithDefault(params, "path finding iterations", 3)

  def findConnectingPaths(data: Dataset): Map[Instance, Map[PathType, Int]] = {
    println("Finding connecting paths")

    val pathTypeFactory = new BasicPathTypeFactory()

    val finder = new RandomWalkPathFinder(config.graph.get.asInstanceOf[GraphOnDisk],
      data.instances.asJava,
      new SingleEdgeExcluder(Seq()),
      walksPerSource,
      PathTypePolicy.PAIRED_ONLY,
      pathTypeFactory)
    finder.execute(numIters)

    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    Thread.sleep(500)

    val pathCountMap = finder.getPathCountMap().asScala.mapValues(
      _.asScala.mapValues(_.toInt).toMap
    ).toMap
    finder.shutDown()
    pathCountMap
  }
}
