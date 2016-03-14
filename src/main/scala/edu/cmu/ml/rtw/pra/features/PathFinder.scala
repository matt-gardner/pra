package edu.cmu.ml.rtw.pra.features

import java.util.{Map => JavaMap}
import java.util.{Set => JavaSet}

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.Pair
import com.mattg.util.Vector

import scala.collection.JavaConverters._

// TODO(matt): move RandomWalkPathFinder to scala, and change these definitions to use scala
// objects.  Or just leave RandomWalkPathFinder in java, and incur a hit when converting between
// datatypes (do the conversion in the GraphChiPathFinder class below).  If it turns out that we
// move away from the RandomWalkPathFinder, then we can take the second option.  Otherwise, the
// first.
trait PathFinder[T <: Instance] {
  // Constructs a local subgraph for a single instance.  This is for SGD-style training, as opposed
  // to a batch computation.  Some PathFinders may not support this mode of operation (it's
  // incredibly inefficient with GraphChi, for instance).  This getLocalSubgraphs method does not
  // require first running findPaths.
  def getLocalSubgraph(instance: T): Subgraph

  // Does whatever computation is necessary to find paths between nodes requested in the dataset.
  def findPaths(data: Dataset[T])

  // These look at the paths found during findPaths and output different results.  Behavior is
  // undefined if called before findPaths, and can either crash or give empty results.
  def getPathCounts(): JavaMap[PathType, Integer]
  def getPathCountMap(): JavaMap[T, JavaMap[PathType, Integer]]
  def getLocalSubgraphs(): Map[T, Map[PathType, Set[(Int, Int)]]]
  def finished()
}

object NodePairPathFinder {
  def create(
    params: JValue,
    relation: String,
    relationMetadata: RelationMetadata,
    outputter: Outputter,
    fileUtil: FileUtil = new FileUtil
  ): PathFinder[NodePairInstance] = {
    val finderType = JsonHelper.extractWithDefault(params, "type", "BfsPathFinder")
    finderType match {
      case "RandomWalkPathFinder" =>
        new GraphChiPathFinder(params, relation, relationMetadata, outputter, fileUtil)
      case "BfsPathFinder" =>
        new NodePairBfsPathFinder(params, relation, relationMetadata, outputter, fileUtil)
      case other => throw new IllegalStateException("Unrecognized path finder for NodePairInstances")
    }
  }
}

object NodePathFinder {
  def create(
    params: JValue,
    relation: String,
    relationMetadata: RelationMetadata,
    outputter: Outputter,
    fileUtil: FileUtil = new FileUtil
  ): PathFinder[NodeInstance] = {
    val finderType = JsonHelper.extractWithDefault(params, "type", "BfsPathFinder")
    finderType match {
      case "BfsPathFinder" =>
        new NodeBfsPathFinder(params, relation, relationMetadata, outputter, fileUtil)
      case other => throw new IllegalStateException("Unrecognized path finder for NodeInstances")
    }
  }
}

