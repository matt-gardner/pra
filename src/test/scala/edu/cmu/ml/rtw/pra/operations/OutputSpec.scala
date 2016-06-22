package edu.cmu.ml.rtw.pra.operations

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.JsonDSL._
import org.scalatest._

class OutputSpec extends FlatSpecLike with Matchers {
  val graph = {
    val fileUtil = new FakeFileUtil
    val nodeDictFile = "1\tnode1\n" +
      "2\tnode2\n" +
      "3\tnode3\n" +
      "4\tnode4\n" +
      "5\tnode5\n" +
      "6\tnode6\n" +
      "7\tnode7\n" +
      "8\tnode8\n" +
      "9\tnode9\n" +
      "10\tnode10\n"
    val edgeDictFile = "1\trel1\n" +
      "2\trel2\n" +
      "3\trel3\n" +
      "4\trel4\n" +
      "5\trel5\n" +
      "6\trel6\n" +
      "7\trel7\n" +
      "8\trel8\n"
    fileUtil.addFileToBeRead("/graph/node_dict.tsv", nodeDictFile)
    fileUtil.addFileToBeRead("/graph/edge_dict.tsv", edgeDictFile)
    new GraphOnDisk("/graph/", fileUtil)
  }

  "outputFeatureMatrix" should "output a properly formatted feature matrix" in {
    val instance = new NodePairInstance(1, 2, true, graph)
    val rows = Seq(new MatrixRow(instance, Array(0, 1), Array(0.1, 0.2))).asJava
    val featureNames = Seq("-1-", "-2-")

    val matrixFile = "/results/fake name/fake relation/training_matrix.tsv"
    val expectedMatrixFileContents = "node1,node2\t1\t-1-,0.1 -#- -2-,0.2\n"

    val fileUtil = new FakeFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(matrixFile, expectedMatrixFileContents)
    Output.outputFeatureMatrix(matrixFile, new FeatureMatrix(rows), featureNames, fileUtil)
    fileUtil.expectFilesWritten()
  }
}
