package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

object merge_parafac_matrices {
  val fileUtil = new FileUtil

  def loadParafacDir(dir: String): Map[Int, Seq[(Int, Int, Double)]] = {
    fileUtil.listDirectoryContents(dir).asScala.par.map(filename => {
      val rel_index = filename.split("slice")(1).split("-")(0).toInt
      val entries = fileUtil.readLinesFromFile(dir + filename).asScala.map(line => {
        val fields = line.split("\t")
        (fields(0).toInt, fields(1).toInt, fields(2).toDouble)
      }).toSeq
      (rel_index, entries)
    }).seq.toMap
  }

  def readMatrixFile(filename: String): Map[Int, Seq[(Int, Int, Double)]] = {
    println(s"Reading file $filename")
    var current_relation = -1
    val matrices = new mutable.HashMap[Int, Seq[(Int, Int, Double)]]
    var entries: mutable.ArrayBuffer[(Int, Int, Double)] = null
    for (line <- fileUtil.readLinesFromFile(filename).asScala) {
      if (line.startsWith("Relation")) {
        if (current_relation != -1) {
          matrices += (current_relation -> entries.toSeq)
        }
        val rel_index = line.split(" ")(1).toInt
        entries = new mutable.ArrayBuffer[(Int, Int, Double)]
        current_relation = rel_index
      } else {
        val fields = line.split("\t")
        try {
          if (fields.length == 2) {
            entries += Tuple3(fields(0).toInt, fields(1).toInt, 1.0)
          } else {
            entries += Tuple3(fields(0).toInt, fields(1).toInt, fields(2).toInt)
          }
        } catch {
          case e: NumberFormatException => {
            println(line)
            throw e
          }
        }
      }
    }
    matrices += (current_relation -> entries.toSeq)
    matrices.toMap
  }

  def outputMatrixFile(filename: String, matrices: Map[Int, Seq[(Int, Int, Double)]]) {
    val out = fileUtil.getFileWriter(filename)
    val keys = matrices.keySet.toList.sorted
    for (key <- keys) {
      out.write(s"Relation ${key}\n")
      for (entry <- matrices(key)) {
        out.write(s"${entry._1}\t${entry._2}\t${entry._3}\n")
      }
    }
    out.close
  }

  def NOT_main(args: Array[String]) {
    val matrix_dir = "/home/mg1/pra/graphs/nell/kb-t_svo/matrices/" // args(0)
    val parafac_dir = "/home/mg1/pra/tmp/" // args(1)
    val out_dir = "/home/mg1/pra/graphs/nell/kb-t_svo/parafac_matrices/"

    fileUtil.mkdirOrDie(out_dir)
    val parafac_entries = loadParafacDir(parafac_dir)
    val matrix_files = fileUtil.listDirectoryContents(matrix_dir).asScala
    println(matrix_files)
    matrix_files.par.map(matrix_file => {
      val matrix_entries = readMatrixFile(matrix_dir + matrix_file)
      val transformed_entries = matrix_entries.map(matrix_entry => {
        if (parafac_entries.contains(matrix_entry._1)) {
          (matrix_entry._1, parafac_entries(matrix_entry._1))
        } else {
          matrix_entry
        }
      })
      outputMatrixFile(out_dir + matrix_file, transformed_entries)
    })
  }
}
