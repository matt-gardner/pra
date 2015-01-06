package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

class SpecFileReaderSpec extends FlatSpecLike with Matchers {

  val baseSpecFilename = "/base/spec/file"
  val baseSpecFile = """{
    |  "kb files": "nell kb files",
    |  "graph files": "nell graph files",
    |  "split": "nell split",
    |  "l1 weight": 0.05,
    |  "l2 weight": 1,
    |  "walks per source": 200,
    |  "walks per path": 50,
    |  "path finding iterations": 3,
    |  "number of paths to keep": 1000,
    |  "matrix accept policy": "all-targets",
    |  "path accept policy": "paired-only"
    |}""".stripMargin

  val baseParams: JValue =
    ("kb files" -> "nell kb files") ~
    ("graph files" -> "nell graph files") ~
    ("split" -> "nell split") ~
    ("l1 weight" -> 0.05) ~
    ("l2 weight" -> 1) ~
    ("walks per source" -> 200) ~
    ("walks per path" -> 50) ~
    ("path finding iterations" -> 3) ~
    ("number of paths to keep" -> 1000) ~
    ("matrix accept policy" -> "all-targets") ~
    ("path accept policy" -> "paired-only")

  val extendedSpecFilename = "/extended/spec/file"
  val extendedSpecFile = """load /base/spec/file
    |{
    |  "additional param": "additional kb files"
    |}""".stripMargin

  val extendedParams: JValue = {
    val extraParam: JValue = ("additional param" -> "additional kb files")
    baseParams merge extraParam
  }

  val overwrittenSpecFilename = "/overwritten/spec/file"
  val overwrittenSpecFile = """load /extended/spec/file
    |{
    |  "additional param": "overwritten kb files"
    |}""".stripMargin

  val overwrittenParams: JValue = {
    val extraParam: JValue = ("additional param" -> "overwritten kb files")
    baseParams merge extraParam
  }

  val nestedSpecFilename = "/nested/spec/file"
  val nestedSpecFile = """{
    |  "level 1": {
    |    "level 2": {
    |      "level 3": {
    |        "level 4": "finished"
    |      }
    |    }
    |  }
    |}""".stripMargin

  val nestedParams: JValue =
    ("level 1" -> ("level 2" -> ("level 3" -> ("level 4" -> "finished"))))

  val fileUtil: FakeFileUtil = {
    val f = new FakeFileUtil
    f.addFileToBeRead(baseSpecFilename, baseSpecFile)
    f.addFileToBeRead(extendedSpecFilename, extendedSpecFile)
    f.addFileToBeRead(overwrittenSpecFilename, overwrittenSpecFile)
    f.addFileToBeRead(nestedSpecFilename, nestedSpecFile)
    f
  }

  "readSpecFile" should "read a simple map" in {
    new SpecFileReader(fileUtil).readSpecFile(baseSpecFilename) should be(baseParams)
  }

  it should "read a file with an extension" in {
    new SpecFileReader(fileUtil).readSpecFile(extendedSpecFilename) should be(extendedParams)
  }

  it should "overwrite parameters and do nested loads" in {
    new SpecFileReader(fileUtil).readSpecFile(overwrittenSpecFilename) should be(overwrittenParams)
  }

  it should "read nested parameters" in {
    new SpecFileReader(fileUtil).readSpecFile(nestedSpecFilename) should be(nestedParams)
    println(nestedParams \ "level 5")
  }

  "setPraConfigFromParams" should "initialize simple params" in {
  }
}
