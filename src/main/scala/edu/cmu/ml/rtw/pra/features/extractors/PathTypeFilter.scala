package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.features.BaseEdgeSequencePathType
import edu.cmu.ml.rtw.pra.features.LexicalizedPathType
import edu.cmu.ml.rtw.pra.features.Path
import edu.cmu.ml.rtw.pra.graphs.Graph
import com.mattg.util.JsonHelper

import org.json4s._
import org.json4s.native.JsonMethods._

trait PathFilter {
  def shouldKeepPath(path: Path, graph: Graph): Boolean
}

object PathFilter {
  def create(params: JValue): PathFilter = {
    new EdgeSequenceFilter(params)
  }
}

class EdgeSequenceFilter(params: JValue) extends PathFilter {
  implicit val formats = DefaultFormats
  val allowedParamKeys = Seq("type", "includes", "excludes")
  JsonHelper.ensureNoExtras(params, "BasicPathTypeFilter", allowedParamKeys)

  val includes = parseParamStr(JsonHelper.extractWithDefault(params, "includes", Seq[String]("*")))
  val excludes = parseParamStr(JsonHelper.extractWithDefault(params, "excludes", Seq[String]()))

  override def shouldKeepPath(path: Path, graph: Graph): Boolean = {
    val pathSeq = path.edges.zip(path.reverses).toSeq
    _shouldKeepPath(pathSeq, graph)
  }

  def parseParamStr(paramStr: Seq[String]): Seq[Seq[String]] = paramStr.map(_.split(",").toSeq)

  def _shouldKeepPath(pathSeq: Seq[(Int, Boolean)], graph: Graph): Boolean = {
    val included = includes.exists(pathMatchesFilter(pathSeq, graph))
    if (included) {
      val excluded = excludes.exists(pathMatchesFilter(pathSeq, graph))
      !excluded
    } else {
      false
    }
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
