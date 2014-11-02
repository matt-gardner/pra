package edu.cmu.ml.rtw.pra.graphs

import breeze.linalg.CSCMatrix

import java.io.BufferedReader

import scala.collection.JavaConversions._
import scala.collection.mutable
import scalax.io.Resource
import scala.util.control.Breaks._

import edu.cmu.ml.rtw.pra.features.BaseEdgeSequencePathType
import edu.cmu.ml.rtw.users.matt.util.FileUtil

class PathMatrixCreator(fileUtil: FileUtil = new FileUtil) {
  def getPathMatrices(
      num_nodes: Int,
      path_types: Seq[BaseEdgeSequencePathType],
      graph_dir: String): Map[BaseEdgeSequencePathType, CSCMatrix[Int]] = {
    val relations = path_types.flatMap(_.getEdgeTypes).toSet
    val filenames = fileUtil.listDirectoryContents(graph_dir + "matrices/").toSet
    val relations_by_filename = separateRelationsByFile(relations, filenames)
    val connectivity_matrices: Map[Int, CSCMatrix[Int]] = relations_by_filename.par.flatMap(x =>
        readMatricesFromFile(num_nodes, fileUtil.getBufferedReader(x._1), x._2)).seq

    path_types.par.map(x => (x, createPathMatrix(x, connectivity_matrices))).seq.toMap
  }

  def createPathMatrix(
      path_type: BaseEdgeSequencePathType,
      connectivity_matrices: Map[Int, CSCMatrix[Int]]): CSCMatrix[Int] = {
    var result = connectivity_matrices(path_type.getEdgeTypes()(0))
    if (path_type.getReverse()(0)) {
      result = result.t
    }
    for (i <- 1 to path_type.getEdgeTypes().length) {
      val relation_matrix = connectivity_matrices(path_type.getEdgeTypes()(i))
      if (path_type.getReverse()(i)) {
        result *= relation_matrix.t
      } else {
        result *= relation_matrix
      }
    }
    result
  }

  def separateRelationsByFile(relations: Set[Int], filenames: Set[String]): Map[String, Set[Int]] = {
    val relation_partition = new mutable.HashMap[String, Set[Int]].withDefaultValue(Set[Int]())
    val ranges = filenames.map(x => (getRangeFromFilename(x), x)).toList.sorted

    var current_file_index = 0
    for (relation <- relations.toList.sorted) {
      while (relation > ranges(current_file_index)._1._2) current_file_index += 1
      val filename = ranges(current_file_index)._2
      relation_partition.update(filename, relation_partition(filename) + relation)
    }
    relation_partition.toMap
  }

  def getRangeFromFilename(filename: String): (Int, Int) = {
    if (filename.contains("-")) {
      val fields = filename.split("-")
      (fields(0).toInt, fields(1).toInt)
    } else {
      (filename.toInt, filename.toInt)
    }
  }

  def readMatricesFromFile(
      numNodes: Int,
      reader: BufferedReader,
      relations: Set[Int]): Map[Int, CSCMatrix[Int]] = {
    val matrices = new mutable.HashMap[Int, CSCMatrix[Int]]
    val sorted_relations = relations.toSeq.sorted
    var relation_index = 0
    var current_relation = -1
    var builder: CSCMatrix.Builder[Int] = null
    breakable {
      for (line <- Resource.fromReader(reader).lines()) {
        if (line.startsWith("Relation")) {
          if (builder != null) {
            matrices(sorted_relations(relation_index)) = builder.result
            relation_index += 1
            builder = null
            if (relation_index >= sorted_relations.size) break
          }
          if (line.split(" ").last.toInt == sorted_relations(relation_index)) {
            builder = new CSCMatrix.Builder[Int](numNodes, numNodes)
          }
        } else if (builder != null) {
          val fields = line.split("\t")
          val source = fields(0).toInt
          val target = fields(1).toInt
          builder.add(source, target, 1)
        }
      }
    }
    if (builder != null) {
      matrices(sorted_relations(relation_index)) = builder.result
    }
    matrices.toMap
  }
}
