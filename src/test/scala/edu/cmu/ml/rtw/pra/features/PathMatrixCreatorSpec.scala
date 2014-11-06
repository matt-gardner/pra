package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import java.io.BufferedReader
import java.io.StringReader
import java.lang.Integer
import java.util.{List => JList}
import java.util.{Set => JSet}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.Random
import scalax.io.Resource

import breeze.linalg._
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

class PathMatrixCreatorSpec extends FlatSpecLike with Matchers {
  val numNodes = 100
  val path_type_factory = new BasicPathTypeFactory()
  val relation1File = {
    "Relation 1\n" +
    "1\t1\n" +
    "1\t2\n" +
    "1\t3\n"
  }
  val relation1Matrix = {
    val builder = new CSCMatrix.Builder[Int](numNodes, numNodes)
    builder.add(1, 1, 1)
    builder.add(1, 2, 1)
    builder.add(1, 3, 1)
    builder.result
  }

  val relation2File = {
    "Relation 2\n" +
    "2\t1\n" +
    "2\t2\n" +
    "2\t3\n"
  }
  val relation2Matrix = {
    val builder = new CSCMatrix.Builder[Int](numNodes, numNodes)
    builder.add(2, 1, 1)
    builder.add(2, 2, 1)
    builder.add(2, 3, 1)
    builder.result
  }

  val relation3File = {
    "Relation 3\n" +
    "3\t1\n" +
    "3\t2\n" +
    "3\t3\n"
  }
  val relation3Matrix = {
    val builder = new CSCMatrix.Builder[Int](numNodes, numNodes)
    builder.add(3, 1, 1)
    builder.add(3, 2, 1)
    builder.add(3, 3, 1)
    builder.result
  }

  val relation4File = {
    "Relation 4\n" +
    "4\t1\n" +
    "4\t2\n" +
    "4\t3\n" +
    "4\t4\n" +
    "4\t5\n" +
    "4\t6\n"
  }
  val relation4Matrix = {
    val builder = new CSCMatrix.Builder[Int](numNodes, numNodes)
    builder.add(4, 1, 1)
    builder.add(4, 2, 1)
    builder.add(4, 3, 1)
    builder.add(4, 4, 1)
    builder.add(4, 5, 1)
    builder.add(4, 6, 1)
    builder.result
  }

  val connectivity_matrices = {
    val matrices = new mutable.HashMap[Int, CSCMatrix[Int]]
    matrices(1) = relation1Matrix
    matrices(2) = relation2Matrix
    matrices(3) = relation3Matrix
    matrices(4) = relation4Matrix
    matrices.toMap
  }

  def checkMatrixRow(matrix_row: MatrixRow, expectedFeatures: Map[Int, Double]) {
    matrix_row.pathTypes.toSet should be (expectedFeatures.keySet)
    for (feature <- matrix_row.pathTypes zip matrix_row.values) {
      feature._2 should be (expectedFeatures(feature._1))
    }
  }

  lazy val creatorWithPathTypes = {
    val fileUtil = new FakeFileUtil()
    val matrixFile = relation1File + relation2File + relation3File + relation4File
    fileUtil.addFileToBeRead("/matrices/1-4", matrixFile)
    val edgesToExclude: JList[Pair[Pair[Integer, Integer], Integer]] = Lists.newArrayList();
    val edgeDict = new Dictionary();
    edgeDict.getIndex("1");
    edgeDict.getIndex("2");
    edgeDict.getIndex("3");
    edgeDict.getIndex("4");
    edgesToExclude.add(Pair.makePair(Pair.makePair(4, 2), 4))
    val path_types = seqAsJavaList(Seq(
      path_type_factory.fromString("-1-"),
      path_type_factory.fromString("-1-2-"),
      path_type_factory.fromString("-1-_1-")
      ))
    new PathMatrixCreator(
      numNodes,
      path_types,
      Sets.newHashSet(1, 2, 3),
      "/",
      edgeDict,
      edgesToExclude,
      3,
      fileUtil)
  }

