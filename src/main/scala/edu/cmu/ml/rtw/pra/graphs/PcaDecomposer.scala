package edu.cmu.ml.rtw.pra.graphs

import java.io.PrintWriter

import scala.collection.mutable

import breeze.linalg._

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.FileUtil

class PcaDecomposer(
  graph_dir: String,
  result_dir: String,
  outputter: Outputter
) {

  val fileUtil = new FileUtil
  val graph_file = graph_dir + "/graph_chi/edges.tsv"
  val in_progress_file = result_dir + "in_progress"
  lazy val edge_dict = {
    val dict = new MutableConcurrentDictionary
    dict.setFromFile(graph_dir + "edge_dict.tsv")
    dict
  }

  def createPcaRelationEmbeddings(dims: Int) {
    fileUtil.mkdirs(result_dir)
    fileUtil.touchFile(in_progress_file)
    val rows = new mutable.HashMap[(Int, Int), mutable.ArrayBuffer[(Int, Double)]]

    outputter.info("Reading graph from file")
    for (line <- fileUtil.readLinesFromFile(graph_file)) {
      val fields = line.split("\t")
      val source = fields(0).toInt
      val target = fields(1).toInt
      val relation = fields(2).toInt
      val value = if (fields.size == 4) fields(3).toDouble else 1.0
      val rels = rows.getOrElseUpdate((source, target), new mutable.ArrayBuffer[(Int, Double)])
      rels += Tuple2(relation, value)
    }

    outputter.info(s"Building matrix with ${rows.size} rows and ${edge_dict.getNextIndex} columns")
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

    outputter.info(s"Performing SVD with $dims dimensions")
    val svd.SVD(u, s, v) = svd(matrix, dims)
    outputter.info(s"Got matrix, v is size: ${v.rows}, ${v.cols}")
    val weights = breeze.numerics.sqrt(s)

    outputter.info("Saving results")
    val out = fileUtil.getFileWriter(result_dir + "embeddings.tsv")
    for (i <- 1 until edge_dict.getNextIndex) {
      val vector = v(::, i) :* weights
      val normalized = vector / norm(vector)
      out.write(edge_dict.getString(i))
      for (j <- normalized.activeValuesIterator) {
        out.write("\t")
        out.write(j.toString)
      }
      out.write("\n")
    }
    out.close
    fileUtil.deleteFile(in_progress_file)
  }
}
