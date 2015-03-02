package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy
import edu.cmu.ml.rtw.pra.features.MatrixPathFollowerFactory
import edu.cmu.ml.rtw.pra.features.PathTypePolicy
import edu.cmu.ml.rtw.pra.features.RandomWalkPathFollowerFactory
import edu.cmu.ml.rtw.pra.features.VectorClusteringPathTypeSelector
import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory
import edu.cmu.ml.rtw.pra.graphs.GraphConfig
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConversions._

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

class SpecFileReader(baseDir: String, fileUtil: FileUtil = new FileUtil()) {
  implicit val formats = DefaultFormats

  def readSpecFile(file: java.io.File): JValue = {
    readSpecFile(fileUtil.readLinesFromFile(file))
  }

  def readSpecFile(filename: String): JValue = {
    readSpecFile(fileUtil.readLinesFromFile(filename))
  }

  def readSpecFile(lines: Seq[String]): JValue = {
    val empty_params = new JObject(Nil)
    val params = populateParamsFromSpecs(lines, empty_params)
    doNestedLoads(params)
  }

  // This handles load statements at the beginning of a file, however many there are.
  def populateParamsFromSpecs(specs: Seq[String], params: JValue): JValue = {
    if (specs(0).startsWith("load")) {
      return readSpecFile(getParamFileFromLoadStatement(specs(0))) merge populateParamsFromSpecs(
        specs.drop(1), params)
    } else {
      params merge parse(specs.mkString(" "))
    }
  }

  // This handles load statements that are given as values in the json (i.e. "graph": "load file").
  def doNestedLoads(params: JValue): JValue = {
    params mapField {
      case (name, JString(load)) if (load.startsWith("load ")) => {
        (name, readSpecFile(getParamFileFromLoadStatement(load)))
      }
      case (name, JArray(list)) => {
        val new_list = list.map(_ match {
          case JString(load) if (load.startsWith("load ")) => {
            readSpecFile(getParamFileFromLoadStatement(load))
          }
          case other => other
        })
        (name, new_list)
      }
      case other => other
    }
  }

  def getParamFileFromLoadStatement(load: String) = {
        val name = load.split(" ")(1)
        if (name.startsWith("/")) {
          name
        } else {
          s"${baseDir}param_files/${name}.json"
        }
  }

  def setPraConfigFromParams(params: JValue, config: PraConfig.Builder) {
    // It's important that this happens first.  Well, it's at least important that this happens
    // before anything that uses the dictionaries (like initializePathTypeFactory, for instance),
    // so we just put it here at the top to be safe.
    initializeGraphParameters(getGraphDirectory(params), config)

    val pra_params = params \ "pra parameters"

    var value = pra_params \ "l1 weight"
    if (!value.equals(JNothing)) {
      config.setL1Weight(value.extract[Double])
    }
    value = pra_params \ "l2 weight"
    if (!value.equals(JNothing)) {
      config.setL2Weight(value.extract[Double])
    }
    value = pra_params \ "walks per source"
    if (!value.equals(JNothing)) {
      config.setWalksPerSource(value.extract[Int])
    }
    value = pra_params \ "walks per path"
    if (!value.equals(JNothing)) {
      config.setWalksPerPath(value.extract[Int])
    }
    value = pra_params \ "path finding iterations"
    if (!value.equals(JNothing)) {
      config.setNumIters(value.extract[Int])
    }
    value = pra_params \ "number of paths to keep"
    if (!value.equals(JNothing)) {
      config.setNumPaths(value.extract[Int])
    }
    value = pra_params \ "binarize features"
    if (!value.equals(JNothing)) {
      config.setBinarizeFeatures(value.extract[Boolean])
    }
    value = pra_params \ "normalize walk probabilities"
    if (!value.equals(JNothing)) {
      config.setNormalizeWalkProbabilities(value.extract[Boolean])
    }
    value = pra_params \ "matrix accept policy"
    if (!value.equals(JNothing)) {
      config.setAcceptPolicy(MatrixRowPolicy.parseFromString(value.extract[String]))
    }
    value = pra_params \ "path accept policy"
    if (!value.equals(JNothing)) {
      config.setPathTypePolicy(PathTypePolicy.parseFromString(value.extract[String]))
    }
    value = pra_params \ "max matrix feature fan out"
    if (!value.equals(JNothing)) {
      config.setMaxMatrixFeatureFanOut(value.extract[Int])
    }
    value = pra_params \ "path type factory"
    if (!value.equals(JNothing)) {
      initializePathTypeFactory(value, config);
    }
    value = pra_params \ "path type selector"
    if (!value.equals(JNothing)) {
      initializePathTypeSelector(value, config);
    }
    value = pra_params \ "path follower"
    if (!value.equals(JNothing)) {
      initializePathFollowerFactory(value, config);
    }
  }

