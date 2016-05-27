package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.features.Path
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.pra.graphs.Node
import com.mattg.util.MutableConcurrentDictionary

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

class PathTypeFilterSpec extends FlatSpecLike with Matchers {

  val edgeDict = new MutableConcurrentDictionary
  edgeDict.getIndex("edge1")
  edgeDict.getIndex("edge2")
  edgeDict.getIndex("edge3")
  edgeDict.getIndex("edge4")
  edgeDict.getIndex("edge5")
  edgeDict.getIndex("edge6")
  edgeDict.getIndex("edge7")
  val graph = new GraphInMemory(Array[Node](), new MutableConcurrentDictionary, edgeDict)
  val pathType1 = Path(0, Array(0), Array(1), Array(false))
  val pathType2 = Path(0, Array(0, 0), Array(1, 2), Array(false, false))
  val pathType3 = Path(0, Array(0, 0), Array(1, 5), Array(false, false))
  val pathType4 = Path(0, Array(0, 0), Array(2, 4), Array(false, false))
  val pathType5 = Path(0, Array(0, 0, 0), Array(2, 5, 4), Array(false, false, false))
  val pathType6 = Path(0, Array(0, 0, 0, 0, 0), Array(2, 5, 6, 7, 4), Array(false, false, false, false, false))
  val pathType7 = Path(0, Array(0, 0), Array(1, 2), Array(false, true))

  "shouldKeepPath" should "keep all paths with default parameters" in {
    val filter = new EdgeSequenceFilter(JNothing)
    filter.shouldKeepPath(pathType1, graph) should be(true)
    filter.shouldKeepPath(pathType2, graph) should be(true)
    filter.shouldKeepPath(pathType3, graph) should be(true)
    filter.shouldKeepPath(pathType4, graph) should be(true)
    filter.shouldKeepPath(pathType5, graph) should be(true)
  }

  it should "keep only paths matching an initial include" in {
    val params: JValue = ("includes" -> Seq("edge1,*"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(true)
    filter.shouldKeepPath(pathType2, graph) should be(true)
    filter.shouldKeepPath(pathType3, graph) should be(true)
    filter.shouldKeepPath(pathType4, graph) should be(false)
    filter.shouldKeepPath(pathType5, graph) should be(false)
  }

  it should "keep only paths matching a different initial include" in {
    val params: JValue = ("includes" -> Seq("edge2,*"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(true)
    filter.shouldKeepPath(pathType5, graph) should be(true)
  }

  it should "keep only paths matching a final include" in {
    val params: JValue = ("includes" -> Seq("*,edge2"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(true)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(false)
    filter.shouldKeepPath(pathType5, graph) should be(false)
  }

  it should "keep only paths matching a different final include" in {
    val params: JValue = ("includes" -> Seq("*,edge4"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(true)
    filter.shouldKeepPath(pathType5, graph) should be(true)
  }

  it should "keep only paths matching a middle include" in {
    val params: JValue = ("includes" -> Seq("*,edge4,*"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(true)
    filter.shouldKeepPath(pathType5, graph) should be(true)
  }

  it should "keep only paths matching a more complicated middle include" in {
    val params: JValue = ("includes" -> Seq("*,edge5,edge6,*"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(false)
    filter.shouldKeepPath(pathType5, graph) should be(false)
    filter.shouldKeepPath(pathType6, graph) should be(true)
  }

  it should "keep only paths matching an even more complicated middle include" in {
    val params: JValue = ("includes" -> Seq("*,edge5,*,edge7,*"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(false)
    filter.shouldKeepPath(pathType5, graph) should be(false)
    filter.shouldKeepPath(pathType6, graph) should be(true)
  }

  it should "correctly merge multiple includes" in {
    val params: JValue = ("includes" -> Seq("*,edge4", "*,edge2"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(true)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(true)
    filter.shouldKeepPath(pathType5, graph) should be(true)
  }

  it should "handle inverses" in {
    val params: JValue = ("includes" -> Seq("*,_edge2"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(false)
    filter.shouldKeepPath(pathType5, graph) should be(false)
    filter.shouldKeepPath(pathType6, graph) should be(false)
    filter.shouldKeepPath(pathType7, graph) should be(true)
  }

  it should "handle pluses correctly" in {
    val params: JValue = ("includes" -> Seq("*,edge2,+,edge4"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(false)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(false)
    filter.shouldKeepPath(pathType4, graph) should be(false)
    filter.shouldKeepPath(pathType5, graph) should be(true)
    filter.shouldKeepPath(pathType6, graph) should be(true)
  }

  it should "handle excludes" in {
    val params: JValue = ("includes" -> Seq("edge1,*")) ~ ("excludes" -> Seq("*,edge2"))
    val filter = new EdgeSequenceFilter(params)
    filter.shouldKeepPath(pathType1, graph) should be(true)
    filter.shouldKeepPath(pathType2, graph) should be(false)
    filter.shouldKeepPath(pathType3, graph) should be(true)
    filter.shouldKeepPath(pathType4, graph) should be(false)
    filter.shouldKeepPath(pathType5, graph) should be(false)
    filter.shouldKeepPath(pathType6, graph) should be(false)
  }
}
