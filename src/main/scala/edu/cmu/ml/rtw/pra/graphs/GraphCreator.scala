package edu.cmu.ml.rtw.pra.graphs

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter

import edu.cmu.graphchi.ChiFilenames
import edu.cmu.graphchi.EmptyType
import edu.cmu.graphchi.datablocks.IntConverter
import edu.cmu.graphchi.preprocessing.EdgeProcessor
import edu.cmu.graphchi.preprocessing.FastSharder
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.IntTriple
import edu.cmu.ml.rtw.users.matt.util.Pair

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

// TODO(matt): I like the design of RelationSet a lot better than this, where the params JValue is
// a class argument instead of an argument to a method.  Fix this.
// TODO(matt): Add an option to exclude the test edges in a split when creating the graph.  This
// makes the graph dependent on the split, but is necessary for some experimental protocols.  It
// should be easy to use seenTriples to implement this - just add the whole test split (including
// inverses) to the seenTriples object before adding any edges.
class GraphCreator(baseDir: String, outdir: String, fileUtil: FileUtil = new FileUtil) {
  implicit val formats = DefaultFormats

  val matrixOutDir = outdir + "matrices/"
  val inProgressFile = outdir + "in_progress"
  val paramFile = outdir + "params.json"
  val relationSetDir = "relation_sets/"

  def getRelationSets(params: JValue): Seq[RelationSet] = {
    val value = params \ "relation sets"
    value.children.map(rel_set => {
      (rel_set \ "type") match {
        case JNothing => new RelationSet(rel_set, fileUtil)
        case JString("generated") => generateSyntheticRelationSet(rel_set \ "generation params")
        case other => throw new IllegalStateException("Bad relation set specification")
      }
    })
  }

  def deduplicateEdges(params: JValue): Boolean = {
    val value = params \ "deduplicate edges"
    if (value.equals(JNothing)) {
      false
    } else {
      value.extract[Boolean]
    }
  }

  def createMatrices(params: JValue): Boolean = {
    val value = params \ "create matrices"
    if (value.equals(JNothing)) {
      false
    } else {
      value.extract[Boolean]
    }
  }

  def maxMatrixFileSize(params: JValue): Int = {
    val value = params \ "max matrix file size"
    if (value.equals(JNothing)) {
      100000
    } else {
      value.extract[Int]
    }
  }

  def createGraphChiRelationGraph(params: JValue) {
    createGraphChiRelationGraph(params, true)
  }

  def createGraphChiRelationGraph(params: JValue, shouldShardGraph: Boolean) {
    val name = (params \ "name").extract[String]
    println(s"Creating graph $name in $outdir")
    println("Making directories")

    // Some preparatory stuff
    fileUtil.mkdirOrDie(outdir)
    fileUtil.touchFile(inProgressFile)
    val params_out = fileUtil.getFileWriter(paramFile)
    params_out.write(pretty(render(params)))
    params_out.close

    fileUtil.mkdirs(outdir + "graph_chi/")
    val edgeFilename = outdir + "graph_chi/edges.tsv"
    val intEdgeFile = fileUtil.getFileWriter(edgeFilename)

    val relationSets = getRelationSets(params)

    println("Loading aliases")
    val aliases = relationSets.filter(_.isKb).par.map(relationSet => {
      (relationSet.aliasRelation, relationSet.getAliases)
    }).seq

    val nodeDict = new Dictionary()
    val edgeDict = new Dictionary()

    val seenNps = new mutable.HashSet[String]
    val seenTriples: mutable.HashSet[(Int, Int, Int)] = {
      if (deduplicateEdges(params)) {
        new mutable.HashSet[(Int, Int, Int)]
      } else {
        null
      }
    }
    val prefixes = getSvoPrefixes(relationSets)
    var numEdges = 0
    for (relationSet <- relationSets) {
      println("Adding edges to the graph from " + relationSet.relationFile)
      val prefix = prefixes(relationSet)
      numEdges += relationSet.writeRelationEdgesToGraphFile(intEdgeFile,
                                                            seenTriples,
                                                            prefix,
                                                            seenNps,
                                                            aliases,
                                                            nodeDict,
                                                            edgeDict)
    }
    intEdgeFile.close()

    // Adding edges is now finished, and the dictionaries aren't getting any more entries, so we
    // can output them.
    outputDictionariesToDisk(nodeDict, edgeDict)

    // Now decide how many shards to do, based on the number of edges that are in the graph.
    val numShards = getNumShards(numEdges)
    val writer = fileUtil.getFileWriter(outdir + "num_shards.tsv")
    writer.write(numShards + "\n")
    writer.close()
    if (shouldShardGraph) {
      shardGraph(edgeFilename, numShards)
    }

    // This is for if you want to do the path following step with matrix multiplications instead of
    // with random walks (which I'm expecting to be a lot faster, but haven't finished implementing
    // yet).
    if (createMatrices(params)) {
      outputMatrices(edgeFilename, maxMatrixFileSize(params))
    }
    fileUtil.deleteFile(inProgressFile)
  }

  /**
   * Runs GraphChi's preprocessing (sharding) on the graph.  This produces a number of shard files,
   * and if the files are already present, this is a no-op.  So it's only run once for each graph,
   * no matter how many times you run GraphChi code.
   */
  def shardGraph(baseFilename: String, numShards: Int) {
    val sharder = new FastSharder[EmptyType, Integer](baseFilename, numShards, null,
        new EdgeProcessor[Integer]() {
          def receiveEdge(from: Int, to: Int, token: String): java.lang.Integer = {
            token.toInt
          }
        }, null, new IntConverter())
    if (!new File(ChiFilenames.getFilenameIntervals(baseFilename, numShards)).exists()) {
      sharder.shard(new FileInputStream(new File(baseFilename)), "edgelist")
    }
  }

