package edu.cmu.ml.rtw.pra.experiments

import java.io.File

import scala.collection.mutable
import scalax.io.Resource

import edu.cmu.ml.rtw.pra.experiments.PathExplorer
import edu.cmu.ml.rtw.users.matt.util.FileHelper

object ExperimentExplorer {

  val SPEC_DIR = "/experiment_specs/"
  val EXPLORATION_DIR = "/exploration_results/"

  def main(args: Array[String]) {
    val pra_base = args(0)
    val filter = if (args.length > 1) args(1) else ""
    exploreFeatures(pra_base, filter)

    // The GraphChi code doesn't do a good job at killing all of its threads, so we do so here.
    System.exit(0)
  }

  def exploreFeatures(pra_base: String, experiment_filter: String) {
    val experiment_spec_dir = pra_base + SPEC_DIR
    val exploration_base_dir = pra_base + EXPLORATION_DIR
    val experiment_specs = FileHelper.recursiveListFiles(new File(experiment_spec_dir), """.*\.spec""".r)
    experiment_specs.filter(_.getAbsolutePath().contains(experiment_filter))
      .map(exploreFeaturesFromSpec(exploration_base_dir) _)
  }

  def exploreFeaturesFromSpec(exploration_base_dir: String)(spec_file: File) {
    println(s"Running PRA from spec file $spec_file")
    val experiment = spec_file.getAbsolutePath().split(SPEC_DIR).last.replace(".spec", "")
    val exploration_out_dir = exploration_base_dir + experiment
    if (new File(exploration_out_dir).exists) {
      println(s"Result directory $exploration_out_dir already exists. Skipping...")
      return
    }
    val settings = Resource.fromFile(spec_file).lines().map(_.split("\t")).map(x => (x(0), x(1))).toMap
    val split_dir = settings("split")
    val relations = Resource.fromFile(split_dir + "relations_to_run.tsv").lines()
    for (relation <- relations) {
      val relation_file = s"${split_dir}${relation}/testing.tsv"
      val output_base = s"${exploration_out_dir}${relation}/"
      println(s"Getting test-set features for relation ${relation}")
      PathExplorer.exploreFeatures(
        settings("graph files"),
        relation_file,
        settings("param file"),
        output_base)
    }
  }
}
