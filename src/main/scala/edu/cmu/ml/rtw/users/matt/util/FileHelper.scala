package edu.cmu.ml.rtw.users.matt.util

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable
import scalax.io.Resource
import scala.util.matching.Regex

object FileHelper {
  def recursiveListFiles(f: File, r: Regex): Seq[File] = {
    val these = f.listFiles
    if (these == null) {
      Nil
    } else {
      val good = these.filter(f => r.findFirstIn(f.getName).isDefined)
      good ++ these.filter(_.isDirectory).flatMap(recursiveListFiles(_, r))
    }
  }
}
