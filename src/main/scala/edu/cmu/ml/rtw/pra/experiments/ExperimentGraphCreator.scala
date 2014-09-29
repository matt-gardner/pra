package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.graphs.GraphCreator
import edu.cmu.ml.rtw.pra.graphs.RelationSet
import edu.cmu.ml.rtw.users.matt.util.FileHelper

import java.io.File

import scala.collection.mutable
import scala.collection.JavaConversions._
import scalax.io.Resource

object ExperimentGraphCreator {

  val GRAPH_DIR = "/graphs/"
  val GRAPH_SPEC_DIR = "/graph_specs/"

  def main(args: Array[String]) {
    val pra_base = args(0)
    val filter = if (args.length > 1) args(1) else ""
    createGraphs(pra_base, filter)

    // The GraphChi code doesn't do a good job at killing all of its threads, so we do so here.
    System.exit(0)
  }

  def createGraphs(pra_base: String, graph_filter: String) {
    val graph_base_dir = pra_base + GRAPH_DIR
    val graph_spec_dir = pra_base + GRAPH_SPEC_DIR
    val graph_specs = FileHelper.recursiveListFiles(new File(graph_spec_dir), """.*\.spec""".r)
    graph_specs.filter(_.getAbsolutePath().contains(graph_filter))
      .map(createGraphFromSpec(graph_base_dir) _)
  }

  def createGraphFromSpec(graph_base_dir: String)(spec_file: File) {
    println(s"Creating graph from spec file $spec_file")
    val graph = spec_file.getAbsolutePath.split(GRAPH_SPEC_DIR).last.replace(".spec", "")
    val graph_dir = graph_base_dir + graph
    if (new File(graph_dir).exists) {
      println(s"Graph directory $graph_dir already exists. Skipping...")
      return
    }
    val paramLines = Resource.fromFile(spec_file).lines().toList
    val relation_sets = paramLines.filter(_.startsWith("relation set\t"))
      .map(_.split("\t")(1))
      .map(RelationSet.fromFile(_)).toList
    val params = paramLines.filter(x => !x.startsWith("relation set\t"))
      .map(x => Tuple2(x.split("\t")(0), x.split("\t")(1))).toMap
    val deduplicatedEdges = params.get("deduplicate edges").getOrElse("false").toBoolean
    val graph_creator = new GraphCreator(relation_sets, graph_dir, deduplicatedEdges)
    graph_creator.createGraphChiRelationGraph()
  }
}
