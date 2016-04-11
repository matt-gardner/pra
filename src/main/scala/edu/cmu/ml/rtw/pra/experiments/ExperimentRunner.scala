package edu.cmu.ml.rtw.pra.experiments

import com.mattg.util.FileUtil
import com.mattg.util.SpecFileReader

import java.io.File

import scala.util.Random

import org.json4s.{JNothing,JString}

object ExperimentRunner {

  val SPEC_DIR = "/experiment_specs/"
  val RESULTS_DIR = "/results/"
  val fileUtil = new FileUtil

  // TODO(matt): This whole thing should be restructed to use my pipeline architecture.  Then you
  // can move from having both ExperimentRunner and ExperimentScorer to having just one main entry
  // point that will run and score experiments.  A few things have to change for that, though, and
  // I don't actually use this code to run experiments anymore (I now use it as a library to
  // generate features for other, more interesting experiments).  So, this will wait until I care
  // enough to make the change...
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Must supply a base directory as the first argument to ExperimentRunner")
      return
    }
    val pra_base = fileUtil.addDirectorySeparatorIfNecessary(args(0))
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
    val experiment_specs = fileUtil.recursiveListFiles(new File(experiment_spec_dir), """.*\.json$""".r)
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
    val spec_lines = fileUtil.readLinesFromFile(spec_file)
    val params = new SpecFileReader(pra_base).readSpecFile(spec_file)
    val result_base_dir = pra_base + RESULTS_DIR
    val experiment_spec_dir = pra_base + SPEC_DIR
    println(s"Running PRA from spec file $spec_file")
    val experiment = spec_file.getAbsolutePath().split(SPEC_DIR).last.replace(".json", "")
    val result_dir = result_base_dir + experiment
    if (new File(result_dir).exists) {
      println(s"Result directory $result_dir already exists. Skipping...")
      return
    }
    new Driver(pra_base, experiment, params, fileUtil).runPipeline()
  }
}
