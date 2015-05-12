package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

class DatasetSpec extends FlatSpecLike with Matchers {

  val datasetFile = "node1\tnode2\t1\n" + "node1\tnode3\t-1\n"

  val badDatasetFile = datasetFile + "node1\tnode4\tnode5\n"
  val badDatasetFilename = "/bad/filename"

  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(badDatasetFilename, badDatasetFile)

  "fromFile" should "crash on bad third column" in {
    TestUtil.expectError(classOf[RuntimeException], new Function() {
      override def call() {
        Dataset.fromFile(badDatasetFilename, new Dictionary, fileUtil)
      }
    })
  }
}
