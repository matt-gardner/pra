package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.users.matt.util.Pair

import java.lang.Integer

import scala.collection.JavaConverters._

class FeatureGenerator(config: PraConfig) {

  /**
   * Do feature selection for a PRA model, which amounts to finding common paths between sources
   * and targets.
   * <p>
   * This pretty much just wraps around the PathFinder GraphChi program, which does
   * random walks to find paths between source and target nodes, along with a little bit of post
   * processing to (for example) collapse paths that are the same, but are written differently in
   * the GraphChi output because of inverse relationships.
   *
   * @param data A {@link Dataset} containing source and target nodes from which we start walks.
   *
   * @return A ranked list of the <code>numPaths</code> highest ranked path features, encoded as
   *     {@link PathType} objects.
   */
  def selectPathFeatures(data: Dataset): Seq[PathType] = {
    println("Selecting path features with " + data.getAllSources().size() + " training instances")
    val edgesToExclude = createEdgesToExclude(data)
    val finder = new PathFinder(config.graph,
      config.numShards,
      data.getAllSources(),
      data.getAllTargets(),
      new SingleEdgeExcluder(edgesToExclude),
      config.walksPerSource,
      config.pathTypePolicy,
      config.pathTypeFactory)
    finder.execute(config.numIters)
    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    Thread.sleep(500)

    val finderPathCounts = finder.getPathCounts().asScala.toMap.mapValues(_.toInt)
    val inverses = config.relationInverses.asScala.map(x => (x._1.toInt, x._2.toInt)).toMap
    val pathCounts = collapseInverses(finderPathCounts, inverses)
    finder.shutDown()
    val javaPathCounts = pathCounts.mapValues(x => Integer.valueOf(x)).asJava
    config.outputter.outputPathCounts(config.outputBase, "found_path_counts.tsv", javaPathCounts)
    val pathTypes = config.pathTypeSelector.selectPathTypes(javaPathCounts, config.numPaths)
    // THIS IS UGLY!!!  I'm experimenting a bit here.  TODO(matt): This should change before
    // anything becomes final.
    config.pathFollowerFactory match {
      case f: RescalMatrixPathFollowerFactory => {
        pathTypes.add(0, config.pathTypeFactory.fromString("-" + config.relation + "-"))
      }
      case other => {}
    }
    config.outputter.outputPaths(config.outputBase, "kept_paths.tsv", pathTypes)
    pathTypes.asScala
  }

  /**
   * Like selectPathFeatures, but instead of returning just a ranked list of paths, we return
   * all of the paths found between each input (source, target) pair.  This is useful for
   * exploratory data analysis, and maybe a few other things.  If you're building a model where
   * the _presence_ of a connection is important, but not the actual random walk probability,
   * then this is sufficient to give you a feature matrix.  Note that the "count" returned is a
   * bit hackish, however, because the PathFinder joins on intermediate nodes.
   *
   * @param data A {@link Dataset} containing source and target nodes from which we start walks.
   *
   * @return A map from (source, target) pairs to (path type, count) pairs.
   */
  def findConnectingPaths(data: Dataset) = {
    println("Finding connecting paths")
    val edgesToExclude = createEdgesToExclude(data)
    val finder = new PathFinder(config.graph,
      config.numShards,
      data.getAllSources(),
      data.getAllTargets(),
      new SingleEdgeExcluder(edgesToExclude),
      config.walksPerSource,
      config.pathTypePolicy,
      config.pathTypeFactory)
    finder.execute(config.numIters)

    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    Thread.sleep(500)

    val finderPathCountMap = finder.getPathCountMap().asScala.map(entry => {
      val key = (entry._1.getLeft().toInt, entry._1.getRight().toInt)
      val value = entry._2.asScala.mapValues(_.toInt).toMap
      (key, value)
    }).toMap
    val inverses = config.relationInverses.asScala.map(x => (x._1.toInt, x._2.toInt)).toMap
    val pathCountMap = collapseInversesInCountMap(finderPathCountMap, inverses)
    finder.shutDown()
    val javaMap = pathCountMap.map(entry => {
      val key = new Pair[Integer, Integer](entry._1._1, entry._1._2)
      val value = entry._2.mapValues(x => Integer.valueOf(x)).asJava
      (key, value)
    }).asJava
    config.outputter.outputPathCountMap(config.outputBase, "path_count_map.tsv", javaMap, data)
    pathCountMap
  }