  def getGraphDirectory(params: JValue): String = {
    var value = params \ "graph"
    try {
      val name = value.extract[String]
      if (name.startsWith("/")) {
        return name
      } else {
        return baseDir + "/graphs/" + name
      }
    } catch {
      case e: MappingException => {
        val name = (value \ "name").extract[String]
        return baseDir + "/graphs/" + name
      }
    }
  }

  def initializeGraphParameters(graphDirectory: String, config: PraConfig.Builder) {
    val dir = fileUtil.addDirectorySeparatorIfNecessary(graphDirectory)
    config.setGraph(dir + "graph_chi" + java.io.File.separator + "edges.tsv");
    println(s"Loading node and edge dictionaries from graph directory: $dir");
    val numShards = fileUtil.readIntegerListFromFile(dir + "num_shards.tsv").get(0)
    config.setNumShards(numShards)
    val nodeDict = new Dictionary();
    nodeDict.setFromReader(fileUtil.getBufferedReader(dir + "node_dict.tsv"))
    config.setNodeDictionary(nodeDict);
    val edgeDict = new Dictionary();
    edgeDict.setFromReader(fileUtil.getBufferedReader(dir + "edge_dict.tsv"))
    config.setEdgeDictionary(edgeDict);
  }

  def initializePathTypeFactory(params: JValue, config: PraConfig.Builder) {
    val name = (params \ "name").extract[String]
    if (name.equals("VectorPathTypeFactory")) {
      initializeVectorPathTypeFactory(params, config)
    } else {
      throw new RuntimeException("Unrecognized path type factory")
    }
  }

  def initializeVectorPathTypeFactory(params: JValue, config: PraConfig.Builder) {
    println("Initializing vector path type factory")
    val spikiness = (params \ "spikiness").extract[Double]
    val resetWeight = (params \ "reset weight").extract[Double]
    println(s"RESET WEIGHT SET TO $resetWeight")
    val embeddingsFiles = (params \ "embeddings").extract[List[String]]
    val matrixDir = params \ "matrix dir"
    if (!matrixDir.equals(JNothing)) {
      config.setMatrixDir(matrixDir.extract[String])
    }
    val embeddings = config.readEmbeddingsVectors(embeddingsFiles)
    config.setPathTypeFactory(
      new VectorPathTypeFactory(config.edgeDict, embeddings, spikiness, resetWeight))
  }

  def initializePathTypeSelector(params: JValue, config: PraConfig.Builder) {
    val name = (params \ "name").extract[String]
    if (name.equals("VectorClusteringPathTypeSelector")) {
      initializeVectorClusteringPathTypeSelector(params, config)
    } else {
      throw new RuntimeException("Unrecognized path type selector")
    }
  }

  def initializeVectorClusteringPathTypeSelector(params: JValue, config: PraConfig.Builder) {
    println("Initializing VectorClusteringPathTypeSelector")
    val similarityThreshold = (params \ "similarity threshold").extract[Double]
    config.setPathTypeSelector( new VectorClusteringPathTypeSelector(
      config.pathTypeFactory.asInstanceOf[VectorPathTypeFactory],
      similarityThreshold))
  }

  def initializePathFollowerFactory(params: JValue, config: PraConfig.Builder) {
    val name = params.extract[String]
    if (name.equals("random walks")) {
      config.setPathFollowerFactory(new RandomWalkPathFollowerFactory());
    } else if (name.equals("matrix multiplication")) {
      config.setPathFollowerFactory(new MatrixPathFollowerFactory());
    } else {
      throw new RuntimeException("Unrecognized path follower")
    }
  }
}
