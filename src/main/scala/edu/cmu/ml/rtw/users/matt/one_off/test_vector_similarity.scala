package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.pra.features.RescalPathMatrixCreator
import edu.cmu.ml.rtw.pra.features.PathType
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
    val creator = new RescalPathMatrixCreator(
      List[PathType]().asJava,
      Set[java.lang.Integer]().asJava,
      rescal_dir,
      node_dict,
      edge_dict,
      0)

    val edge_index = edge_dict.getIndex("base_01_training_01")
    val matrix = normalize(creator.rescal_matrices(edge_index).toDenseVector)
    val similarities = creator.rescal_matrices.par.map(entry => {
      val matrix2 = normalize(entry._2.toDenseVector)
      val similarity = matrix dot matrix2
      (similarity, edge_dict.getString(entry._1))
    }).seq.toList.sorted.reverse
    for (entry <- similarities.take(5)) {
      println(entry)
    }
  }
}