  /**
   * Given a set of source nodes and path types, compute values for a feature matrix where the
   * feature types (or columns) are the path types, the rows are (source node, target node)
   * pairs, and the values are the probability of starting at source node, following a path of a
   * particular type, and ending at target node.
   * <p>
   * This is essentially a simple wrapper around the PathFollower GraphChi program, which
   * computes these features using random walks.
   * <p>
   * Note that this computes a fixed number of _columns_ of the feature matrix, with a not
   * necessarily known number of rows (when only the source node of the row is specified).
   *
   * @param pathTypes A list of {@link PathType} objects specifying the path types to follow from
   *     each source node.
   * @param sourcesMap A set of source nodes to start walks from, mapped to targets that are
   *     known to be paired with that source.  The mapped targets have two uses.  First, along
   *     with <code>config.unallowedEdges</code>, they determine which edges are cheating and
   *     thus not allowed to be followed for a given source.  Second, they are used in some
   *     accept policies (see documentation for <code>config.acceptPolicy</code>).  To do a query
   *     of the form (source, relation, *), simply pass in a map that has empty sets
   *     corresponding to each source.  This is appropriate for production use during prediction
   *     time, but not really appropriate for testing, as you could easily be cheating if the
   *     edge you are testing already exists in the graph.  You also shouldn't do this during
   *     training time, as you want to training data to match the test data, and the test data
   *     will not have the relation edge between the source and the possible targets.
   * @param outputFile If not null, the location to save the computed feature matrix.  We can't
   *     just use config.outputBase here, because this method gets called from several places,
   *     with potentially different filenames under config.outputBase.
   *
   * @return A feature matrix encoded as a list of {@link MatrixRow} objects.  Note that this
   *     feature matrix may not have rows corresponding to every source in sourcesMap if there
   *     were no paths from a source to an acceptable target following any of the path types,
   *     there will be no row in the matrix for that source.
   */
  def computeFeatureValues(pathTypes: Seq[PathType], data: Dataset, outputFile: String) = {
    println("Computing feature values")
    val edgesToExclude = createEdgesToExclude(data)
    val follower = config.pathFollowerFactory.create(
      pathTypes.asJava, config, data, new SingleEdgeExcluder(edgesToExclude))
    follower.execute()
    if (follower.usesGraphChi()) {
      // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
      // reason I don't understand.
      Thread.sleep(1000)
    }
    val featureMatrix = follower.getFeatureMatrix()
    follower.shutDown()
    if (outputFile != null) {
      config.outputter.outputFeatureMatrix(outputFile, featureMatrix, pathTypes.asJava)
    }
    featureMatrix
  }

  def collapseInverses(pathCounts: Map[PathType, Int], inverses: Map[Int, Int]) = {
    val javaInverses = inverses.map(x => (Integer.valueOf(x._1), Integer.valueOf(x._2))).asJava
    pathCounts.toSeq.map(pathCount => {
      (config.pathTypeFactory.collapseEdgeInverses(pathCount._1, javaInverses), pathCount._2)
    }).groupBy(_._1).mapValues(_.map(_._2.toInt).sum).toMap
  }

  def collapseInversesInCountMap(
      pathCountMap: Map[(Int, Int), Map[PathType, Int]],
      inverses: Map[Int, Int]) = {
    pathCountMap.mapValues(m => collapseInverses(m, inverses))
  }

  def createEdgesToExclude(data: Dataset): Seq[((Int, Int), Int)] = {
    // If there was no input data (e.g., if we are actually trying to predict new edges, not
    // just hide edges from ourselves to try to recover), then there aren't any edges to
    // exclude.  So return an empty list.
    if (data == null) {
      return Seq()
    }
    val sources = data.getAllSources().asScala.map(_.toInt)
    val targets = data.getAllTargets().asScala.map(_.toInt)
    if (sources.size == 0 || targets.size == 0) {
      return Seq()
    }
    sources.zip(targets).flatMap(sourceTarget => {
      config.unallowedEdges.asScala.map(edge => {
        (sourceTarget, edge.toInt)
      })
    })
  }
}
