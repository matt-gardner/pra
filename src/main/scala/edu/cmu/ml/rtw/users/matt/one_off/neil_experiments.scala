package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

object neil_experiments {
  val fileUtil = new FileUtil

  val spatial_base = "/home/xinleic/GRA/rcnn2007_2007_PGMM4TTNMS_0.5/"
  val x_spatial_format = "TXT_%s_0.8.txt"
  val classifier_base = "/home/xinleic/GRA/rcnn2007_trainval_2007_SOFTMAX/"
  val x_classifier_format = "%s_0.7.txt"
  val ground_truth_base = "/home/xinleic/GRA/rcnn2007_trainval_2007_GT/"
  val x_ground_truth_format = "%s.txt"

  val data_base = "/home/mg1/data/neil/"
  val my_spatial_format = "spatial_relationships_%s.tsv"
  val my_classifier_format = "classifier_output_%s.tsv"
  val my_ground_truth_format = "ground_truth_%s.tsv"

  val relset_base = "/home/mg1/pra/param_files/relation_sets/images/"

  val datasets = Seq("train", "test", "val")

  def main(args: Array[String]) {
    //copy_and_fix_data()
    createRelationSets()
  }

  def convertSpatialFileLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1)
    val rel = fields(2)
    s"${src}\t${rel}\t${target}"
  }

  def convertClassifierLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1).split("_").last
    val rel = "CLASSIFIER"
    s"${src}\t${rel}\t${target}"
  }

  def convertGroundTruthLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1)
    val rel = "GROUND_TRUTH"
    s"${src}\t${rel}\t${target}"
  }

  def copy_and_fix_data() {
    for (file_type <- datasets) {
      println(s"Converting $file_type spatial relationship file")
      var lines = fileUtil.readLinesFromFile(spatial_base + x_spatial_format.format(file_type)).asScala
      var new_lines = lines.par.map(convertSpatialFileLine).seq
      fileUtil.writeLinesToFile(data_base + my_spatial_format.format(file_type), new_lines.asJava)
      println(s"Converting $file_type classifier file")
      lines = fileUtil.readLinesFromFile(classifier_base + x_classifier_format.format(file_type)).asScala
      new_lines = lines.par.map(convertClassifierLine).seq
      fileUtil.writeLinesToFile(data_base + my_classifier_format.format(file_type), new_lines.asJava)
      println(s"Converting $file_type ground truth file")
      lines = fileUtil.readLinesFromFile(ground_truth_base + x_ground_truth_format.format(file_type)).asScala
      new_lines = lines.par.map(convertGroundTruthLine).seq
      fileUtil.writeLinesToFile(data_base + my_ground_truth_format.format(file_type), new_lines.asJava)
    }
  }

  def createRelationSets() {
    val file_types = Seq("spatial_relationships", "classifier_output", "ground_truth")
    val template = """{
      |  "relation file": "%s",
      |  "is kb": false
      |}""".stripMargin
    for (dataset <- datasets) {
      for (file_type <- file_types) {
        val relset_file = relset_base + file_type + "_" + dataset + ".json"
        val relation_file = data_base + file_type + "_" + dataset + ".tsv"
        val contents = template.format(relation_file)
        fileUtil.writeLinesToFile(relset_file, contents.split("\n").toList.asJava)
      }
    }

  }
}
