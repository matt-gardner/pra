package edu.cmu.ml.rtw.pra.graphs

import scala.collection.mutable
import scala.util.Random

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper

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
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil()
) {

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
  val num_negatives_per_positive = JsonHelper.extractWithDefault(params, "num_negatives_per_positive", 1)
  val num_rules = (params \ "num_rules").extract[Int]
  val min_rule_length = (params \ "min_rule_length").extract[Int]
  val max_rule_length = (params \ "max_rule_length").extract[Int]
  val rule_prob_mean = (params \ "rule_prob_mean").extract[Double]
  val rule_prob_stddev = (params \ "rule_prob_stddev").extract[Double]

  val num_noise_relations = (params \ "num_noise_relations").extract[Int]
  val num_noise_relation_instances = (params \ "num_noise_relation_instances").extract[Int]

  val r = new Random
  val split_dir = s"${base_dir}splits/${name}/"
  // TODO(matt): can't we create a better place for this?
  val relation_set_dir = s"${base_dir}synthetic_relation_sets/${name}/"
  val in_progress_file = s"${relation_set_dir}in_progress"
  val param_file = s"${relation_set_dir}params.json"
  val data_file = s"${relation_set_dir}data.tsv"

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
    fileUtil.mkdirs(relation_set_dir)
    fileUtil.touchFile(in_progress_file)
    outputter.info(s"Creating relation set in $relation_set_dir")
    val pra_relations = (1 to num_pra_relations).toList.par.map(generatePraRelations)
    val instances = (new mutable.ArrayBuffer[(Int, String, Int, Boolean)],
      new mutable.ArrayBuffer[(Int, String, Int, Boolean)],
      new mutable.ArrayBuffer[(Int, String, Int)])
    val pra_instances = pra_relations.map(generateRelationInstances).seq
    for (rel_instances <- pra_instances) {
      instances._1 ++= rel_instances._1
      instances._2 ++= rel_instances._2
      instances._3 ++= rel_instances._3
    }
    instances._3 ++= relation_sets.par.flatMap(generateOverlappingInstances).seq
    instances._3 ++= generateNoiseInstances()
    outputRelationSet(instances._3)
    outputSplitFiles(instances._1.toSet, instances._2.toSet, pra_relations.map(_._1).seq.toSet)
    outputRules(pra_relations.flatMap(x =>
        x._2._1.map(y => (x._1, y._1, y._2, true)) ++
        x._2._2.map(y => (x._1, y._1, y._2, false))).seq)
    val out = fileUtil.getFileWriter(param_file)
    out.write(pretty(render(params)))
    out.close
    fileUtil.deleteFile(in_progress_file)
  }

  def generatePraRelations(relation_index: Int) = {
    val positive_rules = new mutable.ArrayBuffer[(Seq[Int], Double)]
    for (rule <- 1 to num_rules) {
      val relations = (1 to num_base_relations).toList
      val path_lengths = min_rule_length to max_rule_length
      val shuffled = r.shuffle(relations)
      val path_length = r.shuffle(path_lengths).head
      var rule_prob = r.nextGaussian() * rule_prob_stddev + rule_prob_mean
      if (rule_prob > 1.0) rule_prob = 1.0
      if (rule_prob < 0.0) rule_prob = 0.0
      positive_rules += Tuple2(shuffled.take(path_length), rule_prob)
    }
    val negative_rules = new mutable.ArrayBuffer[(Seq[Int], Double)]
    for (rule <- 1 to num_rules) {
      val relations = (1 to num_base_relations).toList
      val path_lengths = min_rule_length to max_rule_length
      val shuffled = r.shuffle(relations)
      val path_length = r.shuffle(path_lengths).head
      var rule_prob = r.nextGaussian() * rule_prob_stddev + rule_prob_mean
      if (rule_prob > 1.0) rule_prob = 1.0
      if (rule_prob < 0.0) rule_prob = 0.0
      negative_rules += Tuple2(shuffled.take(path_length), rule_prob)
    }
    (f"pra_${relation_index}%02d", (positive_rules.toSeq, negative_rules.toSeq))
  }

  def generateRelationInstances(pra_relation: (String, (Seq[(Seq[Int], Double)], Seq[(Seq[Int], Double)]))) = {
    val training_instances = new mutable.HashSet[(Int, String, Int, Boolean)]
    val testing_instances = new mutable.HashSet[(Int, String, Int, Boolean)]
    val graphEdges = new mutable.HashSet[(Int, String, Int)]
    for (i <- 1 to num_pra_relation_training_instances) {
      val instances = generatePraInstance(pra_relation._1, pra_relation._2._1, pra_relation._2._2, true)
      training_instances ++= instances._1
      testing_instances ++= instances._2
      graphEdges ++= instances._3
    }
    for (i <- 1 to num_pra_relation_testing_instances) {
      val instances = generatePraInstance(pra_relation._1, pra_relation._2._1, pra_relation._2._2, false)
      training_instances ++= instances._1
      testing_instances ++= instances._2
      graphEdges ++= instances._3
    }
    (training_instances.toSeq, testing_instances.toSeq, graphEdges.toSeq)
  }

  def generatePraInstance(
      name: String,
      rules: Seq[(Seq[Int], Double)],
      negative_rules: Seq[(Seq[Int], Double)],
      isTraining: Boolean) = {
    val training_instances = new mutable.HashSet[(Int, String, Int, Boolean)]
    val testing_instances = new mutable.HashSet[(Int, String, Int, Boolean)]
    val graphEdges = new mutable.HashSet[(Int, String, Int)]
    val source = r.nextInt(num_entities)
    val target = r.nextInt(num_entities)
    if (isTraining) {
      training_instances += Tuple4(source, name, target, true)
      graphEdges += Tuple3(source, name, target)
    } else {
      testing_instances += Tuple4(source, name, target, true)
    }
    graphEdges ++= rules.flatMap(generateSupportingEdges(source, target, isTraining))
    for (i <- 1 to num_negatives_per_positive) {
      val negativeTarget = r.nextInt(num_entities)
      if (isTraining) {
        training_instances += Tuple4(source, name, negativeTarget, false)
      } else {
        testing_instances += Tuple4(source, name, negativeTarget, false)
      }
      graphEdges ++= negative_rules.flatMap(generateSupportingEdges(source, negativeTarget, isTraining))
    }
    (training_instances.toSet, testing_instances.toSet, graphEdges.toSet)
  }

  def generateSupportingEdges(source: Int, target: Int, isTraining: Boolean)(rule: (Seq[Int], Double)) = {
    val edges = new mutable.HashSet[(Int, String, Int)]
    if (r.nextDouble < rule._2) {
      var i = 0
      var current_node = source
      while (i < rule._1.size - 1) {
        val next_node = r.nextInt(num_entities)
        val relation = getConcreteBaseRelation(rule._1(i), isTraining)
        edges += Tuple3(current_node, relation, next_node)
        current_node = next_node
        i += 1
      }
      val relation = getConcreteBaseRelation(rule._1(i), isTraining)
      edges += Tuple3(current_node, relation, target)
    }
    edges.toSet
  }

  def getConcreteBaseRelation(index: Int, isTraining: Boolean) = {
    val base_relation = relation_sets(index-1)
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
  }

  def outputSplitFiles(
      training_instances: Set[(Int, String, Int, Boolean)],
      testing_instances: Set[(Int, String, Int, Boolean)],
      pra_relations: Set[String]) {
    val relations_to_run_filename = s"$split_dir/relations_to_run.tsv"
    var out = fileUtil.getFileWriter(relations_to_run_filename)
    for (relation <- pra_relations) {
      out.write(relation)
      out.write("\n")
    }
    out.close
    val relation_instances: Map[String, (Set[(Int, Int, Boolean)], Set[(Int, Int, Boolean)])] = {
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
        val positive = if (instance._3) 1 else -1
        out.write(s"${instance._1}\t${instance._2}\t${positive}\n")
      }
      out.close
      out = fileUtil.getFileWriter(relation_dir + "testing.tsv")
      for (instance <- relation_instance_set._2._2) {
        val positive = if (instance._3) 1 else -1
        out.write(s"${instance._1}\t${instance._2}\t${positive}\n")
      }
      out.close
    }
  }

  def rekeyByRelation(instances: Set[(Int, String, Int, Boolean)]): Map[String, Set[(Int, Int, Boolean)]] = {
    instances.groupBy(_._2).mapValues(set => set.map(instance => (instance._1, instance._3, instance._4)))
  }

  def outputRules(rules: Seq[(String, Seq[Int], Double, Boolean)]) {
    val rules_file = s"$split_dir/rules.tsv"
    val out = fileUtil.getFileWriter(rules_file)
    for (rule <- rules) {
      val relations = rule._2.mkString("-")
      if (rule._4) {
        out.write(s"${rule._1}\t${relations}\t${rule._3}\n")
      } else {
        out.write(s"${rule._1}\t${relations}\t${rule._3} (NEGATIVE)\n")
      }
    }
    out.close
  }
}

// This bit of ugliness is required for proper testing.  I want to supply a fake instance of
// RelationSetCreator when testing the GraphCreator class, so I need to have a factory, so I don't
// have to call new RelationSetCreator...
// TODO(matt): No I don't.  I just need GraphCreator to have a method that creates the
// RelationSetCreator that I can override, that's all.
trait ISyntheticDataCreatorFactory {
  def getSyntheticDataCreator(base_dir: String, params: JValue, outputter: Outputter): SyntheticDataCreator = {
    getSyntheticDataCreator(base_dir, params, outputter, new FileUtil)
  }

  def getSyntheticDataCreator(base_dir: String, params: JValue, outputter: Outputter, fileUtil: FileUtil): SyntheticDataCreator
}

class SyntheticDataCreatorFactory extends ISyntheticDataCreatorFactory {
  def getSyntheticDataCreator(base_dir: String, params: JValue, outputter: Outputter, fileUtil: FileUtil) = {
    new SyntheticDataCreator(base_dir, params, outputter, fileUtil)
  }
}
