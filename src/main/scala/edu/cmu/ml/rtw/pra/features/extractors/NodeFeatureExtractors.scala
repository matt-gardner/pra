package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.features.Subgraph
import edu.cmu.ml.rtw.pra.graphs.Graph
import com.mattg.util.FileUtil

import org.json4s._

import com.typesafe.scalalogging.LazyLogging

trait NodeFeatureExtractor extends FeatureExtractor[NodeInstance] with LazyLogging {
}

object NodeFeatureExtractor {
  def create(
    params: JValue,
    fileUtil: FileUtil
  ): NodeFeatureExtractor = {
    params match {
      case JString("PathAndEndNodeFeatureExtractor") => new PathAndEndNodeFeatureExtractor()
      case JString("PathOnlyFeatureExtractor") => new PathOnlyFeatureExtractor()
      case JString(other) => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
      case jval: JValue => {
        (jval \ "name") match {
          case other => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
        }
      }
    }
  }
}

class PathAndEndNodeFeatureExtractor extends NodeFeatureExtractor {
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
          logger.error(s"node: ${instance.node}")
          logger.error(s"Left node: ${start}")
          logger.error(s"Right node: ${end}")
          logger.error(s"path: ${path}")
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be the instance node")
        }
      })
    }).toSeq
  }
}

class PathOnlyFeatureExtractor extends NodeFeatureExtractor {
  override def extractFeatures(instance: NodeInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.flatMap(entry => {
      entry._2.map(nodePair => {
        val (start, end) = nodePair
        val path = entry._1.encodeAsHumanReadableString(graph)
        if (start == instance.node) {
          path
        } else {
          logger.error(s"node: ${instance.node}")
          logger.error(s"Left node: ${start}")
          logger.error(s"Right node: ${end}")
          logger.error(s"path: ${path}")
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be the instance node")
        }
      })
    }).toSeq
  }
}
