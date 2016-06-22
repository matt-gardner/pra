package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.JsonDSL._
import org.scalatest._

class OutputterSpec extends FlatSpecLike with Matchers {
  val nodeDict = new MutableConcurrentDictionary
  val edgeDict = new MutableConcurrentDictionary
  val emptyOutputter = new Outputter(JNothing, "/dev/null", "no method")

  val graph = {
    val fileUtil = new FakeFileUtil
    val nodeDictFile = "1\tnode1\n" +
      "2\tnode2\n" +
      "3\tnode3\n" +
      "4\tnode4\n" +
      "5\tnode5\n" +
      "6\tnode6\n" +
      "7\tnode7\n" +
      "8\tnode8\n" +
      "9\tnode9\n" +
      "10\tnode10\n"
    val edgeDictFile = "1\trel1\n" +
      "2\trel2\n" +
      "3\trel3\n" +
      "4\trel4\n" +
      "5\trel5\n" +
      "6\trel6\n" +
      "7\trel7\n" +
      "8\trel8\n"
    fileUtil.addFileToBeRead("/graph/node_dict.tsv", nodeDictFile)
    fileUtil.addFileToBeRead("/graph/edge_dict.tsv", edgeDictFile)
    new GraphOnDisk("/graph/", fileUtil)
  }
  val factory = new BasicPathTypeFactory(graph)

  "getNode" should "return node string" in {
    emptyOutputter.getNode(1, graph) should be("node1")
  }

  it should "return node name when present" in {
    val fileUtil = new FakeFileUtil
    val nodeNamesFile = "/node/names/file"
    val nodeNamesContents = "node1\tnode1name\n"
    fileUtil.addFileToBeRead(nodeNamesFile, nodeNamesContents)

    val params: JValue = ("node names" -> nodeNamesFile)
    val outputter = new Outputter(params, "/", "fake name", fileUtil)
    outputter.getNode(1, graph) should be("node1name")
    outputter.getNode(2, graph) should be("node2")
  }

  "getPathType" should "return human readable string" in {
    val pathType = "-1-2-"
    emptyOutputter.getPathType(factory.fromString(pathType), graph) should be("-rel1-rel2-")
  }

  "outputScores" should "produce a correct scores file" in {
    val instance1 = new NodePairInstance(3, 7, false, graph)
    val instance2 = new NodePairInstance(3, 8, true, graph)
    val instance3 = new NodePairInstance(3, 4, true, graph)
    val instance4 = new NodePairInstance(1, 2, false, graph)
    val trainingData = new Dataset[NodePairInstance](Seq(instance2))
    val testingData = new Dataset[NodePairInstance](Seq(instance1, instance3, instance4))

    val scores = Seq((instance1, .1), (instance3, .6), (instance2, .3), (instance4, .1))

    val scoresFile = "/results/fake name/fake relation/scores.tsv"
    val expectedScoresFileContents =
        "node1\tnode2\t0.1\t\n" +
        "\n" +
        "node3\tnode4\t0.6\t*\n" +
        "node3\tnode8\t0.3\t*^\n" +
        "node3\tnode7\t0.1\t\n" +
        "\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(scoresFile, expectedScoresFileContents)
    val outputter = new Outputter(JNothing, "/", "fake name", fileUtil)
    outputter.setRelation("fake relation")
    outputter.outputScores(scores, trainingData)
    fileUtil.expectFilesWritten()
  }

  "outputDataset" should "write a correct file" in {
    val trainingData = new Dataset[NodePairInstance](Seq(
      new NodePairInstance(1, 4, true, graph),
      new NodePairInstance(2, 5, true, graph),
      new NodePairInstance(3, 6, true, graph),
      new NodePairInstance(9, 10, false, graph)
    ))
    val trainingFile = "/training_data.tsv"
    val expectedTrainingFileContents =
      "node1\tnode4\t1\nnode2\tnode5\t1\nnode3\tnode6\t1\nnode9\tnode10\t-1\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(trainingFile, expectedTrainingFileContents)
    val outputter = new Outputter(JNothing, "/", "fake name", fileUtil)
    outputter.outputDataset(trainingFile, trainingData)
    fileUtil.expectFilesWritten()
  }
}
