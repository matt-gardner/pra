package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

import java.io.File

object remove_edges_from_graph {

  val fileUtil = new FileUtil

  def remove_edges(graph_dir: String, new_dir: String, split: String, use_triples: Boolean) {
    val node_dict = new Dictionary()
    node_dict.setFromFile(graph_dir + "node_dict.tsv")
    val edge_dict = new Dictionary()
    edge_dict.setFromFile(graph_dir + "edge_dict.tsv")
    val to_remove = readSplit(split, node_dict, edge_dict, use_triples)
    println(s"Node pairs to remove: ${to_remove.size}")
    copyGraphChiEdges(graph_dir, new_dir, to_remove, use_triples)
  }

  def readSplit(
      split_dir: String,
      node_dict: Dictionary,
      edge_dict: Dictionary,
      use_triples: Boolean): Set[(Int, Int, Int)] = {
    val relations = fileUtil.listDirectoryContents(split_dir).asScala
      .map(x => split_dir + x + "/").filter(x => new File(x).isDirectory)
    val instances =
      for (relation <- relations;
           instance_file <- fileUtil.listDirectoryContents(relation).asScala)
             yield {
               (relation.split("/").last, fileUtil.readLinesFromFile(relation + instance_file).asScala) }
    instances.flatMap(relation_instances => {
      val rel_index = if (use_triples) edge_dict.getIndex(relation_instances._1) else -1
      relation_instances._2.map(line => {
        val fields = line.split("\t")
        val source = node_dict.getIndex(fields(0))
        val target = node_dict.getIndex(fields(1))
        (source, rel_index, target)
      })
    }).toSet
  }

  def copyGraphChiEdges(
      graph_dir: String,
      new_dir: String,
      to_remove: Set[(Int, Int, Int)],
      use_triples: Boolean) {
    println("Filtering edges from graph")
    fileUtil.mkdirOrDie(new_dir)
    fileUtil.mkdirs(new_dir + "graph_chi/")
    val new_edge_file = new_dir + "graph_chi/edges.tsv"
    val out = fileUtil.getFileWriter(new_edge_file)
    val old_edge_file = graph_dir + "graph_chi/edges.tsv"
    fileUtil.readLinesFromFile(old_edge_file).asScala.par.filter(line => {
      val fields = line.split("\t").map(_.toInt)
      val source = fields(0)
      val target = fields(1)
      val relation = if (use_triples) fields(2) else -1
      !to_remove.contains((source, relation, target))
    }).seq.map(line => {
      out.write(line)
      out.write("\n")
    })
    out.close()
    println("Copying other files")
    val files_to_copy = Seq("node_dict.tsv", "edge_dict.tsv", "num_shards.tsv", "params.json")
    files_to_copy.map(f => fileUtil.copy(graph_dir + f, new_dir + f))
  }

  def main(args: Array[String]) {
    val graph_dir = "/home/mg1/pra/graphs/nell/kb/"
    val new_dir = "/home/mg1/pra/graphs/testing/"
    val split = "/home/mg1/pra/splits/nell_dev/"
    val use_triples = false
    remove_edges(graph_dir, new_dir, split, use_triples)
  }
}
