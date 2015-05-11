package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

class OutputterSpec extends FlatSpecLike with Matchers {
  val nodeDict = new Dictionary
  val edgeDict = new Dictionary
  val factory = new BasicPathTypeFactory
  val emptyOutputter = new Outputter(null, null, null, new FakeFileUtil)

  val rel1 = "rel1"
  val rel2 = "rel2"
  val rel1Index = edgeDict.getIndex(rel1)
  val rel2Index = edgeDict.getIndex(rel2)

  val node1 = "node1"
  val node2 = "node2"
  val node1Index = nodeDict.getIndex(node1)
  val node2Index = nodeDict.getIndex(node2)

  "getNode" should "return index with no dictionary" in {
    emptyOutputter.getNode(1) should be("1")
  }

  it should "return node string with dictionary" in {
    val outputter = new Outputter(nodeDict, edgeDict)
    outputter.getNode(node1Index) should be(node1)
  }

  it should "return node name when present" in {
    val nodeNames = Map(node1 -> "node1name")
    val outputter = new Outputter(nodeDict, edgeDict, nodeNames)
    outputter.getNode(node1Index) should be("node1name")
    outputter.getNode(node2Index) should be(node2)
  }

  "getPathType" should "return encoded string with no dictionary" in {
    val pathType = "-1-2-3-"
    emptyOutputter.getPathType(factory.fromString(pathType)) should be(pathType)
  }

  it should "return human readable string with edge dictionary" in {
    val pathType = "-1-2-"
    val outputter = new Outputter(nodeDict, edgeDict)
    outputter.getPathType(factory.fromString(pathType)) should be("-rel1-rel2-")
  }

  "outputWeights" should "sort weights and format them correctly" in {
    val weights = Seq(.2, .9)
    val featureNames = Seq("-1-", "-2-")
    val weightFile = "weight_file"
    val expectedWeightFileContents = "-2-\t0.9\n" + "-1-\t0.2\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(weightFile, expectedWeightFileContents)
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputWeights(weightFile, weights, featureNames)
    fileUtil.expectFilesWritten()
  }

  "outputPathCounts" should "sort counts and format them correctly" in {
    val baseDir = "/"
    val filename = "path counts file"
    val pathCounts = Map((factory.fromString("-1-") -> 1), (factory.fromString("-2-") -> 2))
    val pathCountFile = "/path counts file"
    val expectedPathCountFileContents = "-2-\t2\n-1-\t1\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(pathCountFile, expectedPathCountFileContents)
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputPathCounts(baseDir, filename, pathCounts)
    fileUtil.expectFilesWritten()
  }

  "outputScores" should "produce a correct scores file" in {
    val filename = "/scores file"
    val trainingData = new Dataset(Seq(Instance(1, 2, true), Instance(3, 8, true)))
    val testingData = new Dataset(Seq(Instance(3, 4, true), Instance(10, 11, true),
      Instance(5, 6, false), Instance(12, 13, false)))
    val config = new PraConfig.Builder()
        .noChecks()
        .setTrainingData(trainingData)
        .setTestingData(testingData)
        .build()

    val sourceScores = Map((3 -> Seq((4, .6), (8, .3), (7, .1))), (12 -> Seq((1, .1))))

    val scoresFile = "/scores file"
    val expectedScoresFileContents =
        "3\t4\t0.6\t*\n" +
        "3\t8\t0.3\t*^\n" +
        "3\t7\t0.1\t\n" +
        "\n" +
        "5\t\t\t-\n" +
        "\n" +
        "10\t\t\t\n" +
        "\n" +
        "12\t1\t0.1\t-\n" +
        "\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(scoresFile, expectedScoresFileContents)
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputScores(filename, sourceScores, config)
    fileUtil.expectFilesWritten()
  }

  "outputSplitFiles" should "write two correct files" in {
    val baseDir = "/"
    val trainingData = new Dataset(Seq(Instance(1, 4, true), Instance(2, 5, true),
      Instance(3, 6, true), Instance(9, 10, false)))
    val testingData = new Dataset(Seq(Instance(7, 8, true), Instance(1, 2, false)))

    val trainingFile = "/training_data.tsv"
    val expectedTrainingFileContents = "1\t4\t1\n2\t5\t1\n3\t6\t1\n9\t10\t-1\n"
    val testingFile = "/testing_data.tsv"
    val expectedTestingFileContents = "7\t8\t1\n1\t2\t-1\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(trainingFile, expectedTrainingFileContents)
    fileUtil.addExpectedFileWritten(testingFile, expectedTestingFileContents)
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputSplitFiles(baseDir, trainingData, testingData)
    fileUtil.expectFilesWritten()
  }

  "outputPathCountMap" should "format data correctly" in {
    val baseDir = "/"
    val filename = "path count map"
    val data = new Dataset(Seq(Instance(1, 2, true), Instance(3, 4, false)))

    val pathCountMap = Map(((1, 2) -> Map((factory.fromString("-1-"), 22))))

    val pathCountMapFile = "/path count map"
    val expectedPathCountMapFileContents =
        "1\t2\t+\n" +
        "\t-1-\t22\n" +
        "\n" +
        "3\t4\t-\n" +
        "\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(pathCountMapFile, expectedPathCountMapFileContents)
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputPathCountMap(baseDir, filename, pathCountMap, data)
    fileUtil.expectFilesWritten()
  }

  "outputPaths" should "format data correctly" in {
    val baseDir = "/"
    val filename = "path file"
    val pathTypes = Seq(factory.fromString("-1-"))

    val pathFile = "/path file"
    val expectedPathFileContents = "-1-\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(pathFile, expectedPathFileContents)
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputPaths(baseDir, filename, pathTypes)
    fileUtil.expectFilesWritten()
  }

  "outputFeatureMatrix" should "format the matrix correctly" in {
    val filename = "/matrix file"
    val rows = Seq(new MatrixRow(1, 2, Array(0, 1), Array(0.1, 0.2))).asJava
    val featureNames = Seq("-1-", "-2-")

    val matrixFile = "/matrix file"
    val expectedMatrixFileContents = "1,2\t-1-,0.1 -#- -2-,0.2\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(matrixFile, expectedMatrixFileContents)
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputFeatureMatrix(filename, new FeatureMatrix(rows), featureNames)
    fileUtil.expectFilesWritten()
  }

  "output methods" should "do nothing with null base dir" in {
    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    val outputter = new Outputter(null, null, null, fileUtil)
    outputter.outputSplitFiles(null, null, null)
    outputter.outputPathCounts(null, null, null)
    outputter.outputPathCountMap(null, null, null, null)
    outputter.outputPaths(null, null, null)
    fileUtil.expectFilesWritten()
  }
}
