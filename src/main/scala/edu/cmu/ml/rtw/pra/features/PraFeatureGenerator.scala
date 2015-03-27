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

    // First we get necessary path finding parameters from the params object (we do this here
    // because the params object is hard to work with in java; otherwise we'd just pass part of the
    // object to the path finder).
    val finderParams = params \ "path finder"
    val finderParamKeys = Seq("walks per source", "path accept policy", "path type factory",
      "path type selector", "path finding iterations")
    JsonHelper.ensureNoExtras(finderParams, "pra parameters -> features -> path finder", finderParamKeys)
    val walksPerSource = JsonHelper.extractWithDefault(finderParams, "walks per source", 100)
    val pathAcceptPolicy = JsonHelper.extractWithDefault(finderParams, "path accept policy", "paired-only")
    val pathTypeFactory = createPathTypeFactory(finderParams \ "path type factory")
    val numIters = JsonHelper.extractWithDefault(finderParams, "path finding iterations", 3)

    // Now we create and run the path finder.
    val edgesToExclude = createEdgesToExclude(data, config.unallowedEdges)
    val finder = new PathFinder(config.graph,
      config.numShards,
      data.getAllSources(),
      data.getAllTargets(),
      new SingleEdgeExcluder(edgesToExclude),
      walksPerSource,
      PathTypePolicy.parseFromString(pathAcceptPolicy),
      pathTypeFactory)
    finder.execute(numIters)
    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    Thread.sleep(500)

    // Next we get the resultant path counts.
    val finderPathCounts = finder.getPathCounts().asScala.toMap.mapValues(_.toInt)
    val inverses = config.relationInverses.asScala.map(x => (x._1.toInt, x._2.toInt)).toMap
    val pathCounts = collapseInverses(finderPathCounts, inverses, pathTypeFactory)
    finder.shutDown()
    val javaPathCounts = pathCounts.mapValues(x => Integer.valueOf(x)).asJava
    config.outputter.outputPathCounts(config.outputBase, "found_path_counts.tsv", javaPathCounts)

    // And finally, we select and output path types.
    val pathTypeSelector = createPathTypeSelector(params \ "path selector", pathTypeFactory)
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

  def collapseInverses(
      pathCounts: Map[PathType, Int],
      inverses: Map[Int, Int],
      pathTypeFactory: PathTypeFactory) = {
    val javaInverses = inverses.map(x => (Integer.valueOf(x._1), Integer.valueOf(x._2))).asJava
    pathCounts.toSeq.map(pathCount => {
      (pathTypeFactory.collapseEdgeInverses(pathCount._1, javaInverses), pathCount._2)
    }).groupBy(_._1).mapValues(_.map(_._2.toInt).sum).toMap
  }

  def collapseInversesInCountMap(
      pathCountMap: Map[(Int, Int), Map[PathType, Int]],
      inverses: Map[Int, Int],
      pathTypeFactory: PathTypeFactory) = {
    pathCountMap.mapValues(m => collapseInverses(m, inverses, pathTypeFactory))
  }

  def createPathTypeFactory(params: JValue): PathTypeFactory = {
    (params \ "name") match {
      case JNothing => new BasicPathTypeFactory()
      case JString("VectorPathTypeFactory") => createVectorPathTypeFactory(params)
      case other => throw new IllegalStateException("Unregonized path type factory")
    }
  }

  def createVectorPathTypeFactory(params: JValue) = {
    println("Initializing vector path type factory")
    val spikiness = (params \ "spikiness").extract[Double]
    val resetWeight = (params \ "reset weight").extract[Double]
    println(s"RESET WEIGHT SET TO $resetWeight")
    val embeddingsFiles = (params \ "embeddings") match {
      case JNothing => Nil
      case JString(path) if (path.startsWith("/")) => List(path)
      case JString(name) => List(s"${praBase}embeddings/${name}/embeddings.tsv")
      case JArray(list) => {
        list.map(_ match {
          case JString(path) if (path.startsWith("/")) => path
          case JString(name) => s"${praBase}embeddings/${name}/embeddings.tsv"
          case other => throw new IllegalStateException("Error specifying embeddings")
        })
      }
      case jval => {
        val name = (jval \ "name").extract[String]
        List(s"${praBase}embeddings/${name}/embeddings.tsv")
      }
    }
    val embeddings = readEmbeddingsVectors(embeddingsFiles)
    val javaEmbeddings = embeddings.map(entry => (Integer.valueOf(entry._1), entry._2)).asJava
    new VectorPathTypeFactory(config.edgeDict, javaEmbeddings, spikiness, resetWeight)
  }

  def readEmbeddingsVectors(embeddingsFiles: Seq[String]) = {
    embeddingsFiles.flatMap(file => {
      println(s"Embeddings file: $file")
      readVectorsFromFile(file)
    }).toMap
  }

  def readVectorsFromFile(embeddingsFile: String) = {
    // Embeddings files are formated as tsv, where the first column is the relation name
    // and the rest of the columns make up the vector.
    fileUtil.readLinesFromFile(embeddingsFile).asScala.map(line => {
      val fields = line.split("\t");
      val relationIndex = config.edgeDict.getIndex(fields(0));
      val vector = fields.drop(1).map(_.toDouble)
      (relationIndex, new Vector(vector))
    }).toMap
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

  def createPathTypeSelector(selectorParams: JValue, factory: PathTypeFactory): PathTypeSelector = {
    val name = JsonHelper.extractWithDefault(selectorParams, "name", "MostFrequentPathTypeSelector")
    if (name.equals("MostFrequentPathTypeSelector")) {
      new MostFrequentPathTypeSelector()
    } else if (name.equals("VectorClusteringPathTypeSelector")) {
      val similarityThreshold = (selectorParams \ "similarity threshold").extract[Double]
      new VectorClusteringPathTypeSelector(factory.asInstanceOf[VectorPathTypeFactory],
        similarityThreshold)
    } else {
      throw new IllegalStateException("Unrecognized path type selector")
    }
  }
}
