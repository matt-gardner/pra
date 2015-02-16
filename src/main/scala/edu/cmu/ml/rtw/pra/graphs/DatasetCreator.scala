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
    num_base_relation_training_duplicates: Int,
    num_base_relation_testing_duplicates: Int,
    num_base_relation_overlapping_instances: Int,

    num_pra_relations: Int,
    num_pra_relation_training_instances: Int,
    num_pra_relation_testing_instances: Int,
    num_rules: Int,
    min_rule_length: Int,
    max_rule_length: Int,
    rule_prob_mean: Double = .6,
    rule_prob_stddev: Double = .2,

    num_noise_relations: Int,
    num_noise_relation_instances: Int

    ) {

  val r = new Random
  val fileUtil = new FileUtil
  val split_dir = s"$pra_dir/splits/${split_name}/"
  val relation_sets: Array[(Array[String], Array[String])] = {
    val tmp = new mutable.ArrayBuffer[(Array[String], Array[String])]
    for (base <- 1 to num_base_relations) {
      val training_set = new mutable.ArrayBuffer[String]
      for (duplicate <- 1 to num_base_relation_training_duplicates) {
        val rel_name = f"base_${base}%02d_training_${duplicate}%02d"
        training_set += rel_name
      }
      if (num_base_relation_testing_duplicates > 0) {
        val testing_set = new mutable.ArrayBuffer[String]
        for (duplicate <- 1 to num_base_relation_testing_duplicates) {
          val rel_name = f"base_${base}%02d_testing_${duplicate}%02d"
          testing_set += rel_name
        }
        tmp += Tuple2(training_set.toArray, testing_set.toArray)
      } else {
        tmp += Tuple2(training_set.toArray, training_set.toArray)
      }
    }
    tmp.toArray
  }

  def createRelationSet() {
    fileUtil.mkdirs(split_dir)
    val pra_relations = (1 to num_pra_relations).toList.par.map(generatePraRelations)
    val instances = (new mutable.ArrayBuffer[(Int, String, Int)],
      new mutable.ArrayBuffer[(Int, String, Int)])
    val pra_instances = pra_relations.map(generateRelationInstances).seq
    for (rel_instances <- pra_instances) {
      instances._1 ++= rel_instances._1
      instances._2 ++= rel_instances._2
    }
    instances._1 ++= relation_sets.par.flatMap(generateOverlappingInstances).seq
    instances._1 ++= generateNoiseInstances()
    outputRelationSet(instances._1)
    outputSplitFiles(instances._1.toSet, instances._2.toSet, pra_relations.map(_._1).seq.toSet)
    outputRules(pra_relations.flatMap(x => x._2.map(y => (x._1, y._1, y._2))).seq)
  }

  def generatePraRelations(relation_index: Int) = {
    val rules = new mutable.ArrayBuffer[(Seq[Int], Double)]
    for (rule <- 1 to num_rules) {
      val relations = (1 to num_base_relations).toList
      val path_lengths = min_rule_length to max_rule_length
      val shuffled = r.shuffle(relations)
      val path_length = r.shuffle(path_lengths).head
      var rule_prob = r.nextGaussian() * rule_prob_stddev + rule_prob_mean
      if (rule_prob > 1.0) rule_prob = 1.0
      if (rule_prob < 0.0) rule_prob = 0.0
      rules += Tuple2(shuffled.take(path_length), rule_prob)
    }
    (f"pra_${relation_index}%02d", rules.toSeq)
  }

  def generateRelationInstances(pra_relation: (String, Seq[(Seq[Int], Double)])) = {
    val training_instances = new mutable.HashSet[(Int, String, Int)]
    val testing_instances = new mutable.HashSet[(Int, String, Int)]
    for (i <- 1 to num_pra_relation_training_instances) {
      val instances = generatePraInstance(pra_relation._1, pra_relation._2, true)
      training_instances ++= instances._1
      testing_instances ++= instances._2
    }
    for (i <- 1 to num_pra_relation_testing_instances) {
      val instances = generatePraInstance(pra_relation._1, pra_relation._2, false)
      training_instances ++= instances._1
      testing_instances ++= instances._2
    }
    (training_instances.toSeq, testing_instances.toSeq)
  }

  def generatePraInstance(name: String, rules: Seq[(Seq[Int], Double)], isTraining: Boolean) = {
    val training_instances = new mutable.HashSet[(Int, String, Int)]
    val testing_instances = new mutable.HashSet[(Int, String, Int)]
    val source = r.nextInt(num_entities)
    val target = r.nextInt(num_entities)
    if (isTraining) {
      training_instances += Tuple3(source, name, target)
    } else {
      testing_instances += Tuple3(source, name, target)
    }
    for (rule <- rules) {
      if (r.nextDouble < rule._2) {
        var i = 0
        var current_node = source
        while (i < rule._1.size - 1) {
          val next_node = r.nextInt(num_entities)
          val relation = getConcreteBaseRelation(rule._1(i), isTraining)
          training_instances += Tuple3(current_node, relation, next_node)
          current_node = next_node
          i += 1
        }
        val relation = getConcreteBaseRelation(rule._1(i), isTraining)
        training_instances += Tuple3(current_node, relation, target)
      }
    }
    (training_instances.toSet, testing_instances.toSet)
  }

  def getConcreteBaseRelation(index: Int, isTraining: Boolean) = {
    val base_relation = relation_sets(index-1)
    //println(index + " " + base_relation._1.size + " " + base_relation._2.size)
    if (isTraining) {
      base_relation._1(r.nextInt(base_relation._1.size))
    } else {
      base_relation._2(r.nextInt(base_relation._2.size))
    }
  }

  def generateOverlappingInstances(relation_set: (Array[String], Array[String])): Set[(Int, String, Int)] = {
    val instances = new mutable.HashSet[(Int, String, Int)]
    val relations = (relation_set._1 ++ relation_set._2).toSet.toList
    if (relations.size < 2) return Set()
    for (i <- 1 to num_base_relation_overlapping_instances) {
      val source = r.nextInt(num_entities)
      val target = r.nextInt(num_entities)
      val shuffled = r.shuffle(relations)
      val rel_1 = shuffled(0)
      val rel_2 = shuffled(1)
      instances += Tuple3(source, rel_1, target)
      instances += Tuple3(source, rel_2, target)
    }
    instances.toSet
  }

  def generateNoiseInstances() = {
    val instances = new mutable.HashSet[(Int, String, Int)]
    for (i <- 1 to num_noise_relations) {
      val name = f"noise_${i}%02d"
      for (j <- 1 to num_noise_relation_instances) {
        val source = r.nextInt(num_entities)
        val target = r.nextInt(num_entities)
        instances += Tuple3(source, name, target)
      }
    }
    instances.toSet
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

  def outputSplitFiles(
      training_instances: Set[(Int, String, Int)],
      testing_instances: Set[(Int, String, Int)],
      pra_relations: Set[String]) {
    val relations_to_run_filename = s"$split_dir/relations_to_run.tsv"
    var out = new PrintWriter(relations_to_run_filename)
    for (relation <- pra_relations) {
      out.println(relation)
    }
    out.close
    val relation_instances: Map[String, (Set[(Int, Int)], Set[(Int, Int)])] = {
      val training_instance_map = rekeyByRelation(training_instances)
      val testing_instance_map = rekeyByRelation(testing_instances)
      pra_relations.map(name => (name, (training_instance_map(name),
        testing_instance_map(name)))).toMap
    }
    for (relation_instance_set <- relation_instances) {
      val relation_dir = split_dir + relation_instance_set._1 + "/"
      fileUtil.mkdirs(relation_dir)
      out = new PrintWriter(relation_dir + "training.tsv")
      for (instance <- relation_instance_set._2._1) {
        out.println(s"${instance._1}\t${instance._2}")
      }
      out.close
      out = new PrintWriter(relation_dir + "testing.tsv")
      for (instance <- relation_instance_set._2._2) {
        out.println(s"${instance._1}\t${instance._2}")
      }
      out.close
    }
  }

  def rekeyByRelation(instances: Set[(Int, String, Int)]): Map[String, Set[(Int, Int)]] = {
    instances.groupBy(_._2).mapValues(set => set.map(instance => (instance._1, instance._3)))
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
      "synthetic_easy",
      1000,

      25,
      5,
      0,
      250,

      2,
      500,
      100,
      10,
      1,
      5,
      .6,
      .2,

      20,
      2500
      ).createRelationSet()
  }
}
