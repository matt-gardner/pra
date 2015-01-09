package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.SpecFileReader
import edu.cmu.ml.rtw.users.matt.util.FileHelper
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import java.io.File

import scala.collection.JavaConversions._
import scalax.io.Resource

object ExperimentRunner {

  val SPEC_DIR = "/experiment_specs/"
  val RESULTS_DIR = "/results/"

  def main(args: Array[String]) {
    val pra_base = args(0)
    val filter = if (args.length > 1) args(1) else ""
    runPra(pra_base, filter)

    // The GraphChi code doesn't do a good job at killing all of its threads, so we do so here.
    // Note that this means we need to set `fork in run := true` in build.sbt, so that we play
    // nicely with an sbt console.
    System.exit(0)
  }

  def runPra(pra_base: String, experiment_filter: String) {
    val experiment_spec_dir = s"${pra_base}/experiment_specs/"
    val experiment_specs = FileHelper.recursiveListFiles(new File(experiment_spec_dir), """.*\.spec$""".r)
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
    val spec_lines = new FileUtil().readLinesFromFile(spec_file)
    if (spec_lines.get(0).startsWith("load") || spec_lines.get(0).startsWith("{")) {
      val params = new SpecFileReader().readSpecFile(spec_file)
      new Driver().runPra(result_dir, params)
    } else {
      val settings = spec_lines.map(_.split("\t")).map(x => (x(0), x(1))).toMap
      new KbPraDriver().runPra(
        settings("kb files"),
        settings("graph files"),
        settings("split"),
        settings("param file"),
        result_dir)
    }
  }
}
