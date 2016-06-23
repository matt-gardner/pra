package edu.cmu.ml.rtw.pra.experiments

import java.io.File
import scala.util.Random

import edu.cmu.ml.rtw.pra.data.Split
import edu.cmu.ml.rtw.pra.operations.TopTenByPprScorer

import com.mattg.pipeline.Step
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.SpecFileReader

import com.typesafe.scalalogging.LazyLogging

import org.json4s._
import org.json4s.JsonDSL._

// Currently I'm just going to implement running the "Top Ten PPR Scorer" for whatever you have in
// your experiment spec.  I will take a standard json file, and mess with the params so that it
// works.  This is going to be somewhat hacky, but it'll do for now.
object PipelineRunner {
  val SPEC_DIR = "/experiment_specs/"
  val RESULTS_DIR = "/results/"
  val fileUtil = new FileUtil

  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Must supply a base directory as the first argument to ExperimentRunner")
      return
    }
    val pra_base = fileUtil.addDirectorySeparatorIfNecessary(args(0))
    val filter = args.toSeq.drop(1)
    run(pra_base, filter)

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

  def run(pra_base: String, experiment_filters: Seq[String]) {
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
    shuffled.map(runFromSpec(pra_base) _)
  }

  def runFromSpec(pra_base: String)(spec_file: File) {
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
    new FinalStep(params, pra_base, experiment, fileUtil).runPipeline()
  }
}

class FinalStep(
  params: JValue,
  baseDir: String,
  experimentDir: String,
  fileUtil: FileUtil
) extends Step(None, fileUtil) with LazyLogging {
  implicit val formats = DefaultFormats
  override val name = "Final Step"
  val validParams = Seq("graph", "split", "train split", "test split", "relation metadata", "operation")
  JsonHelper.ensureNoExtras(params, name, validParams)

  override val inProgressFile = experimentDir + "in_progress"

  val graphParams = params \ "graph"
  val metadataParams = params \ "relation metadata"

  // TODO(matt): allow for other kinds of operations here, instead of just running the Top Ten
  // stuff.
  val operationParams: JValue = {
    (params \ "operation" \ "type") match {
      case JString("train and test") => {
        (params \ "operation").removeField(_._1 == "type")
      }
      case _ => throw new IllegalStateException("Can't handle this yet...")
    }
  }

  val testSplitParams = (params \ "test split") match {
    case JNothing => params \ "split"
    case jval => jval
  }
  val trainSplitParams = (params \ "train split") match {
    case JNothing => params \ "split"
    case jval => jval
  }

  // The gist here is that we want to look at the split in the experiment spec, find all of the
  // relations, then create Scorer steps for each one, and put them as inputs to this step.  That
  // will make sure each relation gets run, and it will do them all independently.  And, we're
  // going to do this with the test split relations, because we don't need to bother training a
  // model for a relation that we're not going to score.
  val testSplit = Split.create(testSplitParams, baseDir, fileUtil)
  override val inputs: Set[(String, Option[Step])] = testSplit.relations.map(relation => {
    val trainerParams: JValue = operationParams merge
      ("graph" -> graphParams) ~
      ("relation metadata" -> metadataParams) ~
      ("split" -> trainSplitParams) ~
      ("relation" -> relation)

    val pprComputerParams: JValue = ("ppr computer" -> ("walks per source" -> 50) ~ ("num steps" -> 3))
    val separateTestSplitParams: JValue = (params \ "test split") match {
      case JNothing => JNothing
      case jval => ("split" -> jval)
    }
    val trainerInputParams: JValue = ("trainer" -> trainerParams)
    val scorerParams: JValue = pprComputerParams merge separateTestSplitParams merge trainerInputParams
    val scorer = new TopTenByPprScorer(scorerParams, baseDir, experimentDir, fileUtil)
    (scorer.scoresFile, Some(scorer))
  }).toSet
  override val outputs = Set[String]()

  override def _runStep() {
  }
}
