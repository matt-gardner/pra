package edu.cmu.ml.rtw.pra.graphs

import breeze.linalg.CSCMatrix

import java.io.PrintWriter

import scala.collection.mutable
import scala.util.Random

import edu.cmu.ml.rtw.users.matt.util.FileUtil

class DatasetCreator(
    pra_dir: String,
    split_name: String,
    num_entities: Int,
    num_base_relations: Int,
    num_entities_per_relation: Int,
    num_complex_relations: Int,
    num_rules: Int,
    percent_training: Double = 0.8,
    rule_prob: Double = .95) {

  val r = new Random
  val fileUtil = new FileUtil
  val split_dir = s"$pra_dir/splits/${split_name}/"

  def createRelationSet() {
    fileUtil.mkdirs(split_dir)
    val connectivity_matrices = generateConnectivityMatrices()
    val complex_relations = (1 to num_complex_relations).toList.par.map(
      x => generateComplexRelations(connectivity_matrices, x)).seq
    val all_instances =
      (connectivity_matrices.map(x => (f"relation${x._1}%02d", x._2.activeKeysIterator.toSet)) ++
        complex_relations.map(x => (x._1, x._2)))
      .flatMap(x => x._2.map(y => (y._1, x._1, y._2))).toSeq
    outputRelationSet(all_instances)
    outputSplitFiles(complex_relations.map(x => (x._1, x._2)).toMap)
    outputRules(complex_relations.flatMap(x => x._3.map(y => (x._1, y._1, y._2, y._3))))
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
    val instances = new mutable.HashSet[(Int, Int)]
    val rules = new mutable.ArrayBuffer[(Seq[Int], Double, Int)]
    for (rule <- 1 to num_rules) {
      val relations = (1 to num_base_relations).toList
      val path_lengths = Seq(2, 3, 4)
      val weight = rule_prob
      val shuffled = r.shuffle(relations)
      val path_length = r.shuffle(path_lengths).head
      var path_matrix = connectivity_matrices(shuffled(0))
      for (i <- 1 until path_length) {
        path_matrix = path_matrix * connectivity_matrices(shuffled(i))
      }
      rules += Tuple3(shuffled.take(path_length), weight, path_matrix.activeSize)
      for (entry <- path_matrix.activeIterator) {
        for (i <- 1 to entry._2) {
          val prob = r.nextDouble
          if (prob > rule_prob) {
            instances += entry._1
          }
        }
      }
    }
    (f"complex$relation_index%02d", instances.toSet, rules.toSeq)
  }

  def outputRelationSet(all_instances: Seq[(Int, String, Int)]) {
    val relation_data_filename = s"$pra_dir/relation_sets/${split_name}_data.tsv"
    var out = new PrintWriter(relation_data_filename)
    for (instance <- all_instances) {
      out.println(s"${instance._1}\t${instance._2}\t${instance._3}\t1")
    }
    out.close
    val relation_set_filename = s"$pra_dir/relation_sets/${split_name}.tsv"
    out = new PrintWriter(relation_set_filename)
    out.println(s"relation file\t$relation_data_filename")
    out.println(s"is kb\tfalse")
    out.close
    val graph_spec_filename = s"$pra_dir/graph_specs/${split_name}.spec"
    out = new PrintWriter(graph_spec_filename)
    out.println(s"relation set\t$relation_set_filename")
    out.close
  }

  def outputSplitFiles(complex_relation_instances: Map[String, Set[(Int, Int)]]) {
    val relations_to_run_filename = s"$split_dir/relations_to_run.tsv"
    var out = new PrintWriter(relations_to_run_filename)
    for (relation <- complex_relation_instances.keys) {
      out.println(relation)
    }
    out.close
    for (relation_instances <- complex_relation_instances) {
      val shuffled = r.shuffle(relation_instances._2.toList)
      val num_training = (shuffled.size * percent_training).toInt
      val (training, testing) = shuffled.splitAt(num_training)
      val relation_dir = split_dir + relation_instances._1 + "/"
      fileUtil.mkdirs(relation_dir)
      out = new PrintWriter(relation_dir + "training.tsv")
      for (instance <- training) {
        out.println(s"${instance._1}\t${instance._2}")
      }
      out.close
      out = new PrintWriter(relation_dir + "testing.tsv")
      for (instance <- testing) {
        out.println(s"${instance._1}\t${instance._2}")
      }
      out.close
    }
  }

  def outputRules(rules: Seq[(String, Seq[Int], Double, Int)]) {
    val rules_file = s"$split_dir/rules.tsv"
    val out = new PrintWriter(rules_file)
    for (rule <- rules) {
      val relations = rule._2.mkString("-")
      out.println(s"${rule._1}\t${relations}\t${rule._3}\t${rule._4}")
    }
    out.close
  }
}

object DatasetCreator {
  def main(args: Array[String]) {
    new DatasetCreator(
      "/home/mg1/pra/",
      "synthetic",
      100,
      15,
      300,
      2,
      5).createRelationSet()
  }
}
