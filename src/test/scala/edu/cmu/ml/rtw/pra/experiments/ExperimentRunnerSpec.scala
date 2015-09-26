package edu.cmu.ml.rtw.pra.experiments

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

import java.io.File

class ExperimentRunnerSpec extends FlatSpecLike with Matchers {

  val filters = Seq("filter1", "filter2")

  "shouldKeepFile" should "return true with no filters" in {
    ExperimentRunner.shouldKeepFile(Seq())(new File("/test/file")) should be(true)
  }

  it should "return false when there are filters, and they don't match" in {
    ExperimentRunner.shouldKeepFile(filters)(new File("/test/file")) should be(false)
  }

  it should "return true when there are filters, and they match" in {
    ExperimentRunner.shouldKeepFile(filters)(new File("/test/file/filter1")) should be(true)
  }
}
