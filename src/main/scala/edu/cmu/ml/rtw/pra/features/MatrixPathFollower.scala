package edu.cmu.ml.rtw.pra.features

import breeze.linalg.CSCMatrix
import breeze.linalg.SparseVector
import breeze.linalg.VectorBuilder

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

import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

// Some of these are vals so that they can be accessed in tests.  That's because they are things
// that are specified from params.  TODO(matt): change this to take a JValue as input for those
// params.
class MatrixPathFollower(
    numNodes: Int,
    pathTypes: Seq[PathType],
    val matrixDir: String,
    data: Dataset,
    edge_dict: Dictionary,
    allowedTargets: Set[Int],
    edgeExcluder: EdgeExcluder,
    val maxFanOut: Int,
    val normalizeWalkProbabilities: Boolean,
    fileUtil: FileUtil = new FileUtil) extends PathFollower {

  val source_nodes = data.instances.map(_.source).toSet
  val positive_source_targets = data.getPositiveInstances.map(i => (i.source, i.target)).toSet
  val graph = data.instances(0).graph
  override def execute = {}
  override def shutDown = {}
  override def usesGraphChi = false
  override def getFeatureMatrix() = getFeatureMatrix(source_nodes, allowedTargets)

  val sources_matrix = {
    println(s"num nodes: ${numNodes}")
    val builder = new CSCMatrix.Builder[Double](numNodes, numNodes, source_nodes.size)
    for (source <- source_nodes) {
      println(s"source: $source")
      builder.add(source, source, 1)
    }
    builder.result
  }

  val edges_to_remove = edgeExcluder.asInstanceOf[SingleEdgeExcluder].edgesToExclude
    .map(x => (x._2, (x._1))).groupBy(_._1).mapValues(_.map(_._2)).withDefaultValue(Nil)

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
    getFeatureMatrix(pairs.asScala.map(x => (x.getLeft.asInstanceOf[Int], x.getRight.asInstanceOf[Int])))
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
      getFeatureMatrix(sources.asScala.map(_.asInstanceOf[Int]).toSet, null)
    }
  }

  def getFeatureMatrix(pairs: Seq[(Int, Int)]): FeatureMatrix = {
    val path_matrices = getPathMatrices()
    val matrix_rows = pairs.par.map(x => getFeatureMatrixRow(path_matrices, x._1, x._2)).seq.toSeq
    new FeatureMatrix(matrix_rows.asJava)
  }

  def getFeatureMatrix(sources: Set[Int], allowed_targets: Set[Int]): FeatureMatrix = {
    val path_matrices = getPathMatrices()
    println("Getting feature matrix for input sources")
    val entries = pathTypes.zipWithIndex.par.flatMap(path_type_with_index => {
      for (instance <- path_matrices(path_type_with_index._1).activeIterator
           if sources.contains(instance._1._1);
           if allowed_targets == null || allowed_targets.contains(instance._1._2))
        yield (instance._1, path_type_with_index._2, instance._2)
    })
    val matrix_rows = entries.groupBy(_._1).map(entity_pair_with_features => {
      val feature_list = entity_pair_with_features._2.map(y => (y._2, y._3)).toList.sorted
      createMatrixRow(entity_pair_with_features._1._1, entity_pair_with_features._1._2, feature_list)
    }).seq.toSeq

    new FeatureMatrix(matrix_rows.asJava)
  }

  def getFeatureMatrixRow(path_matrices: Map[PathType, CSCMatrix[Double]], source: Int, target: Int) = {
    val feature_list = pathTypes.zipWithIndex.par.map(
      x => (x._2, path_matrices(x._1)(source, target))).seq.toList.sorted
    createMatrixRow(source, target, feature_list)
  }

  def createMatrixRow(source: Int, target: Int, feature_list: Seq[(Int, Double)]): MatrixRow = {
    val pathTypes = new mutable.ArrayBuffer[Int]
    val values = new mutable.ArrayBuffer[Double]
    for (feature <- feature_list) {
      pathTypes += feature._1
      values += feature._2
    }
    val instance = if (positive_source_targets.contains((source, target))) {
      new Instance(source, target, true, graph)
    } else {
      new Instance(source, target, false, graph)
    }
    new MatrixRow(instance, pathTypes.toArray, values.toArray)
  }

  def getPathMatrices(): Map[PathType, CSCMatrix[Double]] = {
    println(s"Creating path matrices from the relation matrices in $matrixDir")
    val _path_types = pathTypes.toList.asInstanceOf[List[BaseEdgeSequencePathType]]
    val relations = _path_types.flatMap(_.getEdgeTypes).toSet
    val filenames = fileUtil.listDirectoryContents(matrixDir).asScala.toSet - "params.json"
    if (filenames.size == 0) {
      throw new RuntimeException(s"Didn't find any matrix files in ${matrixDir}...")
    }
    val relations_by_filename = separateRelationsByFile(relations, filenames)
    val connectivity_matrices: Map[Int, CSCMatrix[Double]] = relations_by_filename.par.flatMap(x =>
        readMatricesFromFile(fileUtil.getBufferedReader(matrixDir + x._1), x._2)).seq

    _path_types.par.map(x => (x, createPathMatrix(x, connectivity_matrices))).seq.toMap
  }

  def createPathMatrix(
      path_type: BaseEdgeSequencePathType,
      connectivity_matrices: Map[Int, CSCMatrix[Double]]): CSCMatrix[Double] = {
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
      new CSCMatrix.Builder[Double](numNodes, numNodes, 0).result
    } else {
      if (normalizeWalkProbabilities) {
        normalizeMatrix(result)
      } else {
        result
      }
    }
  }

  def normalizeMatrix(matrix: CSCMatrix[Double]): CSCMatrix[Double] = {
    val rowTotals = new mutable.HashMap[Int, Double].withDefaultValue(0.0)
    var offset = 0
    for (instance <- matrix.activeIterator) {
      rowTotals.update(instance._1._1, rowTotals(instance._1._1) + instance._2)
    }
    val builder = new CSCMatrix.Builder[Double](numNodes, numNodes, matrix.activeSize)
    for (instance <- matrix.activeIterator) {
      builder.add(instance._1._1, instance._1._2, instance._2 / rowTotals(instance._1._1))
    }
    builder.result
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
      relations: Set[Int]): Map[Int, CSCMatrix[Double]] = {
    val matrices = new mutable.HashMap[Int, CSCMatrix[Double]]
    val sorted_relations = relations.toSeq.sorted
    var relation_index = 0
    var current_relation = sorted_relations(relation_index)
    var builder: CSCMatrix.Builder[Double] = null
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
            builder = new CSCMatrix.Builder[Double](numNodes, numNodes)
          }
        } else if (builder != null) {
          val fields = line.split("\t")
          val source = fields(0).toInt
          val target = fields(1).toInt
          val value = if (fields.size == 2) 1 else fields(2).toDouble
          if (!edges_to_remove(current_relation).contains((source, target))
              && !edges_to_remove(current_relation).contains((target, source))) {
            builder.add(source, target, value)
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