  ////////////////////////////////////////////////////////
  // Other boilerplate
  ////////////////////////////////////////////////////////

  def outputDictionariesToDisk(nodeDict: Dictionary, edgeDict: Dictionary) {
    println("Outputting dictionaries to disk")
    val nodeDictFile = fileUtil.getFileWriter(outdir + "node_dict.tsv")
    nodeDict.writeToWriter(nodeDictFile)
    nodeDictFile.close()

    val edgeDictFile = fileUtil.getFileWriter(outdir + "edge_dict.tsv")
    edgeDict.writeToWriter(edgeDictFile)
    edgeDictFile.close()
  }

  def getNumShards(numEdges: Int) = {
    if (numEdges < 5000000) {
      2
    } else if (numEdges < 10000000) {
      3
    } else if (numEdges < 40000000) {
      4
    } else if (numEdges < 100000000) {
      5
    } else if (numEdges < 150000000) {
      6
    } else if (numEdges < 250000000) {
      7
    } else if (numEdges < 350000000) {
      8
    } else if (numEdges < 500000000) {
      9
    } else {
      10
    }
  }

  def outputMatrices(filename: String, maxMatrixFileSize: Int) {
    println("Creating matrices")
    fileUtil.mkdirs(outdir + "matrices/")
    println("Reading edge file")
    var line: String = null
    val lines = fileUtil.readLinesFromFile(filename).asScala
    val matrices = lines.par.map(line => {
      val fields = line.split("\t")
      (fields(0).toInt, fields(1).toInt, fields(2).toInt)
    }).groupBy(x => x._3).toMap.mapValues(triple_set => {
      triple_set.map(triple => (triple._1, triple._2)).seq.toSeq
    }).seq
    val numRelations = matrices.map(_._1).max
    println("Outputting matrix files")
    val edgesToWrite = new mutable.ArrayBuffer[Seq[(Int, Int)]]
    var startRelation = 1
    var edgesSoFar = 0
    for (i <- 1 to numRelations) {
      val matrix = matrices.getOrElse(i, Nil)
      if (matrix.size == 0) println("RELATION WITH NO INSTANCES: " + i)
      if (edgesSoFar > 0 && edgesSoFar + matrix.size > maxMatrixFileSize) {
        writeEdgesSoFar(startRelation, i - 1, edgesToWrite.toSeq)
        edgesToWrite.clear
        startRelation = i
        edgesSoFar = 0
      }
      edgesToWrite += matrix
      edgesSoFar += matrix.size
    }
    if (edgesToWrite.size > 0) {
      writeEdgesSoFar(startRelation, numRelations, edgesToWrite)
    }
    System.out.println("Done creating matrices")
  }

  def writeEdgesSoFar(_start_relation: Int, end_relation: Int, edges_to_write: Seq[Seq[(Int, Int)]]) {
    var start_relation = _start_relation
    var filename = matrixOutDir + start_relation
    if (end_relation > start_relation) {
      filename += "-" + end_relation
    }
    val writer = fileUtil.getFileWriter(filename)
    for (matrix <- edges_to_write) {
      writer.write("Relation " + start_relation + "\n")
      for (entry <- matrix) {
        writer.write(entry._1 + "\t" + entry._2 + "\n")
      }
      start_relation += 1
    }
    writer.close()
  }

  /**
   * Create a prefix for each SVO file as necessary, according to how they were embedded.
   *
   * If the edges are embedded, we need to differentiate the latent representations if they were
   * not made together.  That is, if we have two or more embedded SVO files, and they have
   * embeddings files that are _different_, that means that a +L1 edge from one and a +L1 edge from
   * another are not the same edge type.  So we add a prefix to the edge type that is specific to
   * each embedding.  This isn't a problem with KB edges vs. SVO edges, because the "alias"
   * relation assures that the two kinds of edges will never share the same space.
   */
  def getSvoPrefixes(relationSets: Seq[RelationSet]): Map[RelationSet, String] = {
    val embeddingsToRels = relationSets
      .filter(!_.isKb)
      .filter(_.embeddingsFile != null).map(relationSet => {
        (relationSet.embeddingsFile, relationSet)
      }).groupBy(_._1).toMap
    if (embeddingsToRels.size <= 1) {
      Map[RelationSet, String]().withDefaultValue(null)
    } else {
      embeddingsToRels.zipWithIndex.flatMap(x => {
        x._1._2.map(y => (y._2, s"${x._2 + 1}-"))
      })
    }
  }

  var synthetic_data_creator_factory: ISyntheticDataCreatorFactory = new SyntheticDataCreatorFactory

  def generateSyntheticRelationSet(params: JValue): RelationSet = {
    val creator = synthetic_data_creator_factory.getSyntheticDataCreator(baseDir, params, fileUtil)
    if (fileUtil.fileExists(creator.relation_set_dir)) {
      fileUtil.blockOnFileDeletion(creator.in_progress_file)
      val current_params = parse(fileUtil.readLinesFromFile(creator.param_file).asScala.mkString("\n"))
      if (current_params.equals(JNothing)) {
        println(s"Odd...  couldn't read parameters from ${creator.param_file}, even though " +
          s"${creator.relation_set_dir} exists")
      }
      if (current_params != params) {
        println(s"Parameters found in ${creator.param_file}: ${pretty(render(current_params))}")
        println(s"Parameters specified in spec file: ${pretty(render(params))}")
        println(s"Difference: ${current_params.diff(params)}")
        throw new IllegalStateException("Synthetic data parameters don't match!")
      }
    } else {
      creator.createRelationSet()
    }
    val relSetParams = ("relation file" -> creator.data_file) ~ ("is kb" -> false)
    new RelationSet(relSetParams, fileUtil)
  }
}
