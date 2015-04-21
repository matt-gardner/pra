package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.Vector

import java.io.File

import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.native.JsonMethods._

class PraFeatureGenerator(
    params: JValue,
    praBase: String,
    config: PraConfig,
    fileUtil: FileUtil = new FileUtil()) extends FeatureGenerator {
  implicit val formats = DefaultFormats
  val featureParamKeys = Seq("type", "path finder", "path selector", "path follower")
  JsonHelper.ensureNoExtras(params, "pra parameters -> features", featureParamKeys)

  var pathTypes: Seq[PathType] = null

  override def createTrainingMatrix(data: Dataset): FeatureMatrix = {
    pathTypes = selectPathFeatures(data)
    computeFeatureValues(pathTypes, data, null)
  }

  override def removeZeroWeightFeatures(weights: Seq[Double]): Seq[Double] = {
    val finalModel = pathTypes.zip(weights).filter(_._2 != 0.0)
    pathTypes = finalModel.map(_._1)
    finalModel.map(_._2)
  }

  override def createTestMatrix(data: Dataset): FeatureMatrix = {
    val output = if (config.outputBase == null) null else config.outputBase + "test_matrix.tsv"
    computeFeatureValues(pathTypes, data, output)
  }

  override def getFeatureNames(): Array[String] = {
    if (config.edgeDict == null) {
      pathTypes.map(_.encodeAsString).toArray
    } else {
      pathTypes.map(_.encodeAsHumanReadableString(config.edgeDict)).toArray
    }
  }

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

    val finder = createPathFinder()
    val edgesToExclude = createEdgesToExclude(data, config.unallowedEdges)
    finder.findPaths(config, data, edgesToExclude)

    // Next we get the resultant path counts.
    val pathCounts = finder.getPathCounts().asScala.toMap.mapValues(_.toInt)
    finder.finished()
    val javaPathCounts = pathCounts.mapValues(x => Integer.valueOf(x)).asJava
    config.outputter.outputPathCounts(config.outputBase, "found_path_counts.tsv", javaPathCounts)

    // And finally, we select and output path types.
    val pathTypeSelector = createPathTypeSelector(params \ "path selector", finder)
    val numPaths = JsonHelper.extractWithDefault(params, "number of paths to keep", 1000)
    val pathTypes = pathTypeSelector.selectPathTypes(javaPathCounts, numPaths)
    config.outputter.outputPaths(config.outputBase, "kept_paths.tsv", pathTypes)
    pathTypes.asScala
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
    val edgesToExclude = createEdgesToExclude(data, config.unallowedEdges)
    val follower = createPathFollower(params \ "path follower", pathTypes, data)
    follower.execute()
    if (follower.usesGraphChi()) {
      // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
      // reason I don't understand.
      Thread.sleep(1000)
    }
    val featureMatrix = follower.getFeatureMatrix()
    follower.shutDown()
    if (outputFile != null) {
      config.outputter.outputFeatureMatrix(outputFile, featureMatrix, getFeatureNames().toSeq.asJava)
    }
    featureMatrix
  }

  def createPathFinder(): PathFinder = {
    PathFinderCreator.create(params \ "path finder", config, praBase)
  }

  def createPathFollower(
      followerParams: JValue,
      pathTypes: Seq[PathType],
      data: Dataset): PathFollower = {
    val name = JsonHelper.extractWithDefault(followerParams, "name", "random walks")
    val edgeExcluder = new SingleEdgeExcluder(createEdgesToExclude(data, config.unallowedEdges))
    if (name.equals("random walks")) {
      val followerParamKeys = Seq("name", "walks per path", "matrix accept policy",
        "normalize walk probabilities")
      JsonHelper.ensureNoExtras(
        followerParams, "pra parameters -> features -> path follower", followerParamKeys)
      val walksPerPath = JsonHelper.extractWithDefault(followerParams, "walks per path", 100)
      val acceptPolicy = JsonHelper.extractWithDefault(followerParams, "matrix accept policy", "all-targets")
      val normalize = JsonHelper.extractWithDefault(followerParams, "normalize walk probabilities", true)
      new RandomWalkPathFollower(
        config.graph,
        config.numShards,
        data.getCombinedSourceMap,
        config.allowedTargets,
        edgeExcluder,
        pathTypes.asJava,
        walksPerPath,
        MatrixRowPolicy.parseFromString(acceptPolicy),
        normalize)
    } else if (name.equals("matrix multiplication")) {
      val followerParamKeys = Seq("name", "max fan out", "matrix dir", "normalize walk probabilities")
      JsonHelper.ensureNoExtras(
        followerParams, "pra parameters -> features -> path follower", followerParamKeys)
      val max_fan_out = JsonHelper.extractWithDefault(followerParams, "max fan out", 100)
      val matrix_base = JsonHelper.extractWithDefault(followerParams, "matrix dir", "matrices")
      val normalize = JsonHelper.extractWithDefault(followerParams, "normalize walk probabilities", true)
      val graph_base = new File(config.graph).getParent + "/"
      val matrix_dir = if (matrix_base.endsWith("/")) graph_base + matrix_base else graph_base +
        matrix_base + "/"
      new MatrixPathFollower(
        config.nodeDict.getNextIndex(),
        pathTypes.asJava,
        matrix_dir,
        data,
        config.edgeDict,
        config.allowedTargets,
        edgeExcluder,
        max_fan_out,
        normalize)
    } else if (name.equals("rescal matrix multiplication")) {
      val followerParamKeys = Seq("name", "rescal dir", "negatives per source")
      JsonHelper.ensureNoExtras(
        followerParams, "pra parameters -> features -> path follower", followerParamKeys)
      val dir = (followerParams \ "rescal dir").extract[String]
      val rescal_dir = if (dir.endsWith("/")) dir else dir + "/"
      val negativesPerSource = JsonHelper.extractWithDefault(followerParams, "negatives per source", 15)
      new RescalMatrixPathFollower(config, pathTypes.asJava, rescal_dir, data, negativesPerSource)
    } else {
      throw new IllegalStateException("Unrecognized path follower")
    }
  }

  def createPathTypeSelector(selectorParams: JValue, finder: PathFinder): PathTypeSelector = {
    val name = JsonHelper.extractWithDefault(selectorParams, "name", "MostFrequentPathTypeSelector")
    if (name.equals("MostFrequentPathTypeSelector")) {
      new MostFrequentPathTypeSelector()
    } else if (name.equals("VectorClusteringPathTypeSelector")) {
      val similarityThreshold = (selectorParams \ "similarity threshold").extract[Double]
      new VectorClusteringPathTypeSelector(
        finder.asInstanceOf[GraphChiPathFinder].pathTypeFactory.asInstanceOf[VectorPathTypeFactory],
        similarityThreshold)
    } else {
      throw new IllegalStateException("Unrecognized path type selector")
    }
  }
}
