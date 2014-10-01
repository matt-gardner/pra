package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.users.matt.util.FileHelper

import java.io.File

import scalax.io.Resource

object ExperimentRunner {

  val SPEC_DIR = "/experiment_specs/"
  val RESULTS_DIR = "/results/"

  def main(args: Array[String]) {
    val pra_base = args(0)
    val filter = if (args.length > 1) args(1) else ""
    runPra(pra_base, filter)

    // The GraphChi code doesn't do a good job at killing all of its threads, so we do so here.
    System.exit(0)
  }

  def runPra(pra_base: String, experiment_filter: String) {
    val experiment_spec_dir = s"${pra_base}/experiment_specs/"
    val experiment_specs = FileHelper.recursiveListFiles(new File(experiment_spec_dir), """.*\.spec""".r)
    experiment_specs.filter(_.getAbsolutePath().contains(experiment_filter))
      .map(runPraFromSpec(pra_base) _)
  }

  def runPraFromSpec(pra_base: String)(spec_file: File) {
    val experiment_spec_dir = pra_base + SPEC_DIR
    val result_base_dir = pra_base + RESULTS_DIR
    println(s"Running PRA from spec file $spec_file")
    val experiment = spec_file.getAbsolutePath().split(SPEC_DIR).last.replace(".spec", "")
    val result_dir = result_base_dir + experiment
    if (new File(result_dir).exists) {
      println(s"Result directory $result_dir already exists. Skipping...")
      return
    }
    val settings = Resource.fromFile(spec_file).lines().map(_.split("\t")).map(x => (x(0), x(1))).toMap
    new KbPraDriver().runPra(
      settings("kb files"),
      settings("graph files"),
      settings("split"),
      settings("param file"),
      result_dir)
  }
}
