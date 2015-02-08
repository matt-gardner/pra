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
    num_complex_relations: Int,
    num_complex_relation_instances: Int,
    num_rules: Int,
    percent_training: Double = 0.8,
    rule_prob_mean: Double = .6,
    rule_prob_stddev: Double = .2) {

  val r = new Random
  val fileUtil = new FileUtil
  val split_dir = s"$pra_dir/splits/${split_name}/"

  def createRelationSet() {
    fileUtil.mkdirs(split_dir)
    val complex_relations = (1 to num_complex_relations).toList.par.map(generateComplexRelations)
    val instances = complex_relations.flatMap(generateRelationInstances)
    outputRelationSet(instances.seq)
    outputSplitFiles(complex_relations.map(x => (x._1, x._2)).toMap.seq)
    outputRules(complex_relations.flatMap(x => x._3.map(y => (x._1, y._1, y._2))).seq)
  }

  def generateComplexRelations(relation_index: Int) = {
    val instances = new mutable.HashSet[(Int, Int)]
    val entities = (1 to num_entities).toList
    for (i <- 1 to num_complex_relation_instances) {
      val shuffled = r.shuffle(entities)
      val source = shuffled(0)
      val target = shuffled(1)
      instances += Tuple2(source, target)
    }
    val rules = new mutable.ArrayBuffer[(Seq[Int], Double)]
    for (rule <- 1 to num_rules) {
      val relations = (1 to num_base_relations).toList
      val path_lengths = Seq(2, 3, 4)
      val shuffled = r.shuffle(relations)
      val path_length = r.shuffle(path_lengths).head
      var rule_prob = r.nextGaussian() * rule_prob_stddev + rule_prob_mean
      if (rule_prob > 1.0) rule_prob = 1.0
      if (rule_prob < 0.0) rule_prob = 0.0
      rules += Tuple2(shuffled.take(path_length), rule_prob)
    }
    (f"complex$relation_index%02d", instances.toSet, rules.toSeq)
  }

  def generateRelationInstances(complex_relation: (String, Set[(Int, Int)], Seq[(Seq[Int], Double)])) = {
    val instances = new mutable.HashSet[(Int, String, Int)]
    for (instance <- complex_relation._2) {
      instances += Tuple3(instance._1, complex_relation._1, instance._2)
      for (rule <- complex_relation._3) {
        if (r.nextDouble < rule._2) {
          var i = 0
          var current_node = instance._1
          while (i < rule._1.size - 1) {
            val next_node = r.nextInt(num_entities)
            val relation = f"relation${rule._1(i)}%02d"
            instances += Tuple3(current_node, relation, next_node)
            current_node = next_node
            i += 1
          }
          val relation = f"relation${rule._1(i)}%02d"
          instances += Tuple3(current_node, relation, instance._2)
        }
      }
    }
    instances.toSeq
  }

  def outputRelationSet(all_instances: Seq[(Int, String, Int)]) {
    val relation_data_filename = s"${pra_dir}relation_sets/${split_name}_data.tsv"
    var out = new PrintWriter(relation_data_filename)
    for (instance <- all_instances) {
      out.println(s"${instance._1}\t${instance._2}\t${instance._3}\t1")
    }
    out.close
    val relation_set_filename = s"${pra_dir}relation_sets/${split_name}.tsv"
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

  def outputRules(rules: Seq[(String, Seq[Int], Double)]) {
    val rules_file = s"$split_dir/rules.tsv"
    val out = new PrintWriter(rules_file)
    for (rule <- rules) {
      val relations = rule._2.mkString("-")
      out.println(s"${rule._1}\t${relations}\t${rule._3}")
    }
    out.close
  }
}

object DatasetCreator {
  def main(args: Array[String]) {
    new DatasetCreator(
      "/home/mg1/pra/",
      "synthetic",
      10000,
      100,
      5,
      2000,
      4).createRelationSet()
  }
}
