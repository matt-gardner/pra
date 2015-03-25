package edu.cmu.ml.rtw.pra.features

import java.io.BufferedReader
import java.io.StringReader

import org.scalatest._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.DatasetFactory
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

class FeatureGeneratorSpec extends FlatSpecLike with Matchers {
  val factory = new FakePathTypeFactory()
  val finderParams: JValue =
    ("walks per source" -> 123) ~
    ("path accept policy" -> "everything") ~
    ("path type factory" ->
      ("name" -> "VectorPathTypeFactory") ~
      ("spikiness" -> 1.23) ~
      ("reset weight" -> 0.12) ~
      ("embeddings" -> List()))
    ("path finding iterations" -> 27)
  val followerParams: JValue =
    ("name" -> "random walks") ~
    ("walks per path" -> 246) ~
    ("matrix accept policy" -> "everything") ~
    ("normalize walk probabilities" -> false)
  val params: JValue =
    ("path finder" -> finderParams) ~
    ("path follower" -> followerParams)

  val path1 = factory.fromString("-1-2-3-")
  val path2 = factory.fromString("-1-2-3- INVERSE")
  val pathCounts = Map(path1 -> 2, path2 -> 2)
  val inverses = Map(1 -> 2)
  val pair = (1, 1)

  val node1 = "node1"
  val node2 = "node2"
  val node3 = "node3"
  val node4 = "node4"
  val nodeDict = new Dictionary()
  val node1Index = nodeDict.getIndex(node1)
  val node2Index = nodeDict.getIndex(node2)
  val node3Index = nodeDict.getIndex(node3)
  val node4Index = nodeDict.getIndex(node4)
  val unallowedEdges = List(1, 3, 2).map(x => java.lang.Integer.valueOf(x)).asJava
  val dataFile = node1 + "\t" + node2 + "\n" + node3 + "\t" + node4 + "\n"
  val data = new DatasetFactory().fromReader(new BufferedReader(new StringReader(dataFile)), new Dictionary())
  val graphFile = "src/test/resources/edges.tsv"
  val config = new PraConfig.Builder().noChecks()
    .setGraph(graphFile).setNumShards(1).setUnallowedEdges(unallowedEdges).build()
  val generator = new FeatureGenerator(params, "/", config)

  "collapseInverses" should "collapse inverses" in {
    val collapsed = generator.collapseInverses(pathCounts, inverses, factory)
    collapsed.size should be(1)
    collapsed(path2) should be(4)
  }

  "collapseInversesInCountMap" should "collapse inverses in count map" in {
    val collapsed = generator.collapseInversesInCountMap(Map(pair -> pathCounts), inverses, factory)
    collapsed.size should be(1)
    collapsed(pair)(path2) should be(4)
  }

  "createEdgesToExclude" should "handle the basic case" in {
    val edgesToExclude = generator.createEdgesToExclude(data)
    edgesToExclude.size should be(6)
    edgesToExclude.count(_ == ((node1Index, node2Index), 1)) should be(1)
    edgesToExclude.count(_ == ((node1Index, node2Index), 3)) should be(1)
    edgesToExclude.count(_ == ((node1Index, node2Index), 2)) should be(1)
    edgesToExclude.count(_ == ((node3Index, node4Index), 1)) should be(1)
    edgesToExclude.count(_ == ((node3Index, node4Index), 3)) should be(1)
    edgesToExclude.count(_ == ((node3Index, node4Index), 2)) should be(1)
  }

  it should "work with a null dataset" in {
    val edgesToExclude = generator.createEdgesToExclude(null)
    edgesToExclude.size should be(0)
  }

  it should "work with a dataset with no targets" in {
    val nodes = List(Integer.valueOf(node1Index), Integer.valueOf(node3Index)).asJava
    val data = new Dataset.Builder().setPositiveSources(nodes).build()
    val edgesToExclude = generator.createEdgesToExclude(data)
    edgesToExclude.size should be(0)
  }

  "createPathFollower" should "create random walk path follower" in {
    val follower = generator.createPathFollower(followerParams, Seq(path1, path2), data)
    follower.getClass should be(classOf[RandomWalkPathFollower])
    val rwFollower = follower.asInstanceOf[RandomWalkPathFollower]
    rwFollower.getWalksPerPath should be(246)
    val companion = rwFollower.getCompanion
    companion.getAcceptPolicy should be(MatrixRowPolicy.EVERYTHING)
    companion.getNormalizeWalks should be(false)
  }

  it should "create a matrix path follower" in {
    val matrixParams =
      ("name" -> "matrix multiplication") ~
      ("max fan out" -> 2) ~
      ("matrix dir" -> "m") ~
      ("normalize walk probabilities" -> false)
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data)
    follower.getClass should be(classOf[MatrixPathFollower])
    val matrixFollower = follower.asInstanceOf[MatrixPathFollower]
    matrixFollower.getMaxFanOut should be(2)
    matrixFollower.getNormalizeWalks should be(false)
    matrixFollower.getMatrixDir should be("src/test/resources/m/")
  }

  it should "normalize the matrix directory" in {
    val matrixParams =
      ("name" -> "matrix multiplication") ~
      ("max fan out" -> 2) ~
      ("matrix dir" -> "m/") ~
      ("normalize walk probabilities" -> false)
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data)
    val matrixFollower = follower.asInstanceOf[MatrixPathFollower]
    matrixFollower.getMatrixDir should be("src/test/resources/m/")
  }

  it should "create a rescal matrix path follower" in {
    val matrixParams =
      ("name" -> "rescal matrix multiplication") ~
      ("rescal dir" -> "/path/to/r/") ~
      ("negatives per source" -> 23)
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data)
    follower.getClass should be(classOf[RescalMatrixPathFollower])
    val rescalFollower = follower.asInstanceOf[RescalMatrixPathFollower]
    rescalFollower.getNegativesPerSource should be(23)
    rescalFollower.getRescalDir should be("/path/to/r/")
  }

  it should "normalize the rescal directory" in {
    val matrixParams =
      ("name" -> "rescal matrix multiplication") ~
      ("rescal dir" -> "/path/to/r")
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data)
    val rescalFollower = follower.asInstanceOf[RescalMatrixPathFollower]
    rescalFollower.getRescalDir should be("/path/to/r/")
  }

  it should "throw error with unrecognized path follower" in {
    val badParams: JValue = ("name" -> "bad")
    TestUtil.expectError(classOf[IllegalStateException], "Unrecognized path follower", new Function() {
      def call() {
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data)
      }
    })
  }

  it should "throw error with extra parameters" in {
    TestUtil.expectError(classOf[IllegalStateException], "path follower: unexpected key", new Function() {
      def call() {
        val badParams: JValue = ("name" -> "random walks") ~ ("fake" -> "bad")
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data)
      }
    })
    TestUtil.expectError(classOf[IllegalStateException], "path follower: unexpected key", new Function() {
      def call() {
        val badParams: JValue = ("name" -> "matrix multiplication") ~ ("fake" -> "bad")
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data)
      }
    })
    TestUtil.expectError(classOf[IllegalStateException], "path follower: unexpected key", new Function() {
      def call() {
        val badParams: JValue = ("name" -> "rescal matrix multiplication") ~ ("fake" -> "bad")
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data)
      }
    })
  }
}
