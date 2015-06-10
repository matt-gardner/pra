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
    computeFeatureValues(pathTypes, data, null, true)
  }

  override def removeZeroWeightFeatures(weights: Seq[Double]): Seq[Double] = {
    val finalModel = pathTypes.zip(weights).filter(_._2 != 0.0)
    pathTypes = finalModel.map(_._1)
    finalModel.map(_._2)
  }

  override def createTestMatrix(data: Dataset): FeatureMatrix = {
    val output = if (config.outputBase == null) null else config.outputBase + "test_matrix.tsv"
    computeFeatureValues(pathTypes, data, output, false)
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
    println("Selecting path features with " + data.instances.size + " training instances")

    val finder = createPathFinder()
    val edgesToExclude = createEdgesToExclude(data, config.unallowedEdges)
    finder.findPaths(config, data, edgesToExclude)

    // Next we get the resultant path counts.
    val pathCounts = finder.getPathCounts().asScala.toMap.mapValues(_.toInt)
    finder.finished()
    config.outputter.outputPathCounts(config.outputBase, "found_path_counts.tsv", pathCounts)

    // And finally, we select and output path types.
    val pathTypeSelector = createPathTypeSelector(params \ "path selector", finder)
    val numPaths = JsonHelper.extractWithDefault(params, "number of paths to keep", 1000)
    val javaPathCounts = pathCounts.mapValues(x => Integer.valueOf(x)).asJava
    val pathTypes = pathTypeSelector.selectPathTypes(javaPathCounts, numPaths).asScala
    config.outputter.outputPaths(config.outputBase, "kept_paths.tsv", pathTypes)
    pathTypes
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
   * @param outputFile If not null, the location to save the computed feature matrix.  We can't
   *     just use config.outputBase here, because this method gets called from several places,
   *     with potentially different filenames under config.outputBase.
   *
   * @return A feature matrix encoded as a list of {@link MatrixRow} objects.  Note that this
   *     feature matrix may not have rows corresponding to every source in sourcesMap if there
   *     were no paths from a source to an acceptable target following any of the path types,
   *     there will be no row in the matrix for that source.
   */
  def computeFeatureValues(
      pathTypes: Seq[PathType],
      data: Dataset,
      outputFile: String,
      isTraining: Boolean) = {
    println("Computing feature values")
    val edgesToExclude = createEdgesToExclude(data, config.unallowedEdges)
    val follower = createPathFollower(params \ "path follower", pathTypes, data, isTraining)
    follower.execute()
    if (follower.usesGraphChi()) {
      // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
      // reason I don't understand.
      Thread.sleep(1000)
    }
    val featureMatrix = follower.getFeatureMatrix()
    follower.shutDown()
    if (config.outputMatrices && outputFile != null) {
      config.outputter.outputFeatureMatrix(outputFile, featureMatrix, getFeatureNames())
    }
    featureMatrix
  }

  def createPathFinder(): PathFinder = {
    PathFinderCreator.create(params \ "path finder", config, praBase)
  }

  // TODO(matt): this code should move to a PathFollower static object, and most of the params
  // should be passed directly to be handled by the PathFollower subclasses.
  def createPathFollower(
      followerParams: JValue,
      pathTypes: Seq[PathType],
      data: Dataset,
      isTraining: Boolean): PathFollower = {
    val name = JsonHelper.extractWithDefault(followerParams, "name", "random walks")
    val edgeExcluder = new SingleEdgeExcluder(createEdgesToExclude(data, config.unallowedEdges))
    if (name.equals("random walks")) {
      val followerParamKeys = Seq("name", "walks per path", "matrix accept policy",
        "matrix accept policy: training", "matrix accept policy: test", "normalize walk probabilities")
      JsonHelper.ensureNoExtras(
        followerParams, "pra parameters -> features -> path follower", followerParamKeys)
      val walksPerPath = JsonHelper.extractWithDefault(followerParams, "walks per path", 100)
      val acceptPolicy = getMatrixAcceptPolicy(followerParams, isTraining)
      val normalize = JsonHelper.extractWithDefault(followerParams, "normalize walk probabilities", true)
      new RandomWalkPathFollower(
        config.graph,
        config.numShards,
        data.getSourceMap.map(entry => (Integer.valueOf(entry._1) -> entry._2.map(x =>
            Integer.valueOf(x)).asJava)).asJava,
        config.allowedTargets,
        edgeExcluder,
        pathTypes.asJava,
        walksPerPath,
        acceptPolicy,
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
        pathTypes,
        matrix_dir,
        data,
        config.edgeDict,
        if (config.allowedTargets == null) null else config.allowedTargets.asScala.map(_.toInt).toSet,
        edgeExcluder,
        max_fan_out,
        normalize,
        fileUtil)
    } else if (name.equals("rescal matrix multiplication")) {
      val followerParamKeys = Seq("name", "rescal dir", "negatives per source", "matrix accept policy")
      JsonHelper.ensureNoExtras(
        followerParams, "pra parameters -> features -> path follower", followerParamKeys)
      val dir = (followerParams \ "rescal dir").extract[String]
      val rescal_dir = if (dir.endsWith("/")) dir else dir + "/"
      val acceptPolicy = getMatrixAcceptPolicy(followerParams, isTraining)
      val negativesPerSource = JsonHelper.extractWithDefault(followerParams, "negatives per source", 15)
      new RescalMatrixPathFollower(config, pathTypes, rescal_dir, data, negativesPerSource, acceptPolicy, fileUtil)
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

  def getMatrixAcceptPolicy(params: JValue, isTraining: Boolean) = {
    val both = JsonHelper.extractWithDefault(params, "matrix accept policy", null: String)
    if (both != null) {
      MatrixRowPolicy.parseFromString(both)
    } else {
      val training = JsonHelper.extractWithDefault(params, "matrix accept policy: training", "all-targets")
      if (isTraining) {
        MatrixRowPolicy.parseFromString(training)
      } else {
        val test = JsonHelper.extractWithDefault(params, "matrix accept policy: test", training)
        MatrixRowPolicy.parseFromString(test)
      }
    }
  }
}
