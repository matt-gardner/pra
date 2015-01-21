package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.users.matt.util.Dictionary

import breeze.linalg._

import java.io.PrintWriter

import scala.collection.mutable
import scalax.io.Resource

class SimilarityMatrixCreator(
    threshold: Double,
    num_hashes: Int,
    hash_size: Int) {
  var num_vectors: Int = 0
  var dimension: Int = 0

  def createSimilarityMatrix(embeddingsFile: String, outFile: String) {
    val dict = new Dictionary
    println("Reading vectors")
    val vectors = Resource.fromFile(embeddingsFile).lines().map(_.split("\t")).map(x =>
        (dict.getIndex(x(0)), normalize(DenseVector(x.drop(1).map(_.toDouble))))).filter(x => norm(x._2) > 0)
    num_vectors = vectors.size
    dimension = vectors(0)._2.size
    println("Finding max and min vectors")
    val extremes = findExtremes(vectors)
    val min = extremes._1
    val max = extremes._2
    println(s"Min vector: $min")
    println(s"Max vector: $max")
    println("Creating hash functions")
    val hash_functions = createHashFunctions(min, max)
    println("Hashing vectors")
    val hashed_vectors = vectors.par.map(x => (x._1, hashVector(x._2, hash_functions), x._2))
    val hash_maps = (0 until num_hashes).map(index => {
      hashed_vectors.seq.map(y => (y._2(index), (y._1, y._3))).groupBy(_._1).seq.toMap.transform(
        (k, v) => v.map(_._2).toSeq).withDefaultValue(Nil)
    }).toSeq
    println("Computing similarities")
    val similarities = hashed_vectors.flatMap(x => computeSimilarities(x, hash_maps))
    val out = new PrintWriter(outFile)
    similarities.map(x => out.println(s"${dict.getString(x._1)}\t${dict.getString(x._2)}\t${x._3}"))
  }

  def computeSimilarities(
      vec: (Int, Seq[Int], DenseVector[Double]),
      hash_maps: Seq[Map[Int, Seq[(Int, DenseVector[Double])]]]): Seq[(Int, Int, Double)] = {
    println(s"Processing vector number ${vec._1}")
    val start = System.currentTimeMillis
    val close_vectors = new mutable.HashSet[(Int, DenseVector[Double])]
    for (index <- 0 until num_hashes) {
      for (vec2 <- hash_maps(index)(vec._2(index))) {
        if (vec2._1 != vec._1) {
          close_vectors += vec2
        }
      }
    }
    println(s"Number of close vectors: ${close_vectors.size}")
    val similarities = new mutable.ArrayBuffer[(Int, Int, Double)]
    for (vec2 <- close_vectors) {
      val similarity = vec._3 dot vec2._2
      if (similarity > threshold) {
        similarities += Tuple3(vec._1, vec2._1, similarity)
      }
    }
    var seconds = ((System.currentTimeMillis - start) / 1000.0)
    println(s"Took $seconds seconds")
    val total_minutes = num_vectors * seconds / 60;
    println(s"Estimated total minutes: $total_minutes")

    similarities.toSeq
  }

  def findExtremes(vectors: Traversable[(Int, DenseVector[Double])]) = {
    val minVector = DenseVector.zeros[Double](dimension)
    val maxVector = DenseVector.zeros[Double](dimension)
    for (vec <- vectors.map(_._2);
         i <- 0 until dimension) {
      if (vec(i) < minVector(i)) minVector(i) = vec(i)
      if (vec(i) > maxVector(i)) maxVector(i) = vec(i)
    }
    (minVector, maxVector)
  }

  def createHashFunctions(min: DenseVector[Double], max: DenseVector[Double]): Seq[Seq[DenseVector[Double]]] = {
    val hash_functions = new mutable.ArrayBuffer[Seq[DenseVector[Double]]]
    for (hash_num <- 1 to num_hashes) {
      val hash_function = new mutable.ArrayBuffer[DenseVector[Double]]
      for (hash_dim <- 1 to hash_size) {
        hash_function += DenseVector.rand(dimension) :* (max - min) + min
      }
      hash_functions += hash_function.toSeq
    }
    hash_functions.toSeq
  }

  def hashVector(vec: DenseVector[Double], functions: Seq[Seq[DenseVector[Double]]]): Seq[Int] = {
    val hashes = new mutable.ArrayBuffer[Int]
    for (function <- functions) {
      var hash = 0
      for (hash_vector <- function) {
        hash = hash << 1
        if ((vec dot hash_vector) > 0) hash += 1
      }
      hashes += hash
    }
    hashes.toSeq
  }
}

object SimilarityMatrixCreator {
  def main(args: Array[String]) {
    new SimilarityMatrixCreator(.8, 3, 15).createSimilarityMatrix(
      "/home/mg1/pra/embeddings/pca_svo/embeddings.tsv",
      "/home/mg1/pra/embeddings/pca_svo/similarity_matrix.tsv")
  }
}
