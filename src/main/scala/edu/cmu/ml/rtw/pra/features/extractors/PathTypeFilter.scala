package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.features.BaseEdgeSequencePathType
import edu.cmu.ml.rtw.pra.features.PathType
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import org.json4s._
import org.json4s.native.JsonMethods._

trait PathTypeFilter {
  def shouldKeepPath(pathType: PathType, graph: Graph): Boolean
}

object PathTypeFilterCreator {
  def create(params: JValue): PathTypeFilter = {
    JsonHelper.extractWithDefault(params, "type", "basic") match {
      case "basic" => new BasicPathTypeFilter(params)
      case _ => throw new IllegalStateException("Unrecognized path type filter")
    }
  }
}

class BasicPathTypeFilter(params: JValue) extends PathTypeFilter {
  implicit val formats = DefaultFormats
  val allowedParamKeys = Seq("type", "includes", "excludes")
  JsonHelper.ensureNoExtras(params, "BasicPathTypeFilter", allowedParamKeys)

  val includes = parseParamStr(JsonHelper.extractWithDefault(params, "includes", Seq[String]("*")))
  val excludes = parseParamStr(JsonHelper.extractWithDefault(params, "excludes", Seq[String]()))

  override def shouldKeepPath(pathType: PathType, graph: Graph): Boolean = {
    pathType match {
      case p: BaseEdgeSequencePathType => { _shouldKeepPath(p, graph) }
      case _ => { throw new IllegalStateException("I cannot filter this path type...") }
    }
  }

  def parseParamStr(paramStr: Seq[String]): Seq[Seq[String]] = paramStr.map(_.split(",").toSeq)

  def _shouldKeepPath(pathType: BaseEdgeSequencePathType, graph: Graph): Boolean = {
    val included = includes.exists(pathMatchesFilter(pathTypeToSeq(pathType), graph))
    if (included) {
      val excluded = excludes.exists(pathMatchesFilter(pathTypeToSeq(pathType), graph))
      !excluded
    } else {
      false
    }
  }

  def pathTypeToSeq(pathType: BaseEdgeSequencePathType): Seq[(Int, Boolean)] = {
    pathType.getEdgeTypes.zip(pathType.getReverse).toSeq
  }

  def pathMatchesFilter(edges: Seq[(Int,Boolean)], graph: Graph)(filter: Seq[String]): Boolean = {
    val (nextEdge, reverse) = if (filter(0).startsWith("_")) {
      (filter(0).substring(1), true)
    } else {
      (filter(0), false)
    }
    val nextFilter = filter.drop(1)
    nextEdge match {
      case "*" => {
        if (nextFilter.size == 0) return true
        (0 until edges.size).exists(i => pathMatchesFilter(edges.drop(i), graph)(nextFilter))
      }
      case "+" => {
        if (edges.size == 0) return false
        if (nextFilter.size == 0) return true
        (1 until edges.size).exists(i => pathMatchesFilter(edges.drop(i), graph)(nextFilter))
      }
      case edge => {
        if (edges.size == 0) return false
        val filterEdge = (graph.getEdgeIndex(edge), reverse)
        if (filterEdge == edges(0)) {
          if (nextFilter.size == 0) {
            if (edges.size == 1) return true
            else return false
          }
          return pathMatchesFilter(edges.drop(1), graph)(nextFilter)
        } else {
          return false
        }
      }
    }
  }
}
