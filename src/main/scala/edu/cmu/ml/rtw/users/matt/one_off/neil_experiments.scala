package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileHelper
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

import java.io.File

object neil_experiments {
  val fileUtil = new FileUtil

  val spatial_base = "/home/xinleic/GRA/rcnn2007_2007_PGMM4TTNMS_0.5/"
  val x_spatial_format = "TXT_%s_0.8.txt"
  val classifier_base = "/home/xinleic/GRA/rcnn2007_trainval_2007_SOFTMAX/"
  val x_classifier_format = "%s_0.7.txt"
  val neil_classifier_base = "/home/xinleic/GRA/pos_rcnn2007_dump/"
  val x_neil_classifier_format = "%s/"
  val ground_truth_base = "/home/xinleic/GRA/rcnn2007_trainval_2007_GT/"
  val x_ground_truth_format = "%s.txt"

  val data_base = "/home/mg1/data/neil/"
  val my_spatial_format = "spatial_relationships_%s.tsv"
  val my_classifier_format = "classifier_output_%s.tsv"
  val my_neil_classifier_format = "neil_classifier_output_%s.tsv"
  val my_ground_truth_format = "ground_truth_%s.tsv"

  val relset_base = "/home/mg1/pra/param_files/relation_sets/images/"

  val datasets = Seq("train", "test", "val")

  def main(args: Array[String]) {
    copy_and_fix_data()
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

  def convertNeilClassifierLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1).replace("NEIL_", "")
    val rel = "NEIL_CLASSIFIER"
    s"${src}\t${rel}\t${target}"
  }

  def convertGroundTruthLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1)
    val rel = "GROUND_TRUTH"
    s"${src}\t${rel}\t${target}"
  }

  def convertSpatialFile(file_type: String) {
    println(s"Converting $file_type spatial relationship file")
    val lines = fileUtil.readLinesFromFile(spatial_base + x_spatial_format.format(file_type)).asScala
    val new_lines = lines.par.map(convertSpatialFileLine).seq
    fileUtil.writeLinesToFile(data_base + my_spatial_format.format(file_type), new_lines.asJava)
  }

  def convertClassifierFile(file_type: String) {
    println(s"Converting $file_type classifier file")
    val lines = fileUtil.readLinesFromFile(classifier_base + x_classifier_format.format(file_type)).asScala
    val new_lines = lines.par.map(convertClassifierLine).seq
    fileUtil.writeLinesToFile(data_base + my_classifier_format.format(file_type), new_lines.asJava)
  }

  def convertGroundTruthFile(file_type: String) {
    println(s"Converting $file_type ground truth file")
    val lines = fileUtil.readLinesFromFile(ground_truth_base + x_ground_truth_format.format(file_type)).asScala
    val new_lines = lines.par.map(convertGroundTruthLine).seq
    fileUtil.writeLinesToFile(data_base + my_ground_truth_format.format(file_type), new_lines.asJava)
  }

  def convertNeilClassifierFile(file_type: String) {
    println(s"Converting $file_type neil classifier files")
    val neil_base = neil_classifier_base + x_neil_classifier_format.format(file_type)
    val files = FileHelper.recursiveListFiles(new File(neil_base), """.*txt""".r)
    val new_lines = files.par.flatMap(file => {
      val lines = fileUtil.readLinesFromFile(file).asScala
      lines.map(convertNeilClassifierLine)
    }).seq
    fileUtil.writeLinesToFile(data_base + my_neil_classifier_format.format(file_type), new_lines.asJava)
  }

  def copy_and_fix_data() {
    for (file_type <- datasets) {
      //convertSpatialFile(file_type)
      //convertClassifierFile(file_type)
      //convertGroundTruthFile(file_type)
      convertNeilClassifierFile(file_type)
    }
  }

  def createRelationSets() {
    val file_types = Seq("neil_kb", "spatial_relationships", "neil_classifier_output", "ground_truth")
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