  "getFeatureMatrixRow" should "return complete matrix rows" in {
    var matrix_row = creatorWithPathTypes.getFeatureMatrixRow(1, 1)
    checkMatrixRow(matrix_row, Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)

    matrix_row = creatorWithPathTypes.getFeatureMatrixRow(1, 2)
    checkMatrixRow(matrix_row, Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)

    matrix_row = creatorWithPathTypes.getFeatureMatrixRow(1, 3)
    checkMatrixRow(matrix_row, Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)

    matrix_row = creatorWithPathTypes.getFeatureMatrixRow(2, 2)
    checkMatrixRow(matrix_row, Seq((0, 0.0), (1, 0.0), (2, 0.0)).toMap)
  }

  "getFeatureMatrix" should "get the right matrix rows for a source" in {
    val feature_matrix = creatorWithPathTypes.getFeatureMatrix(Set(1), Set(1, 3, 4, 5))
    feature_matrix.size should be (2)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 3), Seq((0, 1.0), (1, 1.0)).toMap)
  }

  it should "get complete matrix rows for a list of pairs" in {
    val feature_matrix = creatorWithPathTypes.getFeatureMatrix(Seq((1, 1), (1, 2), (1, 3), (2, 2)))
    feature_matrix.size should be (4)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 2), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 3), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(2, 2), Seq((0, 0.0), (1, 0.0), (2, 0.0)).toMap)
  }

  it should "interface correctly with java" in {
    var feature_matrix = creatorWithPathTypes.getFeatureMatrix(
      Sets.newHashSet(1:Integer),
      Sets.newHashSet(1:Integer, 2:Integer, 3:Integer))
    feature_matrix.size should be (3)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 2), Seq((0, 1.0), (1, 1.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 3), Seq((0, 1.0), (1, 1.0)).toMap)

    feature_matrix = creatorWithPathTypes.getFeatureMatrix(Lists.newArrayList(
        Pair.makePair(1:Integer, 1:Integer),
        Pair.makePair(1:Integer, 2:Integer),
        Pair.makePair(1:Integer, 3:Integer)))
    feature_matrix.size should be (3)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 1), Seq((0, 1.0), (1, 1.0), (2, 3.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 2), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
    checkMatrixRow(feature_matrix.getSourceTargetRow(1, 3), Seq((0, 1.0), (1, 1.0), (2, 0.0)).toMap)
  }

  "readMatricesFromFile" should "read a single-matrix file" in {
    val matrixFile = relation1File
    val matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(1))
    matrices.size should be (1)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
  }

  it should "read a single matrix from a multi-matrix file" in {
    var matrixFile = relation1File + relation2File
    var matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(1))
    matrices.size should be (1)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))

    matrixFile = relation1File + relation2File
    matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(2))
    matrices.size should be (1)
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))

    matrixFile = relation1File + relation2File + relation3File + relation4File
    matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(3))
    matrices.size should be (1)
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))
  }

  it should "read all matrices from a multi-matrix file" in {
    val matrixFile = relation1File + relation2File + relation3File
    val matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(1, 2, 3))
    matrices.size should be (3)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))
  }

  it should "read some matrices from a multi-matrix file" in {
    val matrixFile = relation1File + relation2File + relation3File + relation4File
    var matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(1, 2, 3))
    matrices.size should be (3)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))

    matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(1, 3))
    matrices.size should be (2)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))

    matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(2, 4))
    matrices.size should be (2)
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(4).activeKeysIterator.toSet should be (Set((4, 1), (4, 2), (4, 3), (4, 4), (4, 5), (4,6)))
  }

  it should "remove training edges from connectivity matrices" in {
    val matrixFile = relation4File
    var matrices = creatorWithPathTypes.readMatricesFromFile(
      new BufferedReader(new StringReader(matrixFile)),
      Set(4))
    matrices(4)(4, 1) should be (1)
    matrices(4)(4, 2) should be (0)
    matrices(4)(4, 3) should be (1)
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
