package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import breeze.linalg._

import scala.collection.mutable
import scalax.io.Resource

class GraphDensifier(
    graph_dir: String,
    matrix_out_dir: String,
    file_util: FileUtil = new FileUtil) {
  val maxMatrixFileSize = 100000
  val edge_dict = new Dictionary
  edge_dict.setFromFile(graph_dir + "/edge_dict.tsv")

  val node_dict = new Dictionary
  node_dict.setFromFile(graph_dir + "/node_dict.tsv")

  def densifyGraph(similarity_matrix_file: String) {
    file_util.mkdirs(matrix_out_dir)
    println("Reading the similarity matrix")
    val similarity_matrix = readSimilarityMatrix(similarity_matrix_file)
    println("Reading the graph")
    val edge_vectors = readGraphEdges(graph_dir + "/graph_chi/edges.tsv")
    println(s"Creating (${edge_vectors.size}) dense entity pair vectors")
    val dense_edge_vectors = edge_vectors.par.map(x => (x._1, similarity_matrix * x._2))
    println(s"Rekey-ing by relation")
    val relation_matrices = dense_edge_vectors.flatMap(x => {
      val entries = new mutable.ArrayBuffer[(Int, Int, Int, Double)]
      var offset = 0
      while (offset < x._2.activeSize) {
        val relation = x._2.indexAt(offset)
        val value = x._2.valueAt(offset)
        entries += Tuple4(relation, x._1._1, x._1._2, value)
        offset += 1
      }
      entries.toSeq
    }).groupBy(_._1).mapValues(x => x.map(y => (y._2, y._3, y._4)).seq.toSet).seq
    println("Outputting relation matrices")
    val edges_to_write = new mutable.ArrayBuffer[Set[(Int, Int, Double)]]
    var start_relation = 1
    var edges_so_far = 0
    for (i <- 1 until edge_dict.getNextIndex) {
      val matrix = relation_matrices.getOrElse(i, Set())
      if (edges_so_far > 0 && edges_so_far + matrix.size > maxMatrixFileSize) {
        writeEdgesSoFar(start_relation, i - 1, edges_to_write.toSeq)
        edges_to_write.clear()
        start_relation = i
        edges_so_far = 0
      }
      edges_to_write += matrix
      edges_so_far += matrix.size
    }
    if (edges_to_write.size > 0) {
      writeEdgesSoFar(start_relation, edge_dict.getNextIndex, edges_to_write)
    }
    println("Done creating matrices")
  }

  def emptyMatrixBuilder(): CSCMatrix.Builder[Double] = {
    new CSCMatrix.Builder[Double](node_dict.getNextIndex, node_dict.getNextIndex)
  }

  def writeEdgesSoFar(_start_relation: Int, end_relation: Int, edges_to_write: Seq[Set[(Int, Int, Double)]]) {
    var start_relation = _start_relation
    var filename = matrix_out_dir + start_relation
    if (end_relation > start_relation) {
      filename += "-" + end_relation
    }
    val writer = file_util.getFileWriter(filename)
    for (matrix <- edges_to_write) {
      writer.write("Relation " + start_relation + "\n")
      for (entry <- matrix) {
        writer.write(entry._1 + "\t" + entry._2 + "\t" + entry._3 + "\n")
      }
      start_relation += 1
    }
    writer.close()
  }

  def readGraphEdges(edge_file: String): Map[(Int, Int), SparseVector[Double]] = {
    val edges = new mutable.HashMap[(Int, Int), Set[Int]]().withDefaultValue(Set())
    for (line <- Resource.fromFile(edge_file).lines()) {
      val fields = line.split("\t")
      val source = fields(0).toInt
      val target = fields(1).toInt
      val relation = fields(2).toInt
      edges.update((source, target), edges(source, target) + relation)
    }
    edges.map(x => (x._1, createSparseVector(x._2))).toMap
  }

  def createSparseVector(relations: Set[Int]): SparseVector[Double] = {
    val builder = new VectorBuilder[Double](edge_dict.getNextIndex)
    for (relation <- relations) {
      builder.add(relation, 1.0)
    }
    builder.toSparseVector
  }

  def readSimilarityMatrix(filename: String): CSCMatrix[Double] = {
    val entries = new mutable.ListBuffer[(Int, Int, Double)]
    for (line <- Resource.fromFile(filename).lines()) {
      val fields = line.split("\t")
      val relation1 = edge_dict.getIndex(fields(0))
      val relation2 = edge_dict.getIndex(fields(1))
      val weight = fields(2).toDouble
      entries += Tuple3(relation1, relation2, weight)
    }
    val builder = new CSCMatrix.Builder[Double](rows=edge_dict.getNextIndex, cols=edge_dict.getNextIndex)
    for (entry <- entries) {
      builder.add(entry._1, entry._2, entry._3)
    }
    for (i <- 0 until edge_dict.getNextIndex) {
      builder.add(i, i, 1.0)
    }
    builder.result
  }
}

object GraphDensifier {
  def main(args: Array[String]) {
    new GraphDensifier("/home/mg1/pra/graphs/nell/kb_svo/",
      "/home/mg1/pra/graphs/nell/kb_svo/vector_matrices_test/").densifyGraph(
      "/home/mg1/pra/embeddings/pca_svo/similarity_matrix.tsv")
  }
}
