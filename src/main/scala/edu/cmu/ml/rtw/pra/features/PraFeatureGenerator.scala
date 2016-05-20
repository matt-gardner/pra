package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.Pair
import com.mattg.util.Vector

import java.io.File

import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.native.JsonMethods._

class PraFeatureGenerator(
  params: JValue,
  graph: GraphOnDisk,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil()
) extends FeatureGenerator[NodePairInstance] {
  implicit val formats = DefaultFormats
  val featureParamKeys = Seq("type", "path finder", "path selector", "path follower")
  JsonHelper.ensureNoExtras(params, "operation -> features", featureParamKeys)

  var pathTypes: Seq[PathType] = null

  override def constructMatrixRow(instance: NodePairInstance): Option[MatrixRow] = {
    // The reason this would be complicated is because we can't do this without having first
    // selected path features.
    throw new RuntimeException("This method is not yet implemented!  And it would be complicated...")
  }

  override def createTrainingMatrix(data: Dataset[NodePairInstance]): FeatureMatrix = {
    pathTypes = selectPathFeatures(data)
    computeFeatureValues(pathTypes, data, true)
  }

  override def removeZeroWeightFeatures(weights: Seq[Double]): Seq[Double] = {
    val finalModel = pathTypes.zip(weights).filter(_._2 != 0.0)
    pathTypes = finalModel.map(_._1)
    finalModel.map(_._2)
  }

  override def createTestMatrix(data: Dataset[NodePairInstance]): FeatureMatrix = {
    computeFeatureValues(pathTypes, data, false)
  }

  override def getFeatureNames(): Array[String] = {
    if (graph.edgeDict == null) {
      pathTypes.map(_.encodeAsString).toArray
    } else {
      pathTypes.map(_.encodeAsHumanReadableString(graph)).toArray
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
  def selectPathFeatures(data: Dataset[NodePairInstance]): Seq[PathType] = {
    outputter.info("Selecting path features with " + data.instances.size + " training instances")

    val finder = NodePairPathFinder.create(params \ "path finder", relation, relationMetadata, outputter)
    finder.findPaths(data)

    // Next we get the resultant path counts.
    val pathCounts = finder.getPathCounts().asScala.toMap.mapValues(_.toInt)
    finder.finished()
    outputter.outputPathCounts(pathCounts)

    // And finally, we select and output path types.
    val pathTypeSelector = createPathTypeSelector(params \ "path selector", finder)
    val numPaths = JsonHelper.extractWithDefault(params \ "path selector", "number of paths to keep", 1000)
    val javaPathCounts = pathCounts.mapValues(x => Integer.valueOf(x)).asJava
    val pathTypes = pathTypeSelector.selectPathTypes(javaPathCounts, numPaths).asScala
    outputter.outputPaths(pathTypes, graph)
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
   *
   * @return A feature matrix encoded as a list of {@link MatrixRow} objects.  Note that this
   *     feature matrix may not have rows corresponding to every source in sourcesMap if there
   *     were no paths from a source to an acceptable target following any of the path types,
   *     there will be no row in the matrix for that source.
   */
  def computeFeatureValues(
    pathTypes: Seq[PathType],
    data: Dataset[NodePairInstance],
    isTraining: Boolean
  ) = {
    outputter.info("Computing feature values")
    val follower = createPathFollower(params \ "path follower", pathTypes, data, isTraining)
    follower.execute()
    if (follower.usesGraphChi()) {
      // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
      // reason I don't understand.
      Thread.sleep(1000)
    }
    val featureMatrix = follower.getFeatureMatrix()
    follower.shutDown()
    featureMatrix
  }

  // TODO(matt): this code should move to a PathFollower static object, and most of the params
  // should be passed directly to be handled by the PathFollower subclasses.
  def createPathFollower(
    followerParams: JValue,
    pathTypes: Seq[PathType],
    data: Dataset[NodePairInstance],
    isTraining: Boolean
  ): PathFollower = {
    val name = JsonHelper.extractWithDefault(followerParams, "name", "random walks")
    val edgesToExclude = {
      val unallowedEdges = relationMetadata.getUnallowedEdges(relation, graph)
      if (unallowedEdges == null) {
        Seq[((Int, Int), Int)]()
      }
      data.instances.flatMap(instance => {
        unallowedEdges.map(edge => {
          ((instance.source, instance.target), edge.toInt)
        })
      })
    }
    val edgeExcluder = new SingleEdgeExcluder(edgesToExclude)
    val allowedTargets = relationMetadata.getAllowedTargets(relation, Some(graph))

    if (name.equals("random walks")) {
      val followerParamKeys = Seq("name", "walks per path", "matrix accept policy",
        "matrix accept policy: training", "matrix accept policy: test", "normalize walk probabilities")
      JsonHelper.ensureNoExtras(
        followerParams, "operation -> features -> path follower", followerParamKeys)
      val walksPerPath = JsonHelper.extractWithDefault(followerParams, "walks per path", 100)
      val acceptPolicy = getMatrixAcceptPolicy(followerParams, isTraining)
      val normalize = JsonHelper.extractWithDefault(followerParams, "normalize walk probabilities", true)
      new RandomWalkPathFollower(
        graph,
        data.instances.asJava,
        allowedTargets match { case None => null; case Some(t) => t.map(x => Integer.valueOf(x)).asJava },
        edgeExcluder,
        pathTypes.asJava,
        walksPerPath,
        acceptPolicy,
        normalize)
    } else {
      throw new IllegalStateException("Unrecognized path follower")
    }
  }

  def createPathTypeSelector(selectorParams: JValue, finder: PathFinder[NodePairInstance]): PathTypeSelector = {
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
