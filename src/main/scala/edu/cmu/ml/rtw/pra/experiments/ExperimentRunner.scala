package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.users.matt.util.FileHelper
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.SpecFileReader

import java.io.File

import scala.util.Random

import org.json4s.{JNothing,JString}

object ExperimentRunner {

  val SPEC_DIR = "/experiment_specs/"
  val RESULTS_DIR = "/results/"
  val EXPLORATION_DIR = "/results_exploration/"

  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Must supply a base directory as the first argument to ExperimentRunner")
      return
    }
    val pra_base = new FileUtil().addDirectorySeparatorIfNecessary(args(0))
    val filter = args.toList.drop(1)
    runPra(pra_base, filter)

    // The GraphChi code doesn't do a good job at killing all of its threads, so we do so here.
    // Note that this means we need to set `fork in run := true` in build.sbt, so that we play
    // nicely with an sbt console.
    System.exit(0)
  }

  def shouldKeepFile(filters: Seq[String])(file: File): Boolean = {
    if (filters.size == 0) return true
    for (filter <- filters) {
      if (file.getAbsolutePath.contains(filter)) return true
    }
    false
  }

  def runPra(pra_base: String, experiment_filters: Seq[String]) {
    val random = new Random
    val experiment_spec_dir = s"${pra_base}/experiment_specs/"
    val experiment_specs = FileHelper.recursiveListFiles(new File(experiment_spec_dir), """.*\.json$""".r)
    if (experiment_specs.size == 0) {
      println("No experiment specs found.  Check your base path (the first argument).")
    }
    val filtered = experiment_specs.filter(shouldKeepFile(experiment_filters))
    println(s"Found ${experiment_specs.size} experiment specs, and kept ${filtered.size} of them")
    if (filtered.size == 0) {
      println("No experiment specs kept after filtering.  Check your filters (all arguments after "
        + "the first one).")
    }
    val shuffled = random.shuffle(filtered)
    shuffled.map(runPraFromSpec(pra_base) _)
  }

  def runPraFromSpec(pra_base: String)(spec_file: File) {
    val spec_lines = new FileUtil().readLinesFromFile(spec_file)
    val params = new SpecFileReader(pra_base).readSpecFile(spec_file)
    // TODO(matt): this is pretty ugly.  Can't we design this better?
    val mode = (params \ "operation" \ "type") match {
      case JNothing => "no op"
      case JString(m) => m
      case other => throw new IllegalStateException("something is wrong in specifying the pra mode")
    }
    val result_base_dir = if (mode == "explore graph") pra_base + EXPLORATION_DIR else pra_base + RESULTS_DIR
    val experiment_spec_dir = pra_base + SPEC_DIR
    println(s"Running PRA from spec file $spec_file")
    val experiment = spec_file.getAbsolutePath().split(SPEC_DIR).last.replace(".json", "")
    val result_dir = result_base_dir + experiment
    if (new File(result_dir).exists) {
      println(s"Result directory $result_dir already exists. Skipping...")
      return
    }
    new Driver(pra_base).runPra(result_dir, params)
  }
}
