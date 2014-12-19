package edu.cmu.ml.rtw.pra.experiments

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable
import scala.math.Ordering.Implicits._
import scalax.io.Resource

import edu.cmu.ml.rtw.users.matt.util.FileHelper

object ExperimentScorer {

  val DATASET_RELATION = "__DATASET__"
  val DISPLAY_NAME = "__DISPLAY_NAME__"
  val TIMESTAMP = "__TIMESTAMP__"
  val SAVED_METRICS = "saved_metrics.tsv"
  val RESULTS_DIR = "/results/"

  val metricComputers_ = List(
    BasicMetricComputer
    )
  val sortResultsBy_ = List("-MAP", "-MRR")
  val displayMetrics_ = List(
    ("MAP", "MAP"),
    ("MMAP", "MMAP"),
    ("MRR", "MRR"),
    ("MMRR", "MMRR"),
    ("MMAP Predicted", "MMAP+"),
    ("MMRR Predicted", "MMRR+")
  )

  def main(args: Array[String]) {
    val pra_base = args(0)
    val filter = if (args.length > 1) args(1) else ""
    scoreExperiments(pra_base, filter, displayMetrics_, sortResultsBy_, metricComputers_)
  }

  def scoreExperiments(
      pra_base: String,
      experiment_filter: String,
      displayMetrics: List[(String, String)],
      sortResultsBy: List[String],
      metricComputers: List[MetricComputer]) {
    val results_dir = pra_base + RESULTS_DIR
    val experiment_dirs = FileHelper.recursiveListFiles(new File(results_dir), """settings.txt""".r)
      .map(_.getParentFile)
      .filter(_.getAbsolutePath.contains(experiment_filter))

    var greatest_common_path = experiment_dirs.last.getParentFile
    var all_in_common = false
    while (!all_in_common) {
      all_in_common = true
      for (experiment_dir <- experiment_dirs) {
        if (!experiment_dir.getAbsolutePath.startsWith(greatest_common_path.getAbsolutePath)) {
          all_in_common = false
        }
      }
      if (!all_in_common) {
        greatest_common_path = greatest_common_path.getParentFile
      }
    }
    val displayNameSplit = greatest_common_path.getAbsolutePath + "/"

    val savedMetricsFile = results_dir + SAVED_METRICS
    val savedMetrics = readSavedMetrics(savedMetricsFile)
    val metrics = EmptyExperimentMetricsWithDefaults

    for (experiment_dir <- experiment_dirs) {
      val experiment_name = experiment_dir.getAbsolutePath().split(RESULTS_DIR).last
      val experiment_metrics = computeMetrics(
        experiment_dir.getAbsolutePath(),
        displayNameSplit,
        savedMetrics.get(experiment_name), metricComputers)
      if (experiment_metrics.size > 0) {
        metrics(experiment_name) = experiment_metrics
        savedMetrics(experiment_name) = experiment_metrics
      }
    }
    val finishedMetrics = makeExperimentMetricsImmutable(metrics)
    displayExperiments(finishedMetrics, displayMetrics, sortResultsBy)
    saveMetrics(makeExperimentMetricsImmutable(savedMetrics), savedMetricsFile)
  }

  def displayExperiments(
      metrics: ExperimentMetrics,
      displayMetrics: List[(String, String)],
      sortResultsBy: List[String]) {
    println()
    val experiment_title = "Experiment"
    val sortKeyFunction = getSortKey(sortResultsBy) _
    val experiments = metrics.map(_._1).toList.sortBy(x => sortKeyFunction(metrics(x)))
    val experiment_header = "Experiment"
    print(f"$experiment_header%-35s")
    for (metricHeader <- displayMetrics.map(_._2)) {
      print(f"$metricHeader%15s")
    }
    println()
    for (experiment <- experiments) {
      val displayName = metrics(experiment)(DISPLAY_NAME).keys.toList(0)
      print(f"${displayName}%-35s")
      for (displayMetric <- displayMetrics) {
        try {
          print(f"${metrics(experiment)(DATASET_RELATION)(displayMetric._1)}%15.4f")
        } catch {
          case e: java.util.NoSuchElementException => {
            val message = "No value"
            print(f"${message}%15s")
          }
        }
      }
      println()
    }
  }

