package edu.cmu.ml.rtw.pra.graphs

import scala.collection.mutable
import scala.util.Random

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

/**
 * Things this doesn't capture very well:
 * - Recursion (if you want to demonstrate where PRA fails with respect to ProPPR)
 * - Mutual exclusivity of certain relations (and maybe other kinds of relation metadata), though
 * PRA doesn't do a great job exploiting this right now, anyway
 * - Characteristics of actual data.  It'd be nice if you could generate some synthetic data that
 * was based off of what you see in Freebase, for instance, though I'm not really sure how to do
 * that.
 */
class SyntheticDataCreator(
    base_dir: String,
    params: JValue,
    fileUtil: FileUtil = new FileUtil()) {

  // Extracting parameters first
  implicit val formats = DefaultFormats

  val name = (params \ "name").extract[String]
  val num_entities = (params \ "num_entities").extract[Int]

  val num_base_relations = (params \ "num_base_relations").extract[Int]
  val num_base_relation_training_duplicates = (params \ "num_base_relation_training_duplicates").extract[Int]
  val num_base_relation_testing_duplicates = (params \ "num_base_relation_testing_duplicates").extract[Int]
  val num_base_relation_overlapping_instances = (params \ "num_base_relation_overlapping_instances").extract[Int]
  val num_base_relation_noise_instances = (params \ "num_base_relation_noise_instances").extract[Int]

  val num_pra_relations = (params \ "num_pra_relations").extract[Int]
  val num_pra_relation_training_instances = (params \ "num_pra_relation_training_instances").extract[Int]
  val num_pra_relation_testing_instances = (params \ "num_pra_relation_testing_instances").extract[Int]
  val num_rules = (params \ "num_rules").extract[Int]
  val min_rule_length = (params \ "min_rule_length").extract[Int]
  val max_rule_length = (params \ "max_rule_length").extract[Int]
  val rule_prob_mean = (params \ "rule_prob_mean").extract[Double]
  val rule_prob_stddev = (params \ "rule_prob_stddev").extract[Double]

  val num_noise_relations = (params \ "num_noise_relations").extract[Int]
  val num_noise_relation_instances = (params \ "num_noise_relation_instances").extract[Int]

  val r = new Random
  val split_dir = s"${base_dir}splits/${name}/"
  val relation_set_dir = s"${base_dir}relation_sets/${name}/"
  val in_progress_file = s"${relation_set_dir}in_progress"
  val param_file = s"${relation_set_dir}params.json"
  val relation_set_spec_file = s"${relation_set_dir}relation_set.tsv"
  val data_file = s"${relation_set_dir}data.tsv"

  lazy val relation_sets: Array[(Array[String], Array[String])] = {
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
    fileUtil.mkdirs(relation_set_dir)
    fileUtil.touchFile(in_progress_file)
    println(s"Creating relation set in $relation_set_dir")
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
    val out = fileUtil.getFileWriter(param_file)
    out.write(pretty(render(params)))
    out.close
    fileUtil.deleteFile(in_progress_file)
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
    for (rel_index <- 1 to relation_sets.size) {
      for (i <- 1 to num_base_relation_noise_instances) {
        val source = r.nextInt(num_entities)
        val target = r.nextInt(num_entities)
        val isTraining = if (r.nextDouble > .5) true else false
        val relation = getConcreteBaseRelation(rel_index, isTraining)
        instances += Tuple3(source, relation, target)
      }
    }
    instances.toSet
  }

  def outputRelationSet(all_instances: Seq[(Int, String, Int)]) {
    var out = fileUtil.getFileWriter(data_file)
    for (instance <- all_instances) {
      out.write(s"${instance._1}\t${instance._2}\t${instance._3}\t1\n")
    }
    out.close
    out = fileUtil.getFileWriter(relation_set_spec_file)
    out.write(s"relation file\t$data_file\n")
    out.write(s"is kb\tfalse\n")
    out.close
  }

  def outputSplitFiles(
      training_instances: Set[(Int, String, Int)],
      testing_instances: Set[(Int, String, Int)],
      pra_relations: Set[String]) {
    val relations_to_run_filename = s"$split_dir/relations_to_run.tsv"
    var out = fileUtil.getFileWriter(relations_to_run_filename)
    for (relation <- pra_relations) {
      out.write(relation)
      out.write("\n")
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
      out = fileUtil.getFileWriter(relation_dir + "training.tsv")
      for (instance <- relation_instance_set._2._1) {
        out.write(s"${instance._1}\t${instance._2}\n")
      }
      out.close
      out = fileUtil.getFileWriter(relation_dir + "testing.tsv")
      for (instance <- relation_instance_set._2._2) {
        out.write(s"${instance._1}\t${instance._2}\n")
      }
      out.close
    }
  }

  def rekeyByRelation(instances: Set[(Int, String, Int)]): Map[String, Set[(Int, Int)]] = {
    instances.groupBy(_._2).mapValues(set => set.map(instance => (instance._1, instance._3)))
  }

  def outputRules(rules: Seq[(String, Seq[Int], Double)]) {
    val rules_file = s"$split_dir/rules.tsv"
    val out = fileUtil.getFileWriter(rules_file)
    for (rule <- rules) {
      val relations = rule._2.mkString("-")
      out.write(s"${rule._1}\t${relations}\t${rule._3}\n")
    }
    out.close
  }
}

object SyntheticDataCreator {
  def main(args: Array[String]) {
    val params =
      ("name" -> "synthetic_easy") ~
      ("num_entities" -> 1000) ~
      ("num_base_relations" -> 25) ~
      ("num_base_relation_training_duplicates" -> 5) ~
      ("num_base_relation_testing_duplicates" -> 0) ~
      ("num_base_relation_overlapping_instances" -> 500) ~
      ("num_base_relation_noise_instances" -> 250) ~
      ("num_pra_relations" -> 2) ~
      ("num_pra_relation_training_instances" -> 500) ~
      ("num_pra_relation_testing_instances" -> 100) ~
      ("num_rules" -> 10) ~
      ("min_rule_length" -> 1) ~
      ("min_rule_length" -> 5) ~
      ("rule_prob_mean" -> .6) ~
      ("rule_prob_stddev" -> .2) ~
      ("num_noise_relations" -> 20) ~
      ("num_noise_relation_instances" -> 2500)
    new SyntheticDataCreator("/home/mg1/pra/", params).createRelationSet()
  }
}

// This bit of ugliness is required for proper testing.  I want to supply a fake instance of
// RelationSetCreator when testing the GraphCreator class, so I need to have a factory, so I don't
// have to call new RelationSetCreator...
trait ISyntheticDataCreatorFactory {
  def getSyntheticDataCreator(base_dir: String, params: JValue): SyntheticDataCreator = {
    getSyntheticDataCreator(base_dir, params, new FileUtil)
  }

  def getSyntheticDataCreator(base_dir: String, params: JValue, fileUtil: FileUtil): SyntheticDataCreator
}

class SyntheticDataCreatorFactory extends ISyntheticDataCreatorFactory {
  def getSyntheticDataCreator(base_dir: String, params: JValue, fileUtil: FileUtil) = {
    new SyntheticDataCreator(base_dir, params, fileUtil)
  }
}
