package edu.cmu.ml.rtw.pra.graphs

import org.scalatest._

import java.io.BufferedReader
import java.io.StringReader
import scala.collection.mutable
import scala.util.Random
import scalax.io.Resource

import breeze.linalg._

class PathMatrixCreatorSpec extends FlatSpecLike with Matchers {
  val numNodes = 1500000
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
    "4\t3\n"
  }
  val relation4Matrix = {
    val builder = new CSCMatrix.Builder[Int](numNodes, numNodes)
    builder.add(4, 1, 1)
    builder.add(4, 2, 1)
    builder.add(4, 3, 1)
    builder.result
  }

  "readMatricesFromFile" should "read a single-matrix file" in {
    val matrixFile = relation1File
    val matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(1))
    matrices.size should be (1)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
  }

  it should "read a single matrix from a multi-matrix file" in {
    var matrixFile = relation1File + relation2File
    var matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(1))
    matrices.size should be (1)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))

    matrixFile = relation1File + relation2File
    matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(2))
    matrices.size should be (1)
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))

    matrixFile = relation1File + relation2File + relation3File + relation4File
    matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(3))
    matrices.size should be (1)
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))
  }

  it should "read all matrices from a multi-matrix file" in {
    val matrixFile = relation1File + relation2File + relation3File
    val matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(1, 2, 3))
    matrices.size should be (3)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))
  }

  it should "read some matrices from a multi-matrix file" in {
    val matrixFile = relation1File + relation2File + relation3File + relation4File
    var matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(1, 2, 3))
    matrices.size should be (3)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))

    matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(1, 3))
    matrices.size should be (2)
    matrices(1).activeKeysIterator.toSet should be (Set((1, 1), (1, 2), (1, 3)))
    matrices(3).activeKeysIterator.toSet should be (Set((3, 1), (3, 2), (3, 3)))

    matrices = new PathMatrixCreator().readMatricesFromFile(
      numNodes,
      new BufferedReader(new StringReader(matrixFile)),
      Set(2, 4))
    matrices.size should be (2)
    matrices(2).activeKeysIterator.toSet should be (Set((2, 1), (2, 2), (2, 3)))
    matrices(4).activeKeysIterator.toSet should be (Set((4, 1), (4, 2), (4, 3)))
  }

  "separateRelationsByFile" should "correctly split relations by file" in {
    val result = new PathMatrixCreator().separateRelationsByFile(
      Set(1, 2, 3, 6, 7, 8),
      Set("1-2", "3", "4-7", "8-10"))
    result.size should be (4)
    result("1-2") should be (Set(1, 2))
    result("3") should be (Set(3))
    result("4-7") should be (Set(6, 7))
    result("8-10") should be (Set(8))
  }
}
