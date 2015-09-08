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
  val my_spatial_format = "spatial_relationships_%s%s.tsv"
  val my_classifier_format = "classifier_output_%s%s.tsv"
  val my_neil_classifier_format = "neil_classifier_output_%s%s.tsv"
  val my_ground_truth_format = "ground_truth_%s%s.tsv"

  val relset_base = "/home/mg1/pra/param_files/relation_sets/images/"

  val datasets = Seq("train", "test", "val")

  def main(args: Array[String]) {
    val car_images = fileUtil.readLinesFromFile("/home/mg1/data/neil/car_images.tsv").asScala.toSet
    val suffix = "_car"
    copy_and_fix_data(car_images, suffix)
    createRelationSets(suffix)
  }

  def patchIsFirstFilter(allowed_images: Set[String])(line: String) = {
    if (allowed_images.size == 0) {
      true
    } else {
      val image = line.split("_")(0)
      if (allowed_images.contains(image)) {
        true
      } else {
        false
      }
    }
  }

  def convertSpatialFileLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1)
    val rel = fields(2)
    s"${src}\t${rel}\t${target}"
  }

  def spatialFilter(allowed_images: Set[String])(line: String) = patchIsFirstFilter(allowed_images)(line)

  def convertClassifierLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1).split("_").last
    val rel = "CLASSIFIER"
    s"${src}\t${rel}\t${target}"
  }

  def classifierFilter(allowed_images: Set[String])(line: String) = patchIsFirstFilter(allowed_images)(line)

  def convertNeilClassifierLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1).replace("NEIL_", "")
    val rel = "NEIL_CLASSIFIER"
    s"${src}\t${rel}\t${target}"
  }

  def neilClassifierFilter(allowed_images: Set[String])(line: String) = patchIsFirstFilter(allowed_images)(line)

  def convertGroundTruthLine(line: String) = {
    val fields = line.split("\t")
    val src = fields(0)
    val target = fields(1)
    val rel = "GROUND_TRUTH"
    s"${src}\t${rel}\t${target}"
  }

  def groundTruthFilter(allowed_images: Set[String])(line: String) = patchIsFirstFilter(allowed_images)(line)

  def convertSpatialFile(file_type: String, allowed_images: Set[String], suffix: String) {
    println(s"Converting $file_type spatial relationship file")
    val lines = fileUtil.readLinesFromFile(spatial_base + x_spatial_format.format(file_type)).asScala
    val new_lines = lines.par.filter(spatialFilter(allowed_images)).map(convertSpatialFileLine).seq
    fileUtil.writeLinesToFile(data_base + my_spatial_format.format(file_type, suffix), new_lines.asJava)
  }

  def convertClassifierFile(file_type: String, allowed_images: Set[String], suffix: String) {
    println(s"Converting $file_type classifier file")
    val lines = fileUtil.readLinesFromFile(classifier_base + x_classifier_format.format(file_type)).asScala
    val new_lines = lines.par.filter(classifierFilter(allowed_images)).map(convertClassifierLine).seq
    fileUtil.writeLinesToFile(data_base + my_classifier_format.format(file_type, suffix), new_lines.asJava)
  }

  def convertGroundTruthFile(file_type: String, allowed_images: Set[String], suffix: String) {
    println(s"Converting $file_type ground truth file")
    val lines = fileUtil.readLinesFromFile(ground_truth_base + x_ground_truth_format.format(file_type)).asScala
    val new_lines = lines.par.filter(groundTruthFilter(allowed_images)).map(convertGroundTruthLine).seq
    fileUtil.writeLinesToFile(data_base + my_ground_truth_format.format(file_type, suffix), new_lines.asJava)
  }

  def convertNeilClassifierFile(file_type: String, allowed_images: Set[String], suffix: String) {
    println(s"Converting $file_type neil classifier files")
    val neil_base = neil_classifier_base + x_neil_classifier_format.format(file_type)
    val files = FileHelper.recursiveListFiles(new File(neil_base), """.*txt""".r)
    val new_lines = files.par.flatMap(file => {
      val lines = fileUtil.readLinesFromFile(file).asScala
      lines.filter(neilClassifierFilter(allowed_images)).map(convertNeilClassifierLine)
    }).seq
    fileUtil.writeLinesToFile(data_base + my_neil_classifier_format.format(file_type, suffix), new_lines.asJava)
  }

  def copy_and_fix_data(allowed_images: Set[String] = Set(), suffix: String = "") {
    for (file_type <- datasets) {
      convertSpatialFile(file_type, allowed_images, suffix)
      convertClassifierFile(file_type, allowed_images, suffix)
      convertGroundTruthFile(file_type, allowed_images, suffix)
      convertNeilClassifierFile(file_type, allowed_images, suffix)
    }
  }

  def createRelationSets(suffix: String = "") {
    val file_types = Seq("neil_kb", "spatial_relationships", "neil_classifier_output", "ground_truth")
    val template = """{
      |  "relation file": "%s",
      |  "is kb": false
      |}""".stripMargin
    for (dataset <- datasets) {
      for (file_type <- file_types) {
        val relset_file = relset_base + file_type + "_" + dataset + suffix + ".json"
        val relation_file = data_base + file_type + "_" + dataset + suffix + ".tsv"
        val contents = template.format(relation_file)
        fileUtil.writeLinesToFile(relset_file, contents.split("\n").toList.asJava)
      }
    }

  }
}
