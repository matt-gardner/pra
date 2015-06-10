package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy
import edu.cmu.ml.rtw.pra.features.PathType
import edu.cmu.ml.rtw.pra.features.RescalMatrixPathFollower
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

import breeze.linalg._

object test_vector_similarity {
  val fileUtil = new FileUtil

  def NOT_main(args: Array[String]) {
    val rescal_dir = "/home/mg1/pra/comparison/rescal/synthetic/small/easy/"
    val node_dict = new Dictionary
    val edge_dict = new Dictionary
    val follower = new RescalMatrixPathFollower(
      new PraConfigBuilder().setNoChecks().build(),
      Seq[PathType](),
      rescal_dir,
      new Dataset(Seq()),
      0,
      MatrixRowPolicy.PAIRED_TARGETS_ONLY)

    val edge_index = edge_dict.getIndex("base_01_training_01")
    val rescal_matrices = follower.getRescalMatrices
    val matrix = normalize(rescal_matrices(edge_index).toDenseVector)
    val similarities = rescal_matrices.par.map(entry => {
      val matrix2 = normalize(entry._2.toDenseVector)
      val similarity = matrix dot matrix2
      (similarity, edge_dict.getString(entry._1))
    }).seq.toList.sorted.reverse
    for (entry <- similarities.take(5)) {
      println(entry)
    }
  }
}
