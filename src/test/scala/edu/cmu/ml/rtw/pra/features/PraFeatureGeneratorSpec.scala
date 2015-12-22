package edu.cmu.ml.rtw.pra.features

import java.io.BufferedReader
import java.io.StringReader

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

class PraFeatureGeneratorSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger
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
  val relation = "fake relation"
  val relationMetadata = RelationMetadata.empty
  val unallowedEdges = List(1, 3, 2)
  val graph = new GraphOnDisk("src/test/resources/", outputter)

  val node1 = "node1"
  val node2 = "node2"
  val node3 = "node3"
  val node4 = "node4"
  val node1Index = graph.nodeDict.getIndex(node1)
  val node2Index = graph.nodeDict.getIndex(node2)
  val node3Index = graph.nodeDict.getIndex(node3)
  val node4Index = graph.nodeDict.getIndex(node4)
  val dataFile = node1 + "\t" + node2 + "\n" + node3 + "\t" + node4 + "\n"
  val data = new Dataset[NodePairInstance](Seq(
    new NodePairInstance(node1Index, node2Index, true, graph),
    new NodePairInstance(node3Index, node4Index, true, graph)
  ))

  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead("/path/to/r/a_matrix.tsv", "node1\t1\t2\t3\n")
  val generator = new PraFeatureGenerator(params, graph, relation, relationMetadata, outputter, fileUtil)

  // TODO(matt): this method should move to a PathFollower object, after PathFollower is moved from
  // java to scala.
  "createPathFollower" should "create random walk path follower" in {
    val follower = generator.createPathFollower(followerParams, Seq(path1, path2), data, true)
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
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data, true)
    follower.getClass should be(classOf[MatrixPathFollower])
    val matrixFollower = follower.asInstanceOf[MatrixPathFollower]
    matrixFollower.maxFanOut should be(2)
    matrixFollower.normalizeWalkProbabilities should be(false)
    matrixFollower.matrixDir should be("src/test/resources/m/")
  }

  it should "normalize the matrix directory" in {
    val matrixParams =
      ("name" -> "matrix multiplication") ~
      ("max fan out" -> 2) ~
      ("matrix dir" -> "m/") ~
      ("normalize walk probabilities" -> false)
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data, true)
    val matrixFollower = follower.asInstanceOf[MatrixPathFollower]
    matrixFollower.matrixDir should be("src/test/resources/m/")
  }

  it should "create a rescal matrix path follower" in {
    val matrixParams =
      ("name" -> "rescal matrix multiplication") ~
      ("rescal dir" -> "/path/to/r/") ~
      ("negatives per source" -> 23)
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data, true)
    follower.getClass should be(classOf[RescalMatrixPathFollower])
    val rescalFollower = follower.asInstanceOf[RescalMatrixPathFollower]
    rescalFollower.negativesPerSource should be(23)
    rescalFollower.rescalDir should be("/path/to/r/")
  }

  it should "normalize the rescal directory" in {
    val matrixParams =
      ("name" -> "rescal matrix multiplication") ~
      ("rescal dir" -> "/path/to/r")
    val follower = generator.createPathFollower(matrixParams, Seq(path1, path2), data, true)
    val rescalFollower = follower.asInstanceOf[RescalMatrixPathFollower]
    rescalFollower.rescalDir should be("/path/to/r/")
  }

  it should "throw error with unrecognized path follower" in {
    val badParams: JValue = ("name" -> "bad")
    TestUtil.expectError(classOf[IllegalStateException], "Unrecognized path follower", new Function() {
      def call() {
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data, true)
      }
    })
  }

  it should "throw error with extra parameters" in {
    TestUtil.expectError(classOf[IllegalStateException], "path follower: unexpected key", new Function() {
      def call() {
        val badParams: JValue = ("name" -> "random walks") ~ ("fake" -> "bad")
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data, true)
      }
    })
    TestUtil.expectError(classOf[IllegalStateException], "path follower: unexpected key", new Function() {
      def call() {
        val badParams: JValue = ("name" -> "matrix multiplication") ~ ("fake" -> "bad")
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data, true)
      }
    })
    TestUtil.expectError(classOf[IllegalStateException], "path follower: unexpected key", new Function() {
      def call() {
        val badParams: JValue = ("name" -> "rescal matrix multiplication") ~ ("fake" -> "bad")
        val follower = generator.createPathFollower(badParams, Seq(path1, path2), data, true)
      }
    })
  }
}
