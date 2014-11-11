package edu.cmu.ml.rtw.pra.graphs

import breeze.linalg.CSCMatrix

import java.io.PrintWriter

import scala.collection.mutable
import scala.util.Random

class DatasetCreator(
    num_entities: Int,
    num_base_relations: Int,
    num_entities_per_relation: Int,
    num_complex_relations: Int,
    num_rules: Int) {

  val r = new Random

  def createRelationSet(filename: String) {
    val connectivity_matrices = generateConnectivityMatrices()
    val complex_relations = (1 to num_complex_relations).toList.par.map(
      x => generateComplexRelations(connectivity_matrices, x)).seq.toMap
    outputRelationSet(filename, connectivity_matrices, complex_relations)
  }

  def generateConnectivityMatrices() = {
    val connectivity_matrices = new mutable.HashMap[Int, CSCMatrix[Int]]
    for (i <- 1 to num_base_relations) {
      val builder = new CSCMatrix.Builder[Int](num_entities, num_entities, num_entities_per_relation)
      val seen_entries = new mutable.HashSet[(Int, Int)]
      while (seen_entries.size < num_entities_per_relation) {
        val source = r.nextInt(num_entities)
        val target = r.nextInt(num_entities)
        if (!seen_entries.contains((source, target))) {
          seen_entries.add((source, target))
          builder.add(source, target, 1)
        }
      }
      connectivity_matrices(i) = builder.result
    }
    connectivity_matrices.toMap
  }

  def generateComplexRelations(
      connectivity_matrices: Map[Int, CSCMatrix[Int]],
      relation_index: Int) = {
    val relations = (1 to num_base_relations).toList
    val path_lengths = Seq(2, 3, 4)
    val instances = new mutable.HashSet[(Int, Int)]
    val weight = r.nextDouble
    val shuffled = r.shuffle(relations)
    val path_length = r.shuffle(path_lengths).head
    var path_matrix = connectivity_matrices(shuffled(0))
    for (i <- 1 until path_length) {
      path_matrix = path_matrix * connectivity_matrices(shuffled(i))
    }
    for (entry <- path_matrix.activeIterator) {
      for (i <- 1 to entry._2) {
        val prob = r.nextDouble
        if (prob > weight) {
          instances += entry._1
        }
      }
    }
    (relation_index, instances.toSet)
  }

  def outputRelationSet(
      filename: String,
      connectivity_matrices: Map[Int, CSCMatrix[Int]],
      complex_relation_instances: Map[Int, Set[(Int, Int)]]) {
    val all_instances =
      (connectivity_matrices.map(x => (f"relation${x._1}%02d", x._2.activeKeysIterator.toSet)) ++
        complex_relation_instances.map(x => (f"complex${x._1}%02d", x._2)))
      .flatMap(x => x._2.map(y => (x._1, y._1, y._2)))
    val out = new PrintWriter(filename)
    for (instance <- all_instances) {
      out.println(s"${instance._2}\t${instance._1}\t${instance._3}\t1")
    }
    out.close
  }
}

object DatasetCreator {
  def main(args: Array[String]) {
    new DatasetCreator(100, 15, 200, 10, 20).createRelationSet("/home/mg1/pra/test.tsv")
  }
}
