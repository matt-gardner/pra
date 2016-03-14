package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.Subgraph
import edu.cmu.ml.rtw.pra.graphs.Graph
import com.mattg.util.FileUtil

import org.json4s._

trait NodeFeatureExtractor extends FeatureExtractor[NodeInstance] {
}

object NodeFeatureExtractor {
  def create(
    params: JValue,
    outputter: Outputter,
    fileUtil: FileUtil
  ): NodeFeatureExtractor = {
    params match {
      case JString("PathAndEndNodeFeatureExtractor") => new PathAndEndNodeFeatureExtractor(outputter)
      case JString("PathOnlyFeatureExtractor") => new PathOnlyFeatureExtractor(outputter)
      case JString(other) => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
      case jval: JValue => {
        (jval \ "name") match {
          case other => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
        }
      }
    }
  }
}

class PathAndEndNodeFeatureExtractor(outputter: Outputter) extends NodeFeatureExtractor {
  override def extractFeatures(instance: NodeInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.flatMap(entry => {
      entry._2.map(nodePair => {
        val (start, end) = nodePair
        val path = entry._1.encodeAsHumanReadableString(graph)
        val endNode = graph.getNodeName(end)
        if (start == instance.node) {
          path + ":" + endNode
        } else {
          outputter.fatal(s"node: ${instance.node}")
          outputter.fatal(s"Left node: ${start}")
          outputter.fatal(s"Right node: ${end}")
          outputter.fatal(s"path: ${path}")
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be the instance node")
        }
      })
    }).toSeq
  }
}

class PathOnlyFeatureExtractor(outputter: Outputter) extends NodeFeatureExtractor {
  override def extractFeatures(instance: NodeInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.flatMap(entry => {
      entry._2.map(nodePair => {
        val (start, end) = nodePair
        val path = entry._1.encodeAsHumanReadableString(graph)
        if (start == instance.node) {
          path
        } else {
          outputter.fatal(s"node: ${instance.node}")
          outputter.fatal(s"Left node: ${start}")
          outputter.fatal(s"Right node: ${end}")
          outputter.fatal(s"path: ${path}")
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be the instance node")
        }
      })
    }).toSeq
  }
}
