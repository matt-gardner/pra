package edu.cmu.ml.rtw.pra.graphs

import scala.collection.mutable

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

trait Graph {
  def entries: Array[Node]

  def getNode(i: Int) = entries(i)
  def getNode(name: String) = entries(getNodeIndex(name))

  def getNodeName(i: Int): String
  def getNodeIndex(name: String): Int
  def getNumNodes(): Int

  def getEdgeName(i: Int): String
  def getEdgeIndex(name: String): Int
  def getNumEdgeTypes(): Int

  // These methods below here should only be used with care!  Depending on your graph, they could
  // take up a lot of memory and a lot of time.  The point here is to write these to disk in
  // particular formats, for the occasion that you would like to do this.  It doesn't make much
  // sense if the graph already exists on disk.
  def getAllTriples(): Set[(Int, Int, Int)] = {
    entries.par.zipWithIndex.flatMap(nodeIndex => {
      val node = nodeIndex._1
      val index = nodeIndex._2
      node.edges.flatMap(relationEdges => {
        val relation = relationEdges._1
        val relationName = getEdgeName(relation)
        val inEdges = relationEdges._2._1
        val outEdges = relationEdges._2._2
        val inEdgeTriples = inEdges.map(sourceIndex => {
          (sourceIndex, relation, index)
        }).toSet
        val outEdgeTriples = outEdges.map(targetIndex => {
          (index, relation, targetIndex)
        }).toSet
        inEdgeTriples ++ outEdgeTriples
      })
    }).seq.toSet
  }

  def getAllTriplesAsStrings(): Set[(String, String, String)] = {
    getAllTriples.par.map(triple => {
      (getNodeName(triple._1), getEdgeName(triple._2), getNodeName(triple._3))
    }).seq
  }

  // This takes the graph and writes it to the format that is read by
  // Dataset.lineToInstanceAndGraph.
  def writeToInstanceGraph(): String = {
    val triples = getAllTriplesAsStrings()
    triples.map(triple => triple._1 + "^,^" + triple._2 + "^,^" + triple._3).mkString(" ### ")
  }

  def writeToGraphChiFormat(): String = {
    getAllTriples.map(triple => triple._1 + "\t" + triple._2 + "\t" + triple._3).mkString("\n")
  }

  def writeToGraphChiLines(): Seq[String] = {
    getAllTriples.map(triple => triple._1 + "\t" + triple._2 + "\t" + triple._3).toSeq
  }
}

// The edges map is (relation -> (in edges, out edges)).
class Node(val edges: Map[Int, (Array[Int], Array[Int])], edgeDict: Dictionary) {
  def getEdges(edgeLabel: String) = {
    edges(edgeDict.getIndex(edgeLabel))
  }

  // We'll save ourselves some time and memory and only create this when it's asked for.  Hopefully
  // it won't exacerbate memory issues too much.  But, especially when using random walks to
  // compute PPR, this can take a lot of time if it's recomputed every time it's asked for.
  private lazy val _connectedNodes = edges.flatMap(keyValue => keyValue._2._1 ++ keyValue._2._2).toSet

  def getAllConnectedNodes(): Set[Int] = _connectedNodes
}

// This Graph implementation is backed by a file on disk, and can either be used with GraphChi or
// loaded into memory.
class GraphOnDisk(
  val graphDir: String,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
) extends Graph {
  lazy val _entries: Array[Node] = loadGraph()
  val graphFile = graphDir + "graph_chi/edges.tsv"
  lazy val numShards = fileUtil.readLinesFromFile(graphDir + "num_shards.tsv")(0).toInt

  outputter.info("Loading node and edge dictionaries")
  val nodeDict = new Dictionary(fileUtil)
  nodeDict.setFromFile(graphDir + "node_dict.tsv")
  val edgeDict = new Dictionary(fileUtil)
  edgeDict.setFromFile(graphDir + "edge_dict.tsv")

  override def entries = _entries
  override def getNodeName(i: Int) = nodeDict.getString(i)
  override def getNodeIndex(name: String) = nodeDict.getIndex(name)
  override def getNumNodes() = nodeDict.getNextIndex()

  override def getEdgeName(i: Int) = edgeDict.getString(i)
  override def getEdgeIndex(name: String) = edgeDict.getIndex(name)
  override def getNumEdgeTypes() = edgeDict.getNextIndex()

  def loadGraph(): Array[Node] = {
    outputter.info(s"Loading graph")
    val graphBuilder = new GraphBuilder(outputter, nodeDict.getNextIndex, nodeDict, edgeDict)
    for (line <- fileUtil.getLineIterator(graphFile)) {
      val fields = line.split("\t")
      val source = fields(0).toInt
      val target = fields(1).toInt
      val relation = fields(2).toInt
      graphBuilder.addEdge(source, target, relation)
    }
    outputter.info("Done reading graph file")
    graphBuilder.build
  }
}