// A simple wrapper around RandomWalkPathFinder, which is a java class, to make the interface with
// this new PathFinder trait a little cleaner.  If we move RandomWalkPathFinder to scala, this
// class can go away.
class GraphChiPathFinder(
  params: JValue,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
) extends PathFinder[NodePairInstance] {
  implicit val formats = DefaultFormats
  val allowedKeys = Seq("type", "walks per source", "path accept policy", "path type factory",
    "path finding iterations", "reset probability")
  JsonHelper.ensureNoExtras(params, "operation -> features -> path finder", allowedKeys)
  val walksPerSource = JsonHelper.extractWithDefault(params, "walks per source", 100)
  val numIters = JsonHelper.extractWithDefault(params, "path finding iterations", 3)
  val pathAcceptPolicy = JsonHelper.extractWithDefault(params, "path accept policy", "paired-only")
  val resetProbability = JsonHelper.extractWithDefault(params, "reset probability", 0.0)

  var finder: RandomWalkPathFinder = null

  // This is an ugly var because of a poor design in PraFeatureGenerator.  In order to create a
  // VectorClusteringPathTypeSelector, I need access to the pathTypeFactory, which doesn't get
  // created until I have a Graph object in findPaths...  This could be done better, but it's not
  // worth the refactoring, because I don't use this code anymore.
  var pathTypeFactory: PathTypeFactory = null

  override def findPaths(data: Dataset[NodePairInstance]) {
    val graph = data.instances(0).graph.asInstanceOf[GraphOnDisk]

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
    pathTypeFactory = createPathTypeFactory(params \ "path type factory", graph)

    // Now we create and run the path finder.
    finder = new RandomWalkPathFinder(graph,
      data.instances.asJava,
      new SingleEdgeExcluder(edgesToExclude),
      walksPerSource,
      PathTypePolicy.parseFromString(pathAcceptPolicy),
      pathTypeFactory
    )
    finder.setResetProbability(resetProbability)
    finder.execute(numIters)
    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    Thread.sleep(500)
  }

  override def finished() { finder.shutDown() }

  override def getLocalSubgraph(instance: NodePairInstance): Subgraph = {
    throw new RuntimeException("This method is not implemented for this PathFinder, " +
      "and would be incredibly inefficient to use anyway.")
  }

  override def getPathCounts(): JavaMap[PathType, Integer] = {
    finder.getPathCounts()
  }

  override def getPathCountMap(): JavaMap[NodePairInstance, JavaMap[PathType, Integer]] = {
    finder.getPathCountMap()
  }

  override def getLocalSubgraphs() = {
    finder.getLocalSubgraphs().asScala.mapValues(_.asScala.mapValues(_.asScala.map(pair =>
      (pair.getLeft().toInt, pair.getRight().toInt)).toSet).toMap).toMap
  }

  def createPathTypeFactory(params: JValue, graph: GraphOnDisk): PathTypeFactory = {
    (params \ "name") match {
      case JNothing => new BasicPathTypeFactory(graph)
      case JString("VectorPathTypeFactory") => createVectorPathTypeFactory(params, graph)
      case other => throw new IllegalStateException("Unregonized path type factory")
    }
  }

  def createVectorPathTypeFactory(params: JValue, graph: GraphOnDisk) = {
    outputter.info("Initializing vector path type factory")
    val spikiness = (params \ "spikiness").extract[Double]
    val resetWeight = (params \ "reset weight").extract[Double]
    outputter.info(s"RESET WEIGHT SET TO $resetWeight")
    val embeddingsFiles = (params \ "embeddings") match {
      case JNothing => Nil
      case JString(path) if (path.startsWith("/")) => List(path)
      case JArray(list) => {
        list.map(_ match {
          case JString(path) if (path.startsWith("/")) => path
          case other => throw new IllegalStateException("Error specifying embeddings (you must "
            + "give full paths to embedding files)")
        })
      }
      case other => throw new IllegalStateException("Error specifying embeddings (you must "
        + "give full paths to embedding files)")
    }
    val embeddings = readEmbeddingsVectors(embeddingsFiles, graph)
    val javaEmbeddings = embeddings.map(entry => (Integer.valueOf(entry._1), entry._2)).asJava
    new VectorPathTypeFactory(graph, javaEmbeddings, spikiness, resetWeight)
  }

  def readEmbeddingsVectors(embeddingsFiles: Seq[String], graph: Graph) = {
    embeddingsFiles.flatMap(file => {
      outputter.info(s"Embeddings file: $file")
      readVectorsFromFile(file, graph)
    }).toMap
  }

  def readVectorsFromFile(embeddingsFile: String, graph: Graph) = {
    // Embeddings files are formated as tsv, where the first column is the relation name
    // and the rest of the columns make up the vector.
    fileUtil.readLinesFromFile(embeddingsFile).map(line => {
      val fields = line.split("\t");
      val relationIndex = graph.getEdgeIndex(fields(0));
      val vector = fields.drop(1).map(_.toDouble)
      (relationIndex, new Vector(vector))
    }).toMap
  }
}
