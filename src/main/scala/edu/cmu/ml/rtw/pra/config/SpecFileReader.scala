package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

class SpecFileReader(baseDir: String, fileUtil: FileUtil = new FileUtil()) {
  implicit val formats = DefaultFormats

  def readSpecFile(file: java.io.File): JValue = {
    readSpecFile(fileUtil.readLinesFromFile(file).asScala)
  }

  def readSpecFile(filename: String): JValue = {
    readSpecFile(fileUtil.readLinesFromFile(filename).asScala)
  }

  def readSpecFile(lines: Seq[String]): JValue = {
    val empty_params = new JObject(Nil)
    val params = populateParamsFromSpecs(lines, empty_params)
    removeDeletes(doNestedLoads(params))
  }

  // This handles load statements at the beginning of a file, however many there are.
  def populateParamsFromSpecs(specs: Seq[String], params: JValue): JValue = {
    if (specs(0).startsWith("load")) {
      return readSpecFile(getParamFileFromLoadStatement(specs(0))) merge populateParamsFromSpecs(
        specs.drop(1), params)
    } else {
      params merge parse(specs.mkString(" "))
    }
  }

  // This handles load statements that are given as values in the json (i.e. "graph": "load file").
  def doNestedLoads(params: JValue): JValue = {
    params mapField {
      case (name, JString(load)) if (load.startsWith("load ")) => {
        (name, readSpecFile(getParamFileFromLoadStatement(load)))
      }
      case (name, JArray(list)) => {
        val new_list = list.map(_ match {
          case JString(load) if (load.startsWith("load ")) => {
            readSpecFile(getParamFileFromLoadStatement(load))
          }
          case other => other
        })
        (name, new_list)
      }
      case other => other
    }
  }

  def removeDeletes(params: JValue): JValue = {
    params.removeField(field => field._2 == JString("delete"))
  }

  def getParamFileFromLoadStatement(load: String) = {
    val name = load.split(" ")(1)
    if (name.startsWith("/")) {
      name
    } else {
      s"${baseDir}param_files/${name}.json"
    }
  }
}

object JsonHelper {
  implicit val formats = DefaultFormats

  // I don't really understand this implicit Manifest stuff, but stackoverflow told me to put it
  // there, and it worked.
  def extractWithDefault[T](params: JValue, key: String, default: T)(implicit m: Manifest[T]): T = {
    (params \ key) match {
      case JNothing => default
      case value => value.extract[T]
    }
  }

  def getPathOrNameOrNull(params: JValue, key: String, baseDir: String, nameDir: String): String = {
    (params \ key) match {
      case JNothing => null
      case JString(path) if (path.startsWith("/")) => path
      case JString(name) => s"${baseDir}${nameDir}/${name}/"
      case other => throw new IllegalStateException(s"$key is not a string field")
    }
  }

  def getPathOrName(params: JValue, key: String, baseDir: String, nameDir: String): Option[String] = {
    (params \ key) match {
      case JNothing => None
      case JString(path) if (path.startsWith("/")) => Some(path)
      case JString(name) => Some(s"${baseDir}${nameDir}/${name}/")
      case other => throw new IllegalStateException(s"$key is not a string field")
    }
  }

  def ensureNoExtras(params: JValue, base: String, keys: Seq[String]) {
    params match {
      case JObject(fields) => {
        fields.map(field => {
          if (!keys.contains(field._1)) {
            val message = s"Malformed parameters under $base: unexpected key: ${field._1}"
            throw new IllegalStateException(message)
          }
        })
      }
      case other => {}
    }
  }
}