  def computeMetrics(
      experiment_dir: String,
      displayNameSplit: String,
      saved_metrics: Option[MutableRelationMetrics],
      metricComputers: List[MetricComputer]): MutableRelationMetrics = {
    println(s"Getting metrics for experiment $experiment_dir")
    val metrics = EmptyRelationMetricsWithDefaults
    // Getting the split dir and relations first.
    val settings_file = s"$experiment_dir/settings.txt"
    if (!(new File(settings_file).exists())) return metrics
    val split_dir = Resource.fromFile(settings_file).lines()
      .filter(_.startsWith("Splits used")).map(_.split(": ")(1)).toList(0)
    val relations = Resource.fromFile(split_dir + "/relations_to_run.tsv").lines().toList

    // Now we loop over the relations and compute metrics, checking the saved metrics and timestamp
    // first to see if we need recompute this.
    var relations_seen = 0
    var last_timestamp = -1L
    for (relation <- relations) {
      val results_file = s"$experiment_dir/$relation/scores.tsv"
      if (new File(results_file).exists()) {
        relations_seen += 1
        val timestamp = new File(results_file).lastModified
        if (timestamp > last_timestamp) last_timestamp = timestamp
        // TODO(matt): check timestamps here
        if (saved_metrics == None
            || !saved_metrics.get.isDefinedAt(relation)
            || !saved_metrics.get(relation).isDefinedAt(TIMESTAMP)
            || saved_metrics.get(relation)(TIMESTAMP) < timestamp) {
          metrics(relation)(TIMESTAMP) = timestamp
          println(s"Computing metrics for relation $relation")
          for (metricComputer <- metricComputers) {
            val test_split_file = s"$split_dir/$relation/testing.tsv"
            val relation_metrics = metricComputer.computeRelationMetrics(results_file, test_split_file)
            metrics.update(relation, relation_metrics ++ metrics(relation))
          }
        } else {
          metrics.update(relation, metrics(relation) ++ saved_metrics.get(relation))
        }
      }
    }

    metrics(DISPLAY_NAME) = EmptyMetricsWithDefault
    var name = experiment_dir.split(displayNameSplit).last
    val num_relations_left = relations.size - relations_seen
    if (num_relations_left != 0) {
      name += s" (not done: ${num_relations_left})"
    }
    metrics(DISPLAY_NAME)(name) = 1

    var timestamp = -1.0
    if (saved_metrics != None && saved_metrics.get.isDefinedAt(DATASET_RELATION) &&
        saved_metrics.get(DATASET_RELATION).isDefinedAt(TIMESTAMP)) {
      timestamp = saved_metrics.get(DATASET_RELATION)(TIMESTAMP)
    }
    if (last_timestamp > timestamp) {
      println("Computing dataset metrics")
      metrics(DATASET_RELATION)(TIMESTAMP) = last_timestamp
      val relation_metrics = metrics.filter(x => x._1 != DATASET_RELATION && x._1 != DISPLAY_NAME)

      for (metricComputer <- metricComputers) {
        val dataset_metrics =
          metricComputer.computeDatasetMetrics(
            experiment_dir,
            split_dir,
            makeRelationMetricsImmutable(relation_metrics))
        metrics.update(DATASET_RELATION, dataset_metrics ++ metrics(DATASET_RELATION))
      }
    } else if (saved_metrics != None) {
      metrics.update(DATASET_RELATION, metrics(DATASET_RELATION) ++ saved_metrics.get(DATASET_RELATION))
    } else {
      metrics.update(DATASET_RELATION, metrics(DATASET_RELATION))
    }
    metrics
  }

  def getSortKey(keys: List[String])(metrics: RelationMetrics) = {
    val entries = new mutable.ListBuffer[Double]
    for (key <- keys) {
      try {
        if (key.charAt(0) == '-') {
          entries += -metrics(DATASET_RELATION)(key.substring(1))
        } else {
          entries += metrics(DATASET_RELATION)(key)
        }
      } catch {
        case e: java.util.NoSuchElementException => {
          if (key.charAt(0) == '-') {
            entries += 1.0
          } else {
            entries += -1.0
          }
        }
      }
    }
    entries.toList
  }

  def saveMetrics(metrics: ExperimentMetrics, metrics_file: String) {
    val out = new PrintWriter(metrics_file)
    for (experiment <- metrics.keys;
         relation <- metrics(experiment).keys;
         metric <- metrics(experiment)(relation).keys) {
      val value = metrics(experiment)(relation)(metric)
      out.println(s"$experiment\t$relation\t$metric\t$value")
    }
    out.close()
  }

  def readSavedMetrics(metrics_file: String) = {
    val metrics = new mutable.HashMap[String, MutableRelationMetrics]
    if (new File(metrics_file).exists) {
      for (line <- Resource.fromFile(metrics_file).lines()) {
        val fields = line.split("\t")
        val experiment = fields(0)
        val relation = fields(1)
        val metric = fields(2)
        val value = fields(3).toDouble
        if (!metrics.isDefinedAt(experiment)) {
          metrics(experiment) = new mutable.HashMap[String, MutableMetrics]
        }
        if (!metrics(experiment).isDefinedAt(relation)) {
          metrics(experiment)(relation) = new mutable.HashMap[String, Double]
        }
        metrics(experiment)(relation)(metric) = value
      }
    }
    metrics
  }
}
