package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.graphs.Graph

// Ideally, the nodes, edges, and reverses would be immutable, but we'll use actual Arrays here for
// speed.
case class Path(
  startNode: Int,
  nodes: Array[Int],
  edges: Array[Int],
  reverses: Array[Boolean]
) {
  def this(startNode: Int) {
    this(startNode, Array(), Array(), Array())
  }

  // Apparently array equality doesn't work like I thought it did, so we have to override the
  // hasCode and equals methods for this case class.
  override def equals(that: Any) = {
    that match {
      case that: Path => {
        that.canEqual(this) && this.hashCode == that.hashCode
      }
    }
  }

  override def hashCode() = {
    41 * (
      41 * (
        41 * (
          41 + startNode
        ) + java.util.Arrays.hashCode(nodes)
      ) + java.util.Arrays.hashCode(edges)
    ) + java.util.Arrays.hashCode(reverses)
  }

  val numSteps = nodes.length
  val isEmpty = numSteps == 0

  def alreadyVisited(node: Int) = node == startNode || nodes.exists(_ == node)

  // This ignores the edge direction, and returns true if the edge was followed at all.
  def containsEdge(source: Int, target: Int, edge: Int): Boolean = {
    if (isEmpty) return false
    if (edgeMatches(startNode, nodes(0), edges(0), source, target, edge)) return true
    for (i <- (0 until nodes.length - 1)) {
      if (edgeMatches(nodes(i), nodes(i+1), edges(i+1), source, target, edge)) return true
    }
    return false
  }

  def edgeMatches(
    querySource: Int,
    queryTarget: Int,
    queryEdge: Int,
    source: Int,
    target: Int,
    edge: Int
  ): Boolean = {
    (querySource == source && queryTarget == target && queryEdge == edge) ||
    (querySource == target && queryTarget == source && queryEdge == edge)
  }

  def addHop(node: Int, edge: Int, reverse: Boolean): Path = {
    val newNodes = new Array[Int](nodes.length + 1)
    val newEdges = new Array[Int](edges.length + 1)
    val newReverses = new Array[Boolean](reverses.length + 1)
    System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
    System.arraycopy(edges, 0, newEdges, 0, edges.length);
    System.arraycopy(reverses, 0, newReverses, 0, reverses.length);
    newNodes(nodes.length) = node
    newEdges(edges.length) = edge
    newReverses(reverses.length) = reverse
    Path(startNode, newNodes, newEdges, newReverses)
  }

  def reversePath(): Path = {
    if (isEmpty) return this
    val newNodes = new Array[Int](nodes.length)
    val newEdges = new Array[Int](edges.length)
    val newReverses = new Array[Boolean](reverses.length)
    val newStartNode = nodes.last
    for (i <- (0 until nodes.length)) {
      if (i < nodes.length - 1) {
        newNodes(i) = nodes(nodes.length - 2 - i)
      } else {
        newNodes(i) = startNode
      }
      newEdges(i) = edges(edges.length - 1 - i)
      newReverses(i) = !reverses(reverses.length - 1 - i)
    }
    Path(newStartNode, newNodes, newEdges, newReverses)
  }

  def addPath(path: Path): Path = {
    if (isEmpty) {
      if (startNode == path.startNode) return path
      throw new IllegalStateException("To add a path, its start node must match my end node")
    }
    if (nodes.last != path.startNode) {
      throw new IllegalStateException("To add a path, its start node must match my end node")
    }
    val newNodes = nodes ++ path.nodes
    val newEdges = edges ++ path.edges
    val newReverses = reverses ++ path.reverses
    Path(startNode, newNodes, newEdges, newReverses)
  }

  def encodeAsString(includeNodes: Boolean = false) = stringDescription(None, includeNodes, Map())

  def encodeAsHumanReadableString(
    graph: Graph,
    includeNodes: Boolean = false,
    edgeMap: Map[Int, String] = Map()
  ) = {
    stringDescription(Some(graph), includeNodes, edgeMap)
  }

  private def stringDescription(graph: Option[Graph], includeNodes: Boolean, edgeMap: Map[Int, String]) = {
    if (includeNodes) {
      lexicalizedStringDescription(graph, edgeMap)
    } else {
      unlexicalizedStringDescription(graph, edgeMap)
    }
  }

  private def unlexicalizedStringDescription(graph: Option[Graph], edgeMap: Map[Int, String]) = {
    val builder = new StringBuilder()
    builder.append("-")
    for (i <- 0 until nodes.length) {
      if (reverses(i)) {
        builder.append("_")
      }
      graph match {
        case None => builder.append(edges(i))
        case Some(graph) => {
          val edgeName = edgeMap.get(edges(i)) match {
            case Some(name) => name
            case None => graph.getEdgeName(edges(i))
          }
          builder.append(edgeName)
        }
      }
      builder.append("-")
    }
    builder.toString()
  }

  // TODO(matt): figure out how to make this configurable in a way that isn't ugly.
  val removeColon = "filter"
  val removeColonErrorMessage = "With remove colon: filter, second column must be KEEP or REMOVE"
  private def lexicalizedStringDescription(graph: Option[Graph], edgeMap: Map[Int, String]) = {
    val builder = new StringBuilder()
    for (i <- 0 until nodes.length) {
      builder.append("-")
      if (reverses(i)) {
        builder.append("_")
      }
      graph match {
        case None => builder.append(edges(i))
        case Some(graph) => {
          val edgeName = edgeMap.get(edges(i)) match {
            case Some(name) => name
            case None => graph.getEdgeName(edges(i))
          }
          builder.append(edgeName)
        }
      }
      builder.append("->")
      if (i < nodes.length - 1) {
        val nodeStr = graph.map(_.getNodeName(nodes(i))).getOrElse(nodes(i).toString)
        removeColon match {
          case "no" => builder.append(nodeStr)
          case "yes" => builder.append(nodeStr.split(":").last)
          case "filter" => {
            if (nodeStr.contains(":")) {
              val parts = nodeStr.split(":")
              parts(1) match {
                case "KEEP" => builder.append(parts.last)
                case "REMOVE" => { }
                case _ => throw new IllegalStateException(removeColonErrorMessage)
              }
            } else {
              builder.append(nodeStr)
            }
          }
        }
      }
    }
    builder.toString()
  }

  override def toString(): String = {
    if (isEmpty) return startNode.toString
    startNode + lexicalizedStringDescription(None, Map()) + nodes.last
  }
}
