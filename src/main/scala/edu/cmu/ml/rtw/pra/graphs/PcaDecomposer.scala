package edu.cmu.ml.rtw.pra.graphs

import java.io.PrintWriter

import scala.collection.mutable
import scalax.io.Resource

import breeze.linalg._

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

class PraDecomposer(
    graph_dir: String,
    result_dir: String) {

  val graph_file = graph_dir + "/graph_chi/edges.tsv"
  val node_dict = {
    val dict = new Dictionary
    dict.setFromFile(graph_dir + "node_dict.tsv")
    dict
  }
  val edge_dict = {
    val dict = new Dictionary
    dict.setFromFile(graph_dir + "edge_dict.tsv")
    dict
  }

  def getPcaRelationEmbeddings(dims: Int) {
    val rows = new mutable.HashMap[(Int, Int), mutable.ArrayBuffer[(Int, Double)]]

    println("Reading graph from file")
    for (line <- Resource.fromFile(graph_file).lines()) {
      val fields = line.split("\t")
      val source = fields(0).toInt
      val target = fields(1).toInt
      val relation = fields(2).toInt
      val value = if (fields.size == 4) fields(3).toDouble else 1.0
      val rels = rows.getOrElseUpdate((source, target), new mutable.ArrayBuffer[(Int, Double)])
      rels += Tuple2(relation, value)
    }

    println(s"Building matrix with ${rows.size} rows and ${edge_dict.getNextIndex} columns")
    val builder = new CSCMatrix.Builder[Double](rows.size, edge_dict.getNextIndex)
    var i = 0
    for (row <- rows) {
      for (relation_value <- row._2) {
        val relation = relation_value._1
        val value = relation_value._2
        builder.add(i, relation, value)
      }
      i += 1
    }
    val matrix = builder.result

    println("Performing SVD with $dims dimensions")
    val svd.SVD(u, s, v) = svd(matrix, dims)

    println("Saving results")
    println(s"Got matrix, v is size: ${v.rows}, ${v.cols}")
    new FileUtil().mkdirs(result_dir)
    val out = new PrintWriter(result_dir + "embeddings.tsv")
    for (i <- 1 until edge_dict.getNextIndex) {
      out.println(edge_dict.getString(i) + "\t" + v(::, i))
    }
    out.close

    val index = edge_dict.getIndex("base_10_training_01")
    var vector = v(::, index)
    vector = vector / norm(vector)
    val similarities = (1 until edge_dict.getNextIndex).map(i => {
      var vec = v(::, i)
      vec = vec / norm(vec)
      val sim = vec dot vector
      (sim, i)
    })
    for ((sim, i) <- similarities.sorted.reverse.take(5)) {
      println(s"${edge_dict.getString(i)}, ${sim}")
    }
  }
}

object PraDecomposer {
  def main(args: Array[String]) {
    new PraDecomposer("/home/mg1/pra/graphs/synthetic_hard/",
      "/home/mg1/pra/embeddings/synthetic_hard/").getPcaRelationEmbeddings(50)
  }
}
