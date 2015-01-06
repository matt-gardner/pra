package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy
import edu.cmu.ml.rtw.pra.features.MatrixPathFollowerFactory
import edu.cmu.ml.rtw.pra.features.PathTypePolicy
import edu.cmu.ml.rtw.pra.features.RandomWalkPathFollowerFactory
import edu.cmu.ml.rtw.pra.features.VectorClusteringPathTypeSelector
import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConversions._

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

class SpecFileReader(fileUtil: FileUtil = new FileUtil()) {
  implicit val formats = DefaultFormats

  def readSpecFile(filename: String): JValue = {
    val params = new JObject(Nil)
    val specs = fileUtil.readLinesFromFile(filename)
    populateParamsFromSpecs(specs, params)
  }

  def populateParamsFromSpecs(specs: Seq[String], params: JValue): JValue = {
    if (specs(0).startsWith("load")) {
      return readSpecFile(specs(0).split(" ")(1)) merge populateParamsFromSpecs(specs.drop(1), params)
    } else {
      params merge parse(specs.mkString(" "))
    }
  }

  def setPraConfigFromParams(params: JValue, config: PraConfig.Builder) {
    var value = params \ "l1 weight"
    if (!value.equals(JNothing)) {
      config.setL1Weight(value.extract[Double])
    }
    value = params \ "l2 weight"
    if (!value.equals(JNothing)) {
      config.setL2Weight(value.extract[Double])
    }
    value = params \ "walks per source"
    if (!value.equals(JNothing)) {
      config.setWalksPerSource(value.extract[Int])
    }
    value = params \ "walks per path"
    if (!value.equals(JNothing)) {
      config.setWalksPerPath(value.extract[Int])
    }
    value = params \ "path finding iterations"
    if (!value.equals(JNothing)) {
      config.setNumIters(value.extract[Int])
    }
    value = params \ "number of paths to keep"
    if (!value.equals(JNothing)) {
      config.setNumPaths(value.extract[Int])
    }
    value = params \ "binarize features"
    if (!value.equals(JNothing)) {
      config.setBinarizeFeatures(value.extract[Boolean])
    }
    value = params \ "normalize walk probabilities"
    if (!value.equals(JNothing)) {
      config.setNormalizeWalkProbabilities(value.extract[Boolean])
    }
    value = params \ "matrix accept policy"
    if (!value.equals(JNothing)) {
      config.setAcceptPolicy(MatrixRowPolicy.parseFromString(value.extract[String]))
    }
    value = params \ "path accept policy"
    if (!value.equals(JNothing)) {
      config.setPathTypePolicy(PathTypePolicy.parseFromString(value.extract[String]))
    }
    value = params \ "max matrix feature fan out"
    if (!value.equals(JNothing)) {
      config.setMaxMatrixFeatureFanOut(value.extract[Int])
    }
    value = params \ "path type factory"
    if (!value.equals(JNothing)) {
      initializePathTypeFactory(value, config);
    }
    value = params \ "path type selector"
    if (!value.equals(JNothing)) {
      initializePathTypeSelector(value, config);
    }
    value = params \ "path follower"
    if (!value.equals(JNothing)) {
      initializePathFollowerFactory(value, config);
    }
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
