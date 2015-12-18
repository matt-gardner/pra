package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

class OutputterSpec extends FlatSpecLike with Matchers {
  val nodeDict = new Dictionary
  val edgeDict = new Dictionary
  val factory = new BasicPathTypeFactory
  val emptyOutputter = new Outputter(null, new FakeFileUtil)

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

  "getNode" should "return node string" in {
    emptyOutputter.getNode(1, graph) should be("node1")
  }

  it should "return node name when present" in {
    val nodeNames = Map("node1" -> "node1name")
    val outputter = new Outputter(nodeNames)
    outputter.getNode(1, graph) should be("node1name")
    outputter.getNode(2, graph) should be("node2")
  }

  "getPathType" should "return human readable string" in {
    val pathType = "-1-2-"
    emptyOutputter.getPathType(factory.fromString(pathType), graph) should be("-rel1-rel2-")
  }

  "outputWeights" should "sort weights and format them correctly" in {
    val weights = Seq(.2, .9)
    val featureNames = Seq("-1-", "-2-")
    val weightFile = "weight_file"
    val expectedWeightFileContents = "-2-\t0.9\n" + "-1-\t0.2\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(weightFile, expectedWeightFileContents)
    val outputter = new Outputter(null, fileUtil)
    outputter.outputWeights(weightFile, weights, featureNames)
    fileUtil.expectFilesWritten()
  }

  // TODO(matt): I need to add a test here that makes sure the output path counts use the right
  // edge dictionary, because currently the found_path_counts.tsv file does not use the edge dict
  // correctly.
  "outputPathCounts" should "sort counts and format them correctly" in {
    val baseDir = "/"
    val filename = "path counts file"
    val pathCounts = Map((factory.fromString("-1-") -> 1), (factory.fromString("-2-") -> 2))
    val pathCountFile = "/path counts file"
    val expectedPathCountFileContents = "-2-\t2\n-1-\t1\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(pathCountFile, expectedPathCountFileContents)
    val outputter = new Outputter(null, fileUtil)
    outputter.outputPathCounts(baseDir, filename, pathCounts)
    fileUtil.expectFilesWritten()
  }

  "outputScores" should "produce a correct scores file" in {
    val filename = "/scores file"
    val instance1 = new NodePairInstance(3, 7, false, graph)
    val instance2 = new NodePairInstance(3, 8, true, graph)
    val instance3 = new NodePairInstance(3, 4, true, graph)
    val instance4 = new NodePairInstance(1, 2, false, graph)
    val trainingData = new Dataset[NodePairInstance](Seq(instance2))
    val testingData = new Dataset[NodePairInstance](Seq(instance1, instance3, instance4))

    val scores = Seq((instance1, .1), (instance3, .6), (instance2, .3), (instance4, .1))

    val scoresFile = "/scores file"
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
    val outputter = new Outputter(null, fileUtil)
    outputter.outputScores(filename, scores, trainingData)
    fileUtil.expectFilesWritten()
  }

  "outputSplitFiles" should "write two correct files" in {
    val baseDir = "/"
    val trainingData = new Dataset[NodePairInstance](Seq(
      new NodePairInstance(1, 4, true, graph),
      new NodePairInstance(2, 5, true, graph),
      new NodePairInstance(3, 6, true, graph),
      new NodePairInstance(9, 10, false, graph)
    ))
    val testingData = new Dataset[NodePairInstance](Seq(
      new NodePairInstance(7, 8, true, graph),
      new NodePairInstance(1, 2, false, graph)
    ))

    val trainingFile = "/training_data.tsv"
    val expectedTrainingFileContents =
      "node1\tnode4\t1\nnode2\tnode5\t1\nnode3\tnode6\t1\nnode9\tnode10\t-1\n"
    val testingFile = "/testing_data.tsv"
    val expectedTestingFileContents = "node7\tnode8\t1\nnode1\tnode2\t-1\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(trainingFile, expectedTrainingFileContents)
    fileUtil.addExpectedFileWritten(testingFile, expectedTestingFileContents)
    val outputter = new Outputter(null, fileUtil)
    outputter.outputSplitFiles(baseDir, trainingData, testingData)
    fileUtil.expectFilesWritten()
  }

  "outputPathCountMap" should "format data correctly" in {
    val baseDir = "/"
    val filename = "path count map"
    val instance1 = new NodePairInstance(1, 2, true, graph)
    val instance2 = new NodePairInstance(3, 4, false, graph)
    val data = new Dataset[NodePairInstance](Seq(instance1, instance2))

    val pathCountMap = Map((instance1 -> Map((factory.fromString("-1-"), 22))))

    val pathCountMapFile = "/path count map"
    val expectedPathCountMapFileContents =
        "node1\tnode2\t+\n" +
        "\t-rel1-\t22\n" +
        "\n" +
        "node3\tnode4\t-\n" +
        "\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(pathCountMapFile, expectedPathCountMapFileContents)
    val outputter = new Outputter(null, fileUtil)
    outputter.outputPathCountMap(baseDir, filename, pathCountMap, data)
    fileUtil.expectFilesWritten()
  }

  "outputPaths" should "format data correctly" in {
    val baseDir = "/"
    val filename = "path file"
    val pathTypes = Seq(factory.fromString("-1-"))

    val pathFile = "/path file"
    val expectedPathFileContents = "-rel1-\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(pathFile, expectedPathFileContents)
    val outputter = new Outputter(null, fileUtil)
    outputter.outputPaths(baseDir, filename, pathTypes, graph)
    fileUtil.expectFilesWritten()
  }

  "outputFeatureMatrix" should "format the matrix correctly" in {
    val filename = "/matrix file"
    val instance = new NodePairInstance(1, 2, true, graph)
    val rows = Seq(new MatrixRow(instance, Array(0, 1), Array(0.1, 0.2))).asJava
    val featureNames = Seq("-1-", "-2-")

    val matrixFile = "/matrix file"
    val expectedMatrixFileContents = "node1,node2\t-1-,0.1 -#- -2-,0.2\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(matrixFile, expectedMatrixFileContents)
    val outputter = new Outputter(null, fileUtil)
    outputter.outputFeatureMatrix(filename, new FeatureMatrix(rows), featureNames)
    fileUtil.expectFilesWritten()
  }

  "output methods" should "do nothing with null base dir" in {
    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    val outputter = new Outputter(null, fileUtil)
    outputter.outputSplitFiles(null, null, null)
    outputter.outputPathCounts(null, null, null)
    outputter.outputPathCountMap(null, null, null, null)
    outputter.outputPaths(null, null, null, null)
    fileUtil.expectFilesWritten()
  }
}
