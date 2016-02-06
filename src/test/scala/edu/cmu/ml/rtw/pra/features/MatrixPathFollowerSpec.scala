package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import java.io.BufferedReader
import java.io.StringReader
import java.lang.Integer
import java.util.{List => JList}
import java.util.{Set => JSet}

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.util.Random

import breeze.linalg._
import com.google.common.collect.Lists
import com.google.common.collect.Sets

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

class MatrixPathFollowerSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger
  val numNodes = 100
  val path_type_factory = new BasicPathTypeFactory(null)
  val relation1File = "/relation 1"
  val relation1FileContents = {
    "Relation 1\n" +
    "1\t1\n" +
    "1\t2\n" +
    "1\t3\n"
  }
  val relation1Matrix = {
    val builder = new CSCMatrix.Builder[Double](numNodes, numNodes)
    builder.add(1, 1, 1)
    builder.add(1, 2, 1)
    builder.add(1, 3, 1)
    builder.result
  }

  val relation2File = "/relation 2"
  val relation2FileContents = {
    "Relation 2\n" +
    "2\t1\n" +
    "2\t2\n" +
    "2\t3\n"
  }
  val relation2Matrix = {
    val builder = new CSCMatrix.Builder[Double](numNodes, numNodes)
    builder.add(2, 1, 1)
    builder.add(2, 2, 1)
    builder.add(2, 3, 1)
    builder.result
  }

  val relation3File = "/relation 3"
  val relation3FileContents = {
    "Relation 3\n" +
    "3\t1\n" +
    "3\t2\n" +
    "3\t3\n"
  }
  val relation3Matrix = {
    val builder = new CSCMatrix.Builder[Double](numNodes, numNodes)
    builder.add(3, 1, 1)
    builder.add(3, 2, 1)
    builder.add(3, 3, 1)
    builder.result
  }

  val relation4File = "/relation 4"
  val relation4FileContents = {
    "Relation 4\n" +
    "4\t1\n" +
    "4\t2\n" +
    "4\t3\n" +
    "4\t4\n" +
    "4\t5\n" +
    "4\t6\n"
  }
  val relation4Matrix = {
    val builder = new CSCMatrix.Builder[Double](numNodes, numNodes)
    builder.add(4, 1, 1)
    builder.add(4, 2, 1)
    builder.add(4, 3, 1)
    builder.add(4, 4, 1)
    builder.add(4, 5, 1)
    builder.add(4, 6, 1)
    builder.result
  }

  val relation5File = "/relation 5"
  val relation5FileContents = {
    "Relation 5\n" +
    "5\t1\n" +
    "5\t1\n" +
    "5\t1\n" +
    "5\t2\n"
  }
  val relation5Matrix = {
    val builder = new CSCMatrix.Builder[Double](numNodes, numNodes)
    builder.add(5, 1, 1)
    builder.add(5, 2, 1)
    builder.result
  }

  val relation1And2File = "/relation 1 and 2"
  val relation1And2FileContents = relation1FileContents + relation2FileContents
  val relation1Through3File = "/relation 1 through 3"
  val relation1Through3FileContents =
    relation1FileContents + relation2FileContents + relation3FileContents
  val relation1Through4File = "/relation 1 through 4"
  val relation1Through4FileContents =
    relation1FileContents + relation2FileContents +
    relation3FileContents + relation4FileContents

  val connectivity_matrices = {
    val matrices = new mutable.HashMap[Int, CSCMatrix[Double]]
    matrices(1) = relation1Matrix
    matrices(2) = relation2Matrix
    matrices(3) = relation3Matrix
    matrices(4) = relation4Matrix
    matrices.toMap
  }

  def checkMatrixRow(matrix_row: MatrixRow, expectedFeatures: Map[Int, Double]) {
    matrix_row.featureTypes.toSet should be (expectedFeatures.keySet)
    for (feature <- matrix_row.featureTypes zip matrix_row.values) {
      feature._2 should be (expectedFeatures(feature._1))
    }
  }

  def getSourceTargetRow(featureMatrix: FeatureMatrix, source: Int, target: Int): MatrixRow = {
    for (row <- featureMatrix.getRows.asScala) {
      val instance = row.instance.asInstanceOf[NodePairInstance]
      if (instance.source == source && instance.target == target) return row
    }
    return null
  }

  lazy val creatorWithPathTypes = {
    val fileUtil = new FakeFileUtil()
    val matrixFile =
      relation1FileContents + relation2FileContents + relation3FileContents + relation4FileContents
    fileUtil.addFileToBeRead("/matrices/1-4", matrixFile)
    fileUtil.addFileToBeRead("/graph/node_dict.tsv", "1\t1\n")
    fileUtil.addFileToBeRead("/graph/edge_dict.tsv", "1\t1\n2\t2\n3\t3\n4\t4\n")
    fileUtil.addFileToBeRead(relation1File, relation1FileContents)
    fileUtil.addFileToBeRead(relation2File, relation2FileContents)
    fileUtil.addFileToBeRead(relation3File, relation3FileContents)
    fileUtil.addFileToBeRead(relation4File, relation4FileContents)
    fileUtil.addFileToBeRead(relation5File, relation5FileContents)
    fileUtil.addFileToBeRead(relation1And2File, relation1And2FileContents)
    fileUtil.addFileToBeRead(relation1Through3File, relation1Through3FileContents)
    fileUtil.addFileToBeRead(relation1Through4File, relation1Through4FileContents)
    val edgesToExclude = Seq[((Int, Int), Int)](((4, 2), 4), ((1, 4), 4))
    val graph = new GraphOnDisk("/graph/", outputter, fileUtil)
    val path_types = Seq(
      path_type_factory.fromString("-1-"),
      path_type_factory.fromString("-1-2-"),
      path_type_factory.fromString("-1-_1-")
      )
    new MatrixPathFollower(
      numNodes,
      path_types,
      outputter,
      "/matrices/",
      new Dataset[NodePairInstance](Seq(
        new NodePairInstance(1, 1, true, graph),
        new NodePairInstance(2, 1, true, graph),
        new NodePairInstance(3, 1, true, graph)
      )),
      Set(),
      new SingleEdgeExcluder(edgesToExclude),
      3,
      false,
      fileUtil)
  }

  "getFeatureMatrixRow" should "return complete matrix rows" in {
    val path_matrices = creatorWithPathTypes.getPathMatrices()
    var matrix_row = creatorWithPathTypes.getFeatureMatrixRow(path_matrices, 1, 1)
    checkMatrixRow(matrix_row, Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)

    matrix_row = creatorWithPathTypes.getFeatureMatrixRow(path_matrices, 1, 2)
    checkMatrixRow(matrix_row, Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)

    matrix_row = creatorWithPathTypes.getFeatureMatrixRow(path_matrices, 1, 3)
    checkMatrixRow(matrix_row, Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)

    matrix_row = creatorWithPathTypes.getFeatureMatrixRow(path_matrices, 2, 2)
    checkMatrixRow(matrix_row, Seq((0, 0.0), (1, 0.0), (2, 0.0)).toMap)
  }

  "getFeatureMatrix" should "get the right matrix rows for a source" in {
    val feature_matrix = creatorWithPathTypes.getFeatureMatrix(Set(1), Set(1, 3, 4, 5))
    feature_matrix.size should be (2)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 3), Seq((0, 1.0), (1, 1.0)).toMap)
  }

  it should "get complete matrix rows for a list of pairs" in {
    val feature_matrix = creatorWithPathTypes.getFeatureMatrix(Seq((1, 1), (1, 2), (1, 3), (2, 2)))
    feature_matrix.size should be (4)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 2), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 3), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 2, 2), Seq((0, 0.0), (1, 0.0), (2, 0.0)).toMap)
  }

  it should "interface correctly with java" in {
    var feature_matrix = creatorWithPathTypes.getFeatureMatrix(
      Sets.newHashSet(1:Integer),
      Sets.newHashSet(1:Integer, 2:Integer, 3:Integer))
    feature_matrix.size should be (3)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 2), Seq((0, 1.0), (1, 1.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 3), Seq((0, 1.0), (1, 1.0)).toMap)

    feature_matrix = creatorWithPathTypes.getFeatureMatrix(Lists.newArrayList(
        Pair.makePair(1:Integer, 1:Integer),
        Pair.makePair(1:Integer, 2:Integer),
        Pair.makePair(1:Integer, 3:Integer)))
    feature_matrix.size should be (3)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 2), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
    checkMatrixRow(getSourceTargetRow(feature_matrix, 1, 3), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
  }

  "readMatricesFromFile" should "read a single-matrix file" in {
    val matrices = creatorWithPathTypes.readMatricesFromFile(relation1File, Set(1))
    matrices.size should be (1)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
  }

  it should "read a single matrix from a multi-matrix file" in {
    var matrices = creatorWithPathTypes.readMatricesFromFile(relation1And2File, Set(1))
    matrices.size should be (1)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))

    matrices = creatorWithPathTypes.readMatricesFromFile(relation1And2File, Set(2))
    matrices.size should be (1)
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))

    matrices = creatorWithPathTypes.readMatricesFromFile(relation1Through4File, Set(3))
    matrices.size should be (1)
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))
  }

  it should "read all matrices from a multi-matrix file" in {
    val matrices = creatorWithPathTypes.readMatricesFromFile(relation1Through3File, Set(1, 2, 3))
    matrices.size should be (3)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))
  }

  it should "read some matrices from a multi-matrix file" in {
    var matrices = creatorWithPathTypes.readMatricesFromFile(relation1Through4File, Set(1, 2, 3))
    matrices.size should be (3)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))

    matrices = creatorWithPathTypes.readMatricesFromFile(relation1Through4File, Set(1, 3))
    matrices.size should be (2)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))

    matrices = creatorWithPathTypes.readMatricesFromFile(relation1Through4File, Set(2, 4))
    matrices.size should be (2)
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(4).activeKeysIterator.toSet should be (Set((4, 3), (4, 4), (4, 5), (4,6)))
  }

  it should "remove training edges from connectivity matrices" in {
    val matrices = creatorWithPathTypes.readMatricesFromFile(relation4File, Set(4))
    matrices(4)(4, 1) should be (0)
    matrices(4)(4, 2) should be (0)
    matrices(4)(4, 3) should be (1)
  }

  it should "have entries higher than 1 when relation instances are repeated" in {
    val matrices = creatorWithPathTypes.readMatricesFromFile(relation5File, Set(5))
    matrices(5)(5, 1) should be (3)
    matrices(5)(5, 2) should be (1)
  }

  "separateRelationsByFile" should "correctly split relations by file" in {
    val result = creatorWithPathTypes.separateRelationsByFile(
      Set(1, 2, 3, 6, 7, 8),
      Set("1-2", "3", "4-7", "8-10"))
    result.size should be (4)
    result("1-2") should be (Set(1, 2))
    result("3") should be (Set(3))
    result("4-7") should be (Set(6, 7))
    result("8-10") should be (Set(8))
  }

  "createPathMatrix" should "create a simple 2-hop path matrix" in {
    val path_type = path_type_factory.fromString("-1-2-").asInstanceOf[BaseEdgeSequencePathType]
    val result = creatorWithPathTypes.createPathMatrix(path_type, connectivity_matrices)
    result.activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    result((1, 1)) should be (1)
    result((1, 2)) should be (1)
    result((1, 3)) should be (1)
  }

  it should "return the first matrix in a path type of length 1" in {
    val path_type = path_type_factory.fromString("-1-").asInstanceOf[BaseEdgeSequencePathType]
    val result = creatorWithPathTypes.createPathMatrix(path_type, connectivity_matrices)
    result.activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    result((1, 1)) should be (1)
    result((1, 2)) should be (1)
    result((1, 3)) should be (1)
  }

  it should "handle inverses correctly" in {
    val path_type = path_type_factory.fromString("-1-_1-").asInstanceOf[BaseEdgeSequencePathType]
    val result = creatorWithPathTypes.createPathMatrix(path_type, connectivity_matrices)
    result.activeKeysIterator.toSet should be (Set((1, 1)))
    result((1, 1)) should be (3)
  }

  it should "handle initial inverses correctly" in {
    val path_type = path_type_factory.fromString("-_1-1-").asInstanceOf[BaseEdgeSequencePathType]
    val result = creatorWithPathTypes.createPathMatrix(path_type, connectivity_matrices)
    result.activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3), (2, 1), (2, 2), (2, 3),
      (3, 1), (3, 2), (3, 3)))
    result((1, 1)) should be (1)
    result((1, 2)) should be (1)
    result((1, 3)) should be (1)
    result((2, 1)) should be (1)
    result((2, 2)) should be (1)
    result((2, 3)) should be (1)
    result((3, 1)) should be (1)
    result((3, 2)) should be (1)
    result((3, 3)) should be (1)
  }

  it should "remove path matrices with fan outs that are too high" in {
    // Actual fan out from this path type is 6; the max set above is 3, so this should be cleared
    // to 0.
    val path_type = path_type_factory.fromString("-_4-4-").asInstanceOf[BaseEdgeSequencePathType]
    val result = creatorWithPathTypes.createPathMatrix(path_type, connectivity_matrices)
    result.activeSize should be (0)
  }
}
