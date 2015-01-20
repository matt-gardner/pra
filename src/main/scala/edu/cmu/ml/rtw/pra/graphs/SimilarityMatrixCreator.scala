package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.users.matt.util.Dictionary

import breeze.linalg._

import java.io.PrintWriter

import scala.collection.mutable
import scalax.io.Resource

class SimilarityMatrixCreator(threshold: Double) {
  var num_vectors = 0

  def createSimilarityMatrix(embeddingsFile: String, outFile: String) {
    val dict = new Dictionary
    println("Reading vectors")
    val vectors = Resource.fromFile(embeddingsFile).lines().map(_.split("\t")).map(x =>
        (dict.getIndex(x(0)), DenseVector(x.drop(1).map(_.toDouble))))
    num_vectors = vectors.size
    println("Computing similarities")
    val similarities = vectors.par.flatMap(x => computeSimilarities(x, vectors))
    val out = new PrintWriter(outFile)
    similarities.map(x => out.println(s"${dict.getString(x._1)}\t${dict.getString(x._2)}\t${x._3}"))
  }

  def computeSimilarities(
      vec1: (Int, DenseVector[Double]),
      vectors: Traversable[(Int, DenseVector[Double])]): Seq[(Int, Int, Double)] = {
    println(s"Processing vector number ${vec1._1}")
    val start = System.currentTimeMillis
    val similarities = new mutable.ArrayBuffer[(Int, Int, Double)]
    for (vec2 <- vectors) {
      val similarity = vec1._2 dot vec2._2
      if (similarity > threshold) {
        similarities += Tuple3(vec1._1, vec2._1, similarity)
      }
    }
    var seconds = ((System.currentTimeMillis - start) / 1000.0).toInt
    println(s"Took $seconds seconds")
    val total_minutes = num_vectors * seconds / 60;
    println(s"Estimated total minutes: $total_minutes")

    similarities.toSeq
  }
}

object SimilarityMatrixCreator {
  def main(args: Array[String]) {
    new SimilarityMatrixCreator(.8).createSimilarityMatrix(
      "/home/mg1/pra/embeddings/pca_svo/embeddings.tsv",
      "/home/mg1/pra/embeddings/pca_svo/similarity_matrix.tsv")
  }
}
