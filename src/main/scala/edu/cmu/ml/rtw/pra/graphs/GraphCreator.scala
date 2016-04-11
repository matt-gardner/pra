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
import edu.cmu.ml.rtw.pra.experiments.Outputter

import com.mattg.pipeline.Step
import com.mattg.util.Dictionary
import com.mattg.util.FileUtil
import com.mattg.util.IntTriple
import com.mattg.util.JsonHelper
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.Pair

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

// TODO(matt): Add an option to exclude the test edges in a split when creating the graph.  This
// makes the graph dependent on the split, but is necessary for some experimental protocols.  It
// should be easy to use seenTriples to implement this - just add the whole test split (including
// inverses) to the seenTriples object before adding any edges.
class GraphCreator(
  baseGraphDir: String,
  params: JValue,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Step(Some(params), fileUtil) {
  implicit val formats = DefaultFormats
  override val name = "Graph Creator"

  val graphName = (params \ "name").extract[String]
  val outdir = s"$baseGraphDir/$graphName/"
  override val inProgressFile = outdir + "in_progress"
  override val paramFile = outdir + "params.json"

  val relationSets = (params \ "relation sets").children.map(relationSet => {
    (relationSet \ "type") match {
      case JNothing => new RelationSet(relationSet, outputter, fileUtil)
      case JString("generated") => generateSyntheticRelationSet(relationSet \ "generation params")
      case other => throw new IllegalStateException("Bad relation set specification")
    }
  })

  // TODO(matt): this could use some work.  Really, we just want to take the union of the required
  // inputs to the RelationSets.  And we want to use None instead of null here.  But, that'll wait
  // for another day.
  val inputRelationFiles = relationSets.map(_.relationFile)
  val inputAliasFiles = relationSets.map(_.aliasFile).filterNot(_ == null)
  override val inputs: Set[(String, Option[Step])] = (inputRelationFiles ++ inputAliasFiles).map((_, None)).toSet

  val deduplicateEdges = JsonHelper.extractWithDefault(params, "deduplicate edges", false)
  val outputBinaryFile = JsonHelper.extractWithDefault(params, "output binary file", true)
  val outputPlainTextFile = JsonHelper.extractWithDefault(params, "output plain text file", false)
  val shouldShardGraph = JsonHelper.extractWithDefault(params, "shard plain text graph", false)
  val outputMatrices = JsonHelper.extractWithDefault(params, "output matrices", false)
  val maxMatrixFileSize = JsonHelper.extractWithDefault(params, "max matrix file size", 100000)

  val matrixOutDir = outdir + "matrices/"
  val plainTextFilename = outdir + "graph_chi/edges.tsv"
  val binaryFilename = outdir + "edges.dat"
  val nodeDictFilename = outdir + "node_dict.tsv"
  val edgeDictFilename = outdir + "edge_dict.tsv"

  override val outputs = {
    val tmp = new mutable.ListBuffer[String]
    tmp += outdir
    tmp += nodeDictFilename
    tmp += edgeDictFilename
    if (outputBinaryFile) tmp += binaryFilename
    if (outputPlainTextFile) tmp += plainTextFilename
    if (outputMatrices) tmp += matrixOutDir
    tmp.toSet
  }

  // This is from old, experimental code that didn't pan out...
  override def parametersMatch(params1: JValue, params2: JValue): Boolean = {
    super.parametersMatch(
      params1.removeField(_._1.equals("denser matrices")),
      params2.removeField(_._1.equals("denser matrices"))
    )
  }

  override def _runStep() {
    outputter.info(s"Creating graph $name in $outdir")

    outputter.info("Loading aliases")
    val aliases = relationSets.filter(_.isKb).par.map(relationSet => {
      (relationSet.aliasRelation, relationSet.getAliases)
    }).seq

    val nodeDict = new MutableConcurrentDictionary()
    val edgeDict = new MutableConcurrentDictionary()

    createGraphFiles(aliases, nodeDict, edgeDict)

    // Adding edges is now finished, and the dictionaries aren't getting any more entries, so we
    // can output them.
    outputter.info("Outputting dictionaries to disk")
    val nodeDictFile = fileUtil.getFileWriter(nodeDictFilename)
    val edgeDictFile = fileUtil.getFileWriter(edgeDictFilename)
    nodeDict.writeToWriter(nodeDictFile)
    nodeDictFile.close()
    edgeDict.writeToWriter(edgeDictFile)
    edgeDictFile.close()

    // This is for if you want to do the path following step with matrix multiplications instead of
    // with random walks.  This was basically a failed experiment, and is not recommended.
    if (outputMatrices) {
      createMatrices(outdir + "graph_chi/edges.tsv", maxMatrixFileSize)
    }
  }

  def createGraphFiles(
    aliases: Seq[(String, Map[String, Seq[String]])],
    nodeDict: MutableConcurrentDictionary,
    edgeDict: MutableConcurrentDictionary
  ) {
    val seenNps = new mutable.HashSet[String]
    val seenTriples: mutable.HashSet[(Int, Int, Int)] = {
      if (deduplicateEdges) {
        new mutable.HashSet[(Int, Int, Int)]
      } else {
        null
      }
    }
    val prefixes = getSvoPrefixes(relationSets)

    val plainTextFile = if (outputPlainTextFile) {
      fileUtil.mkdirs(outdir + "graph_chi/")
      Some(fileUtil.getFileWriter(plainTextFilename))
    } else {
      None
    }

    val binaryFile = if (outputBinaryFile) {
      Some(fileUtil.getDataOutputStream(outdir + "edges.dat"))
    } else {
      None
    }

    var numEdges = 0
    for (relationSet <- relationSets) {
      outputter.info("Adding edges to the graph from " + relationSet.relationFile)
      val prefix = prefixes(relationSet)
      numEdges += relationSet.writeRelationEdgesToGraphFile(
        plainTextFile,
        binaryFile,
        seenTriples,
        prefix,
        seenNps,
        aliases,
        nodeDict,
        edgeDict
      )
    }
    plainTextFile.foreach(_.close())
    binaryFile.foreach(_.close())

    if (outputPlainTextFile && shouldShardGraph) {
      // Now decide how many shards to do, based on the number of edges that are in the graph.
      val numShards = getNumShards(numEdges)
      val writer = fileUtil.getFileWriter(outdir + "num_shards.tsv")
      writer.write(numShards + "\n")
      writer.close()
      if (shouldShardGraph) {
        shardGraph(plainTextFilename, numShards)
      }
    }
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

  def createMatrices(filename: String, maxMatrixFileSize: Int) {
    outputter.info("Creating matrices")
    fileUtil.mkdirs(outdir + "matrices/")
    outputter.info("Reading edge file")
    var line: String = null
    val lines = fileUtil.readLinesFromFile(filename)
    val matrices = lines.par.map(line => {
      val fields = line.split("\t")
      (fields(0).toInt, fields(1).toInt, fields(2).toInt)
    }).groupBy(x => x._3).toMap.mapValues(triple_set => {
      triple_set.map(triple => (triple._1, triple._2)).seq.toSeq
    }).seq
    val numRelations = matrices.map(_._1).max
    outputter.info("Outputting matrix files")
    val edgesToWrite = new mutable.ArrayBuffer[Seq[(Int, Int)]]
    var startRelation = 1
    var edgesSoFar = 0
    for (i <- 1 to numRelations) {
      val matrix = matrices.getOrElse(i, Nil)
      if (matrix.size == 0) outputter.warn("RELATION WITH NO INSTANCES: " + i)
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
    outputter.info("Done creating matrices")
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
    val baseDir = baseGraphDir.replace("graphs/", "")
    val creator = synthetic_data_creator_factory.getSyntheticDataCreator(baseDir, params, outputter, fileUtil)
    if (fileUtil.fileExists(creator.relation_set_dir)) {
      fileUtil.blockOnFileDeletion(creator.in_progress_file)
      val current_params = parse(fileUtil.readLinesFromFile(creator.param_file).mkString("\n"))
      if (current_params.equals(JNothing)) {
        outputter.warn(s"Odd...  couldn't read parameters from ${creator.param_file}, even though " +
          s"${creator.relation_set_dir} exists")
      }
      if (current_params != params) {
        outputter.fatal(s"Parameters found in ${creator.param_file}: ${pretty(render(current_params))}")
        outputter.fatal(s"Parameters specified in spec file: ${pretty(render(params))}")
        outputter.fatal(s"Difference: ${current_params.diff(params)}")
        throw new IllegalStateException("Synthetic data parameters don't match!")
      }
    } else {
      creator.createRelationSet()
    }
    val relSetParams = ("relation file" -> creator.data_file) ~ ("is kb" -> false)
    new RelationSet(relSetParams, outputter, fileUtil)
  }
}
