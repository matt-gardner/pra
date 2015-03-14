package edu.cmu.ml.rtw.pra.features

import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector

import java.io.BufferedReader
import java.lang.Integer
import java.util.{List => JList}
import java.util.{Map => JMap}
import java.util.{Set => JSet}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.parallel.mutable.{ParSeq => MParSeq}
import scalax.io.Resource
import scala.util.control.Breaks._

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

class RescalPathMatrixCreator(
    numNodes: Int,
    java_path_types: JList[PathType],
    java_source_nodes: JSet[Integer],
    rescal_dir: String,
    node_dict: Dictionary,
    edge_dict: Dictionary,
    java_edges_to_remove: JList[Pair[Pair[Integer, Integer], Integer]],
    fileUtil: FileUtil = new FileUtil) {

  // First we convert the java inputs that we got into scala objects.
  val path_types = java_path_types.asScala
  val source_nodes = java_source_nodes.asScala.map(_.toInt)
  val edges_to_remove = java_edges_to_remove.asScala
    .map(x => (x.getRight.asInstanceOf[Int],
      (x.getLeft.getLeft.asInstanceOf[Int], x.getLeft.getRight.asInstanceOf[Int])))
    .groupBy(_._1).mapValues(_.map(_._2)).withDefaultValue(Nil)

  // Now we build up a few data structures that we'll need.  First are the node vectors.
  val node_vectors = fileUtil.readLinesFromFile(rescal_dir + "/a_matrix.tsv").asScala.map(line => {
    val fields = line.split("\t")
    val node_index = node_dict.getIndex(fields(0))
    val vector_entries = fields.drop(0).map(_.toDouble)
    (node_index -> new DenseVector(vector_entries))
  }).toMap
  val rank = node_vectors(1).length

  // These first two methods (and the constructor) get called from java code, so they need to take
  // java collections as inputs.  The rest of the code should use scala collections.

  /**
   * If you know which (source, target) pairs you want to compute features for, this is a faster
   * way to go about it.  However, when creating the initial feature matrix, and when making
   * blanket predictions for a source (as is my typical prediction method in my KB inference
   * experiments), you need to actually look at _all_ of the targets connected to by any path type.
   * That's what the next method is for.
   */
  def getFeatureMatrix(pairs: JList[Pair[Integer, Integer]]): FeatureMatrix = {
    getFeatureMatrix(pairs.asScala.map(x => (x.getLeft.toInt, x.getRight.toInt)))
  }

  /**
   * This gets a complete feature matrix row for every target connected by any selected path to the
   * given source nodes.  Then we do a vector-matrix multiplication for each source, and keep all
   * targets in the resultant vector that are also in allowed_targets.
   */
  def getFeatureMatrix(sources: JSet[Integer], allowed_targets: JSet[Integer]): FeatureMatrix = {
    if (allowed_targets != null) {
      getFeatureMatrix(sources.asScala.map(_.asInstanceOf[Int]).toSet,
        allowed_targets.asScala.map(_.asInstanceOf[Int]).toSet)
    } else {
      throw new IllegalStateException("RescalPathMatrixCreator currently requires a set of " +
        "allowed targets for this method")
    }
  }

  val path_matrices: Map[PathType, DenseMatrix[Double]] = {
    println(s"Creating path matrices from the relation matrices in $rescal_dir")
    val _path_types = path_types.toList.asInstanceOf[List[BaseEdgeSequencePathType]]
    val relations = _path_types.flatMap(_.getEdgeTypes).toSet
    val filename = rescal_dir + "/r_matrix.tsv"
    val rescal_matrices: Map[Int, DenseMatrix[Double]] = {
      val lines = fileUtil.readLinesFromFile(filename).asScala
      val matrices_with_lines = splitMatrixLines(lines)
      matrices_with_lines.par.map(matrix_lines => {
        (edge_dict.getIndex(matrix_lines._1), createDenseMatrixFromLines(matrix_lines._2))
      }).seq.toMap
    }

    _path_types.par.map(x => (x, createPathMatrix(x, rescal_matrices))).seq.toMap
  }

  def splitMatrixLines(lines: Seq[String]): Seq[(String, Seq[String])] = {
    val matrices = new mutable.ListBuffer[(String, Seq[String])]
    var current_relation: String = null
    var matrix_lines: mutable.ListBuffer[String] = null
    for (line <- lines) {
      if (current_relation == null) {
        current_relation = line
        matrix_lines = new mutable.ListBuffer[String]
      }
      if (line.isEmpty) {
        matrices += Tuple2(current_relation, matrix_lines.toSeq)
        current_relation = null
      }
      else {
        matrix_lines += line
      }
    }
    matrices.toSeq
  }

  def createDenseMatrixFromLines(lines: Seq[String]): DenseMatrix[Double] = {
    val matrix = new DenseMatrix[Double](rank, rank)
    for ((line, i) <- lines.zipWithIndex) {
      for ((value, j) <- line.split("\t").zipWithIndex) {
        matrix(i, j) = value.toDouble
      }
    }
    matrix
  }

  def createPathMatrix(
      path_type: BaseEdgeSequencePathType,
      connectivity_matrices: Map[Int, DenseMatrix[Double]]): DenseMatrix[Double] = {
    val str = path_type.encodeAsHumanReadableString(edge_dict)
    var result = connectivity_matrices(path_type.getEdgeTypes()(0))
    if (path_type.getReverse()(0)) {
      result = result.t
    }
    for (i <- 1 until path_type.getEdgeTypes().length) {
      val relation_matrix = connectivity_matrices(path_type.getEdgeTypes()(i))
      if (path_type.getReverse()(i)) {
        result = result * relation_matrix.t
      } else {
        result = result * relation_matrix
      }
    }
    println(s"Done, ${path_type.getEdgeTypes().length} steps, ${result.activeSize} entries, $str")
    result
  }

  def createNodeMatrix(nodes: Seq[Int], transpose: Boolean) = {
    val m = {
      if (transpose) DenseMatrix.zeros[Double](rank, nodes.size)
      else DenseMatrix.zeros[Double](nodes.size, rank)
    }
    for ((node, index) <- nodes.zipWithIndex) {
      if (transpose) {
        m(::, index) := node_vectors(node)
      } else {
        m(index, ::) := node_vectors(node).t
      }
    }
    m
  }

  def getFeatureMatrix(pairs: Seq[(Int, Int)]): FeatureMatrix = {
    val sources = pairs.map(_._1).toSet.toList.sorted
    val targets = pairs.map(_._2).toSet.toList.sorted
    val source_indices = pairs.map(_._1).toSet.map((id: Int) => (id, sources.indexOf(id))).toMap
    val target_indices = pairs.map(_._2).toSet.map((id: Int) => (id, targets.indexOf(id))).toMap
    val source_matrix = createNodeMatrix(sources, false)
    val target_matrix = createNodeMatrix(targets, true)
    getFeatureMatrixFromSourceAndTargetMatrices(pairs, source_matrix, target_matrix,
      source_indices, target_indices)
  }

  def getFeatureMatrix(sources: Set[Int], allowed_targets: Set[Int]): FeatureMatrix = {
    println("Getting feature matrix for input sources");
    val sources_list = sources.toList.sorted
    val targets_list = allowed_targets.toList.sorted
    val source_indices = sources.map(id  => (id, sources_list.indexOf(id))).toMap
    val target_indices = allowed_targets.map(id => (id, targets_list.indexOf(id))).toMap
    val source_matrix = createNodeMatrix(sources_list, false)
    val target_matrix = createNodeMatrix(targets_list, true)
    val pairs = (for (source <- sources; target <- allowed_targets) yield (source, target)).toSeq
    getFeatureMatrixFromSourceAndTargetMatrices(pairs, source_matrix, target_matrix,
      source_indices, target_indices)
  }

  def getFeatureMatrixFromSourceAndTargetMatrices(
      pairs: Seq[(Int, Int)],
      source_matrix: DenseMatrix[Double],
      target_matrix: DenseMatrix[Double],
      source_indices: Map[Int, Int],
      target_indices: Map[Int, Int]) = {
    val matrix_rows = path_types.zipWithIndex.par.flatMap(path_type => {
      val feature_matrix = source_matrix * path_matrices(path_type._1) * target_matrix
      pairs.map(pair => {
        val s = source_indices(pair._1)
        val t = target_indices(pair._2)
        val value = feature_matrix(s, t)
        (pair, (path_type._2, value))
      }).filter(_._2._2 > 0)
    }).groupBy(_._1).toMap.mapValues(_.map(_._2).seq)
      .map(e => createMatrixRow(e._1._1, e._1._2, e._2)).seq.toList
    new FeatureMatrix(matrix_rows.asJava)
  }

  def createMatrixRow(source: Int, target: Int, feature_list: Seq[(Int, Double)]): MatrixRow = {
    val pathTypes = new mutable.ArrayBuffer[Int]
    val values = new mutable.ArrayBuffer[Double]
    for (feature <- feature_list) {
      pathTypes += feature._1
      values += feature._2
    }
    new MatrixRow(source, target, pathTypes.toArray, values.toArray)
  }
}
