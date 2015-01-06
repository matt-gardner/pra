package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConversions._

import org.json4s._
import org.json4s.native.JsonMethods._

class SpecFileReader(fileUtil: FileUtil = new FileUtil()) {
  def readSpecFile(filename: String): JValue = {
    val params = new JObject(Nil)
    val specs = fileUtil.readLinesFromFile(filename)
    populateParamsFromSpecs(specs, params)
  }

  def populateParamsFromSpecs(specs: Seq[String], params: JValue): JValue = {
    if (specs(0).startsWith("load")) {
      return readSpecFile(specs(0).split(" ")(1)) merge populateParamsFromSpecs(specs.drop(1), params)
    } else {
      params merge parse(specs.mkString(" "))
    }
  }
}
