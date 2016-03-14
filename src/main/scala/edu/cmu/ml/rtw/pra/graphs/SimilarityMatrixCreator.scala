package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.JsonHelper
import com.mattg.util.FileUtil
import com.mattg.util.MutableConcurrentDictionary

import breeze.linalg._

import java.io.PrintWriter
import java.util.Random

import scala.collection.mutable

import org.json4s._
import org.json4s.native.JsonMethods.{pretty,render,parse}

class SimilarityMatrixCreator(
    embeddingsDir: String,
    name: String,
    outputter: Outputter,
    fileUtil: FileUtil = new FileUtil) {
  implicit val formats = DefaultFormats

  // If we see more hash collisions than this, recommend increasing the hash size.
  val WARNING_SIZE = 10000

  val embeddingsFile = embeddingsDir + "embeddings.tsv"
  val matrixDir = embeddingsDir + name + "/"
  val outFile = matrixDir + "matrix.tsv"
  val paramFile = matrixDir + "params.json"
  val inProgressFile = matrixDir + "in_progress"

  var num_vectors: Int = 0
  var dimension: Int = 0

  var bad_vector: DenseVector[Double] = null

  def createSimilarityMatrix(params: JValue): Unit = {
    val threshold = (params \ "threshold").extract[Double]
    val num_hashes = (params \ "num_hashes").extract[Int]
    val hash_size = (params \ "hash_size").extract[Int]
    val max_similar = JsonHelper.extractWithDefault(params, "entries per vector", -1)
    val upper_threshold = JsonHelper.extractWithDefault(params, "upper threshold", 0.99999)
    val to_ignore: Set[String] = {
      (params \ "to ignore") match {
        case JNothing => Set()
        case JString(path) => fileUtil.readLinesFromFile(path).toSet
        case other => throw new IllegalStateException("\"to ignore\" must be a string")
      }
    }

    fileUtil.mkdirs(matrixDir)
    fileUtil.touchFile(inProgressFile)
    val paramOut = fileUtil.getFileWriter(paramFile)
    paramOut.write(pretty(render(params)))
    paramOut.close

    val dict = new MutableConcurrentDictionary
    outputter.info("Reading vectors")
    val vectors = {
      val tmp = new mutable.ArrayBuffer[(Int, DenseVector[Double])]
      for (line <- fileUtil.readLinesFromFile(embeddingsFile)) {
        val fields = line.split("\t")
        val relation = fields(0)
        if (!to_ignore.contains(relation)) {
          val vector = normalize(DenseVector(fields.drop(1).map(_.toDouble)))
          if (norm(vector) > 0) {
            if (bad_vector == null || ((vector dot bad_vector) < .99 && (vector dot -bad_vector) < .99)) {
              tmp += Tuple2(dict.getIndex(relation), vector)
            }
          }
        }
      }
      tmp.toSeq
    }

    num_vectors = vectors.size
    dimension = vectors(0)._2.size
    outputter.info("Creating hash functions")
    val hash_functions = createHashFunctions(num_hashes, hash_size, vectors.map(_._2))
    outputter.info("Hashing vectors")
    val hashed_vectors = vectors.par.map(x => (x._1, hashVector(x._2, hash_functions), x._2))
    val hash_maps = (0 until num_hashes).map(index => {
      hashed_vectors.seq.map(y => (y._2(index), (y._1, y._3))).groupBy(_._1).seq.toMap.transform(
        (k, v) => v.map(_._2).toSeq).withDefaultValue(Nil)
    }).toSeq
    outputter.info("Computing similarities")
    val similarities = hashed_vectors.flatMap(x =>
        computeSimilarities(threshold, upper_threshold, max_similar, x, hash_maps))
    outputter.info("Done computing similarities; outputting results")
    val out = fileUtil.getFileWriter(outFile)
    similarities.map(x => out.write(s"${dict.getString(x._1)}\t${dict.getString(x._2)}\t${x._3}\n"))
    out.close()
    fileUtil.deleteFile(inProgressFile)
  }

  val done = new java.util.concurrent.atomic.AtomicInteger
  def computeSimilarities(
      lower_threshold: Double,
      upper_threshold: Double,
      max_similar: Int,
      vec: (Int, Seq[Int], DenseVector[Double]),
      hash_maps: Seq[Map[Int, Seq[(Int, DenseVector[Double])]]]): Seq[(Int, Int, Double)] = {
    if (done.getAndIncrement % 10000 == 0) outputter.info(done.get.toString)
    val start = System.currentTimeMillis
    val close_vectors =
      for ((hash_map, index) <- hash_maps.zipWithIndex; vec2 <- hash_map(vec._2(index))
           if vec2._1 != vec._1)
             yield vec2
    if (close_vectors.size > 100000) {
      outputter.warn("There were too many hash collisions, so I'm giving up on this one")
      return Seq()
    }
    val close_vector_set = close_vectors.toSet
    if (close_vector_set.size > 1000) {
      outputter.warn(s"Saw ${close_vector_set.size} hash collisions for relation ${vec._1}.  Consider " +
        "increasing your hash size")
    }
    val similarities =
      for (vec2 <- close_vector_set;
           similarity = vec._3 dot vec2._2
           if similarity > lower_threshold && similarity < upper_threshold)
             yield (vec._1, vec2._1, similarity)
    var seconds = ((System.currentTimeMillis - start) / 1000.0)
    if (max_similar == -1) {
      similarities.toSeq
    } else {
      similarities.toSeq.sortBy(-_._3).take(max_similar)
    }
  }

  def createHashFunctions(
      num_hashes: Int,
      hash_size: Int,
      vectors: Traversable[DenseVector[Double]]): Seq[Seq[DenseVector[Double]]] = {
    val sum = DenseVector.zeros[Double](dimension)
    for (vector <- vectors) {
      sum += vector
    }
    val mean = sum * (1.0 / num_vectors)
    val squared_diffs = DenseVector.zeros[Double](dimension)
    for (vector <- vectors) {
      val diff = vector - mean
      squared_diffs += diff :* diff
    }
    val variance = squared_diffs * (1.0 / num_vectors)
    val gaussians = {
      val tmp = new mutable.ArrayBuffer[(Double, Double)]
      for (i <- 0 until dimension) {
        tmp += Tuple2(mean(i), Math.sqrt(variance(i)))
      }
      tmp.toSeq
    }
    val random = new Random
    val hash_functions = new mutable.ArrayBuffer[Seq[DenseVector[Double]]]
    for (hash_num <- 1 to num_hashes) {
      val hash_function = new mutable.ArrayBuffer[DenseVector[Double]]
      for (hash_dim <- 1 to hash_size) {
        val vector = DenseVector.zeros[Double](dimension)
        for (i <- 0 until dimension) {
          vector(i) = random.nextGaussian() * gaussians(i)._2 + gaussians(i)._1
        }
        hash_function += normalize(vector)
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
