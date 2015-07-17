package edu.cmu.ml.rtw.pra.features

import java.util.{Map => JavaMap}
import java.util.{Set => JavaSet}

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.Vector

import scala.collection.JavaConverters._

// TODO(matt): move RandomWalkPathFinder to scala, and change these definitions to use scala
// objects.  Or just leave RandomWalkPathFinder in java, and incur a hit when converting between
// datatypes (do the conversion in the GraphChiPathFinder class below).  If it turns out that we
// move away from the RandomWalkPathFinder, then we can take the second option.  Otherwise, the
// first.
trait PathFinder {
  // Does whatever computation is necessary to find paths between nodes requested in the dataset.
  def findPaths(config: PraConfig, data: Dataset, edgesToExclude: Seq[((Int, Int), Int)])

  // These look at the paths found during findPaths and output different results.  Behavior is
  // undefined if called before findPaths, and can either crash or give empty results.
  def getPathCounts(): JavaMap[PathType, Integer]
  def getPathCountMap(): JavaMap[Instance, JavaMap[PathType, Integer]]
  def getLocalSubgraphs(): JavaMap[Instance, JavaMap[PathType, JavaSet[Pair[Integer, Integer]]]]
  def finished()
}

object PathFinderCreator {
  def create(
      params: JValue,
      config: PraConfig,
      praBase: String,
      fileUtil: FileUtil = new FileUtil): PathFinder = {
    val finderType = JsonHelper.extractWithDefault(params, "type", "RandomWalkPathFinder")
    finderType match {
      case "RandomWalkPathFinder" => new GraphChiPathFinder(params, praBase, fileUtil)
      case "BfsPathFinder" => new BfsPathFinder(params, config, praBase, fileUtil)
      case other => throw new IllegalStateException("Unrecognized path finder")
    }
  }
}

// A simple wrapper around RandomWalkPathFinder, which is a java class, to make the interface with
// this new PathFinder trait a little cleaner.  If we move RandomWalkPathFinder to scala, this
// class can go away.
class GraphChiPathFinder(params: JValue, praBase: String, fileUtil: FileUtil = new FileUtil) extends PathFinder {
  implicit val formats = DefaultFormats
  val allowedKeys = Seq("type", "walks per source", "path accept policy", "path type factory",
    "path finding iterations", "reset probability")
  JsonHelper.ensureNoExtras(params, "pra parameters -> features -> path finder", allowedKeys)
  val walksPerSource = JsonHelper.extractWithDefault(params, "walks per source", 100)
  val numIters = JsonHelper.extractWithDefault(params, "path finding iterations", 3)
  val pathAcceptPolicy = JsonHelper.extractWithDefault(params, "path accept policy", "paired-only")
  val resetProbability = JsonHelper.extractWithDefault(params, "reset probability", 0.0)

  var finder: RandomWalkPathFinder = null
  var pathTypeFactory: PathTypeFactory = null
  var inverses: Map[Int, Int] = null

  override def findPaths(config: PraConfig, data: Dataset, edgesToExclude: Seq[((Int, Int), Int)]) {
    // A little ugly, but this is to save some state that needs access to the config object before
    // it can be built.  We'll need these things later when calling getPathCounts and related
    // methods.
    pathTypeFactory = createPathTypeFactory(params \ "path type factory", config)
    inverses = if (config.relationInverses != null) {
      config.relationInverses.map(x => (x._1.toInt, x._2.toInt)).toMap
    } else {
      Map()
    }

    // Now we create and run the path finder.
    val graph = config.graph.get.asInstanceOf[GraphOnDisk]
    finder = new RandomWalkPathFinder(graph,
      data.instances.asJava,
      new SingleEdgeExcluder(edgesToExclude),
      walksPerSource,
      PathTypePolicy.parseFromString(pathAcceptPolicy),
      pathTypeFactory)
    finder.setResetProbability(resetProbability)
    finder.execute(numIters)
    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    Thread.sleep(500)
  }

  override def finished() { finder.shutDown() }

  override def getPathCounts(): JavaMap[PathType, Integer] = {
    GraphChiPathFinder.collapseInverses(finder.getPathCounts(), inverses, pathTypeFactory)
  }

  override def getPathCountMap(): JavaMap[Instance, JavaMap[PathType, Integer]] = {
    GraphChiPathFinder.collapseInversesInCountMap(finder.getPathCountMap(), inverses, pathTypeFactory)
  }

  override def getLocalSubgraphs() = {
    finder.getLocalSubgraphs()
  }

  def createPathTypeFactory(params: JValue, config: PraConfig): PathTypeFactory = {
    (params \ "name") match {
      case JNothing => new BasicPathTypeFactory()
      case JString("VectorPathTypeFactory") => createVectorPathTypeFactory(params, config)
      case other => throw new IllegalStateException("Unregonized path type factory")
    }
  }

  def createVectorPathTypeFactory(params: JValue, config: PraConfig) = {
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
    val embeddings = readEmbeddingsVectors(embeddingsFiles, config.graph.get)
    val javaEmbeddings = embeddings.map(entry => (Integer.valueOf(entry._1), entry._2)).asJava
    new VectorPathTypeFactory(config.graph.get, javaEmbeddings, spikiness, resetWeight)
  }

  def readEmbeddingsVectors(embeddingsFiles: Seq[String], graph: Graph) = {
    embeddingsFiles.flatMap(file => {
      println(s"Embeddings file: $file")
      readVectorsFromFile(file, graph)
    }).toMap
  }

  def readVectorsFromFile(embeddingsFile: String, graph: Graph) = {
    // Embeddings files are formated as tsv, where the first column is the relation name
    // and the rest of the columns make up the vector.
    fileUtil.readLinesFromFile(embeddingsFile).asScala.map(line => {
      val fields = line.split("\t");
      val relationIndex = graph.getEdgeIndex(fields(0));
      val vector = fields.drop(1).map(_.toDouble)
      (relationIndex, new Vector(vector))
    }).toMap
  }
}

object GraphChiPathFinder {
  def collapseInverses(
      pathCounts: JavaMap[PathType, Integer],
      inverses: Map[Int, Int],
      pathTypeFactory: PathTypeFactory) = {
    val javaInverses = inverses.map(x => (Integer.valueOf(x._1), Integer.valueOf(x._2))).asJava
    pathCounts.asScala.toSeq.map(pathCount => {
      (pathTypeFactory.collapseEdgeInverses(pathCount._1, javaInverses), pathCount._2)
    }).groupBy(_._1).mapValues(x => Integer.valueOf(x.map(_._2.toInt).sum)).toMap.asJava
  }

  def collapseInversesInCountMap(
      pathCountMap: JavaMap[Instance, JavaMap[PathType, Integer]],
      inverses: Map[Int, Int],
      pathTypeFactory: PathTypeFactory) = {
    pathCountMap.asScala.mapValues(m => collapseInverses(m, inverses, pathTypeFactory)).asJava
  }

}