// This Graph implementation has no corresponding file on disk, so it cannot be used with GraphChi,
// and is only kept in memory.  It must be constructed with the Node array, as there is no way to
// load it lazily.
class GraphInMemory(_entries: Array[Node], nodeDict: Dictionary, edgeDict: Dictionary) extends Graph {
  override def entries = _entries
  override def getNodeName(i: Int) = nodeDict.getString(i)
  override def getNodeIndex(name: String) = nodeDict.getIndex(name)
  override def getNumNodes() = nodeDict.getNextIndex()

  override def getEdgeName(i: Int) = edgeDict.getString(i)
  override def getEdgeIndex(name: String) = edgeDict.getIndex(name)
  override def getNumEdgeTypes() = edgeDict.getNextIndex()

  def writeToDisk(directory: String) {
    val fileUtil = new FileUtil
    nodeDict.writeToFile(directory + "node_dict.tsv")
    edgeDict.writeToFile(directory + "node_dict.tsv")
    fileUtil.writeLinesToFile(directory + "edges.tsv", writeToGraphChiLines())
  }
}

// This class constructs a Node array corresponding to a particular graph.  There's a little bit of
// funniness with the dictionaries, because GraphOnDisk only needs the Node array created, while
// GraphInMemory needs the dictionaries too.  So we just make them vals, so the caller can get the
// dictionaries out if necessary.
class GraphBuilder(
  outputter: Outputter,
  initialSize: Int = -1,
  val nodeDict: Dictionary = new Dictionary,
  val edgeDict: Dictionary = new Dictionary
) {
  type MutableGraphEntry = mutable.HashMap[Int, mutable.HashMap[Boolean, Set[Int]]]
  var entries = new Array[MutableGraphEntry](if (initialSize > 0) initialSize else 100)
  (0 until entries.size).par.foreach(i => { entries(i) = new MutableGraphEntry })
  var maxIndexSeen = -1
  var edgesAdded = 0

  def addEdge(source: String, target: String, relation: String) {
    addEdge(nodeDict.getIndex(source), nodeDict.getIndex(target), edgeDict.getIndex(relation))
  }

  def addEdge(source: Int, target: Int, relation: Int) {
    if (source > maxIndexSeen) maxIndexSeen = source
    if (target > maxIndexSeen) maxIndexSeen = target
    if (source >= entries.size || target >= entries.size) {
      growEntries()
    }
    val sourceMap = entries(source).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
    sourceMap.update(false, sourceMap.getOrElse(false, Set()) + target)
    val targetMap = entries(target).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
    targetMap.update(true, targetMap.getOrElse(true, Set()) + source)
    edgesAdded += 1
  }

  def growEntries() {
    val oldSize = entries.size
    val newSize = oldSize * 2
    val newEntries = new Array[MutableGraphEntry](newSize)
    Array.copy(entries, 0, newEntries, 0, oldSize)
    (oldSize until newSize).par.foreach(i => { newEntries(i) = new MutableGraphEntry })
    entries = newEntries
  }

  def build(): Array[Node] = {
    outputter.info("Building the graph object")
    // If no initial size was provided, we try to trim the size of the resultant array (this should
    // cut down the graph size by at most a factor of 2).  If we were given an initial graph size,
    // then the caller probably knew how big the graph was, and might query for nodes that we never
    // actually saw edges for, and we'll need an empty node representations for that.
    val finalSize = if (initialSize == -1) maxIndexSeen + 1 else entries.size
    val finalized = new Array[Node](finalSize)
    (0 until finalSize).par.foreach(i => {
      if (entries(i) == null) {
        finalized(i) = new Node(Map(), edgeDict)
      } else {
        finalized(i) = new Node(entries(i).map(entry => {
          val relation = entry._1
          val inEdges = entry._2.getOrElse(true, Set()).toList.sorted.toArray
          val outEdges = entry._2.getOrElse(false, Set()).toList.sorted.toArray
          (relation -> (inEdges, outEdges))
        }).toMap, edgeDict)
      }
    })
    outputter.info("Graph object built")
    finalized
  }

  def toGraphInMemory(): GraphInMemory = {
    val nodes = build()
    new GraphInMemory(nodes, nodeDict, edgeDict)
  }
}
