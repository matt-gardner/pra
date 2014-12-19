package edu.cmu.ml.rtw.pra.features

import breeze.linalg.CSCMatrix
import breeze.linalg.SparseVector
import breeze.linalg.VectorBuilder

import java.io.BufferedReader
import java.lang.Integer
import java.util.{List => JList}
import java.util.{Map => JMap}
import java.util.{Set => JSet}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.parallel.mutable.{ParSeq => MParSeq}
import scalax.io.Resource
import scala.util.control.Breaks._

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

class PathMatrixCreator(
    numNodes: Int,
    parent_path_types: JList[PathType],
    source_nodes: JSet[Integer],
    graph_dir: String,
    edge_dict: Dictionary,
    edgesToExclude: JList[Pair[Pair[Integer, Integer], Integer]],
    maxFanOut: Int,
    fileUtil: FileUtil = new FileUtil) {

  val sources_matrix = {
    val builder = new CSCMatrix.Builder[Int](numNodes, numNodes, source_nodes.size)
    for (source <- source_nodes) {
      builder.add(source, source, 1)
    }
    builder.result
  }

  val edges_to_remove = edgesToExclude
    .map(x => (x.getRight.asInstanceOf[Int],
      (x.getLeft.getLeft.asInstanceOf[Int], x.getLeft.getRight.asInstanceOf[Int])))
    .groupBy(_._1).mapValues(_.map(_._2)).withDefaultValue(Nil)

  // Unfortunately, it seems like the best way to move forward for the moment is to keep the main
  // code path going through java, and just integrate with new scala code this way.  For the java
  // code to compile right, though, it looks like I need to use java collections and integers here
  // in the method signatures, so this is uglier than it could be.  Oh well.  I tried to localize
  // all of the JList/JSet stuff to just the constructor and the first two methods, which just
  // translate the java objects into scala objects, used by the real implementations below.

  /**
   * If you know which (source, target) pairs you want to compute features for, this is a faster
   * way to go about it.  However, when creating the initial feature matrix, and when making
   * blanket predictions for a source (as is my typical prediction method in my KB inference
   * experiments), you need to actually look at _all_ of the targets connected to by any path type.
   * That's what the next method is for.
   */
  def getFeatureMatrix(pairs: JList[Pair[Integer, Integer]]): FeatureMatrix = {
    getFeatureMatrix(pairs.map(x => (x.getLeft.asInstanceOf[Int], x.getRight.asInstanceOf[Int])))
  }

  /**
   * This gets a complete feature matrix row for every target connected by any selected path to the
   * given source nodes.  Then we do a vector-matrix multiplication for each source, and keep all
   * targets in the resultant vector that are also in allowed_targets.
   */
  def getFeatureMatrix(sources: JSet[Integer], allowed_targets: JSet[Integer]): FeatureMatrix = {
    getFeatureMatrix(sources.map(_.asInstanceOf[Int]).toSet,
      allowed_targets.map(_.asInstanceOf[Int]).toSet)
  }

  // NO JAVA INTERFACING CODE BELOW HERE!

  def getFeatureMatrix(pairs: Seq[(Int, Int)]): FeatureMatrix = {
    val matrix_rows = pairs.par.map(x => getFeatureMatrixRow(x._1, x._2)).seq.toSeq
    new FeatureMatrix(matrix_rows)
  }

  def getFeatureMatrix(sources: Set[Int], allowed_targets: Set[Int]): FeatureMatrix = {
    println("Getting feature matrix for input sources");
    val matrix_rows = sources.par.flatMap(
      x => getFeatureMatrixRowsForSource(x, allowed_targets.map(_.asInstanceOf[Int]).toSet)
      ).seq.toSeq
    new FeatureMatrix(matrix_rows)
  }

  def getFeatureMatrixRowsForSource(source: Int, allowed_targets: Set[Int]): Seq[MatrixRow] = {
    val source_vector = {
      val builder = new VectorBuilder[Int](numNodes, 1)
      builder.add(source, 1)
      builder.toSparseVector
    }
    val target_feature_map = parent_path_types.zipWithIndex.par.flatMap(path_type_with_index => {
      // Breeze can't handle multiplication by transposes of sparse vectors.  But we can transpose
      // the sparse matrix, and multiply on the other side.
      val targets = path_matrices(path_type_with_index._1).t * source_vector
      val seen_features = new mutable.ListBuffer[(Int, Int, Int)]
      var offset = 0
      while(offset < targets.activeSize) {
        val target = targets.indexAt(offset)
        val value = targets.valueAt(offset)
        if (allowed_targets.contains(target)) {
          seen_features += Tuple3(target, path_type_with_index._2, value)
        }
        offset += 1
      }
      seen_features.toList
    })
    target_feature_map.groupBy(_._1).map(target_with_features => {
      val feature_list = target_with_features._2.map(y => (y._2, y._3)).toList.sorted
      createMatrixRow(source, target_with_features._1, feature_list)
    }).seq.toSeq
  }

  def getFeatureMatrixRow(source: Int, target: Int): MatrixRow = {
    val feature_list = parent_path_types.zipWithIndex.par.map(
      x => (x._2, path_matrices(x._1)(source, target))).seq.toList.sorted
    createMatrixRow(source, target, feature_list)
  }

  def createMatrixRow(source: Int, target: Int, feature_list: Seq[(Int, Int)]): MatrixRow = {
    val pathTypes = new mutable.ArrayBuffer[Int]
    val values = new mutable.ArrayBuffer[Double]
    for (feature <- feature_list) {
      pathTypes += feature._1
      values += feature._2
    }
    new MatrixRow(source, target, pathTypes.toArray, values.toArray)
  }

  val path_matrices: Map[PathType, CSCMatrix[Int]] = {
    println("Creating path matrices")
    val path_types = parent_path_types.toList.asInstanceOf[List[BaseEdgeSequencePathType]]
    val relations = path_types.flatMap(_.getEdgeTypes).toSet
    val matrix_dir = graph_dir + "matrices/"
    val filenames = fileUtil.listDirectoryContents(matrix_dir).toSet
    val relations_by_filename = separateRelationsByFile(relations, filenames)
    val connectivity_matrices: Map[Int, CSCMatrix[Int]] = relations_by_filename.par.flatMap(x =>
        readMatricesFromFile(fileUtil.getBufferedReader(matrix_dir + x._1), x._2)).seq

    path_types.par.map(x => (x, createPathMatrix(x, connectivity_matrices))).seq.toMap
  }

  def createPathMatrix(
      path_type: BaseEdgeSequencePathType,
      connectivity_matrices: Map[Int, CSCMatrix[Int]]): CSCMatrix[Int] = {
    val str = path_type.encodeAsHumanReadableString(edge_dict)
    var result = connectivity_matrices(path_type.getEdgeTypes()(0))
    if (path_type.getReverse()(0)) {
      result = result.t
    }
    result = sources_matrix * result
    for (i <- 1 until path_type.getEdgeTypes().length) {
      val relation_matrix = connectivity_matrices(path_type.getEdgeTypes()(i))
      if (path_type.getReverse()(i)) {
        result = result * relation_matrix.t
      } else {
        result = result * relation_matrix
      }
    }
    println(s"Done, ${path_type.getEdgeTypes().length} steps, ${result.activeSize} entries, $str")
    if (result.activeSize / sources_matrix.activeSize > maxFanOut) {
      new CSCMatrix.Builder[Int](numNodes, numNodes, 0).result
    } else {
      result
    }
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
      reader: BufferedReader,
      relations: Set[Int]): Map[Int, CSCMatrix[Int]] = {
    val matrices = new mutable.HashMap[Int, CSCMatrix[Int]]
    val sorted_relations = relations.toSeq.sorted
    var relation_index = 0
    var current_relation = sorted_relations(relation_index)
    var builder: CSCMatrix.Builder[Int] = null
    breakable {
      for (line <- Resource.fromReader(reader).lines()) {
        if (line.startsWith("Relation")) {
          if (builder != null) {
            matrices(current_relation) = builder.result
            relation_index += 1
            builder = null
            if (relation_index >= sorted_relations.size) break
            current_relation = sorted_relations(relation_index)
          }
          if (line.split(" ").last.toInt == current_relation) {
            builder = new CSCMatrix.Builder[Int](numNodes, numNodes)
          }
        } else if (builder != null) {
          val fields = line.split("\t")
          val source = fields(0).toInt
          val target = fields(1).toInt
          if (!edges_to_remove(current_relation).contains((source, target))) {
            builder.add(source, target, 1)
          }
        }
      }
    }
    if (builder != null) {
      matrices(current_relation) = builder.result
    }
    matrices.toMap
  }

  def removeEdgesAndBuild(builder: CSCMatrix.Builder[Int], relation: Int) = {
    builder.result
  }
}
