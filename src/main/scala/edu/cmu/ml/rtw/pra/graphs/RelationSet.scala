package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.Dictionary
import com.mattg.util.FileUtil
import com.mattg.util.LineFilter
import com.mattg.util.IntTriple
import com.mattg.util.JsonHelper
import com.mattg.util.Pair

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.FileWriter

import scala.collection.mutable

import org.json4s._

class RelationSet(params: JValue, outputter: Outputter, fileUtil: FileUtil = new FileUtil) {
  implicit val formats = DefaultFormats

  // Fields dealing with the relations themselves.

  // The file containing the relation triples.
  val relationFile = (params \ "relation file").extract[String]

  // If not null, prepend this prefix to all relation strings in this set.
  val relationPrefix = JsonHelper.extractWithDefault(params, "relation prefix", null: String)

  // KB relations or surface relations?  The difference is only in the alias relation format (and
  // in the relation file format).
  val isKb = JsonHelper.extractWithDefault(params, "is kb", false)

  // Fields specific to KB relations.

  // If this is a KB relation set, this file contains the mapping from entities to noun phrases.
  val aliasFile = JsonHelper.extractWithDefault(params, "alias file", null: String)

  // What should we call the alias relation?  Defaults to "@ALIAS@".  Note that we specify this for
  // each set of _KB_ relations, not for each set of _surface_ relations.  If you want something
  // more complicated, like a different name for each (KB, surface) relation set pair, you'll have
  // to approximate it with relation prefixes.
  val aliasRelation = JsonHelper.extractWithDefault(params, "alias relation", "@ALIAS@")

  // Determines how we try to read the alias file.  We currently allow two values: "freebase" and
  // "nell".  The freebase format is a little complicated the nell format is just a list of
  // (concept, noun phrase) pairs that we read into a map.  The "nell" format is generic enough
  // that it could be used for more than just NELL KBs, probably.
  val aliasFileFormat = JsonHelper.extractWithDefault(params, "alias file format", null: String)


  // Fields specific to surface relations.

  // If this is true, we only add the alias edges that get generated from the set, not the
  // relations themselves (either from a KB relation set or a surface relation set).  This is still
  // somewhat experimental, but there are some cases where you might want to try this.
  val aliasesOnly = JsonHelper.extractWithDefault(params, "aliases only", false)

  // Fields for embeddings.

  // The file where we can find embeddings for the relations in this set.
  val embeddingsFile = JsonHelper.extractWithDefault(params, "embeddings file", null: String)

  // If we are embedding the edges, should we replace the original edges or just augment them?
  val keepOriginalEdges = JsonHelper.extractWithDefault(params, "keep original edges", false)

  val replaceRelationsWith = JsonHelper.extractWithDefault(params, "replace relations with", null: String)

  /**
   * Get the set of aliases specified by this KB relation set.
   */
  def getAliases(): Map[String, Seq[String]] = {
    if (aliasFile == null) {
      return Map()
    }
    aliasFileFormat match {
      case "freebase" => {
        fileUtil.readMapListFromTsvFile(aliasFile, 3, false, new LineFilter() {
          override def filter(fields: Array[String]): Boolean = {
            if (fields.length != 4) return true
            return false
          }
        })
      }
      case "nell" => {
        fileUtil.readInvertedMapListFromTsvFile(aliasFile, 1000000)
      }
      case other => throw new IllegalStateException("Unrecognized alias file format")
    }
  }

  def writeRelationEdgesToGraphFile(
    plainTextWriter: Option[FileWriter],
    binaryWriter: Option[DataOutputStream],
    seenTriples: mutable.HashSet[(Int, Int, Int)],
    prefixOverride: String,
    seenNps: mutable.HashSet[(String)],
    aliases: Seq[(String, Map[String, Seq[String]])],
    nodeDict: Dictionary,
    edgeDict: Dictionary
  ): Int = {
    outputter.info(s"Adding edges from relation file $relationFile")
    val prefix = {
      if (prefixOverride != null) {
        prefixOverride
      } else if (relationPrefix != null) {
        relationPrefix
      } else {
        ""
      }
    }
    var i = 0
    var numEdges = 0
    for (line <- fileUtil.getLineIterator(relationFile)) {
      i += 1
      fileUtil.logEvery(1000000, i)
      val fields = line.split("\t")
      // TODO(matt): Maybe make this relation file format a configurable field?
      val (arg1, arg2, relation) = if (isKb) {
        // KB relations are formated (S, O, V).
        (fields(0), fields(1), fields(2))
      } else {
        // And surface relations are formatted (S, V, O).
        (fields(0), fields(2), fields(1))
      }
      val ind1 = nodeDict.getIndex(arg1)
      val ind2 = nodeDict.getIndex(arg2)

      if (!isKb) {
        numEdges += addAliasEdges(arg1, ind1, seenNps, plainTextWriter, binaryWriter, nodeDict, edgeDict, aliases)
        numEdges += addAliasEdges(arg2, ind2, seenNps, plainTextWriter, binaryWriter, nodeDict, edgeDict, aliases)
      }

      if (!aliasesOnly) {
        val replaced = replaceRelation(relation)
        val relationEdges = getEmbeddedRelations(replaced, loadEmbeddings())
        for (relation <- relationEdges) {
          val prefixed_relation = prefix + relation
          val relation_index = edgeDict.getIndex(prefixed_relation)
          writeEdgeIfUnseen(ind1, ind2, relation_index, seenTriples, plainTextWriter, binaryWriter)
          numEdges += 1
        }
      }
    }
    numEdges
  }

  def writeEdgeIfUnseen(
      arg1: Int,
      arg2: Int,
      rel: Int,
      seenTriples: mutable.HashSet[(Int, Int, Int)],
      plainTextWriter: Option[FileWriter],
      binaryWriter: Option[DataOutputStream]) {
    if (seenTriples != null) {
      val triple = (arg1, arg2, rel)
      if (seenTriples.contains(triple)) return
      seenTriples.add(triple)
    }
    writeTriple(arg1, arg2, rel, plainTextWriter, binaryWriter)
  }

  def addAliasEdges(
      np: String,
      npIndex: Int,
      seenNps: mutable.HashSet[String],
      plainTextWriter: Option[FileWriter],
      binaryWriter: Option[DataOutputStream],
      nodeDict: Dictionary,
      edgeDict: Dictionary,
      aliases: Seq[(String, Map[String, Seq[String]])]): Int = {
    var numEdges = 0
    if (seenNps.contains(np)) return numEdges
    seenNps.add(np)
    for (aliasSet <- aliases) {
      val aliasRelation = aliasSet._1
      val aliasIndex = edgeDict.getIndex(aliasRelation)
      val currentAliases = aliasSet._2
      val concepts = currentAliases.getOrElse(np, Nil)
      for (concept <- concepts) {
        val conceptIndex = nodeDict.getIndex(concept)
        writeTriple(npIndex, conceptIndex, aliasIndex, plainTextWriter, binaryWriter)
        numEdges += 1
      }
    }
    return numEdges
  }

  def writeTriple(
    source: Int,
    target: Int,
    relation: Int,
    plainTextWriter: Option[FileWriter],
    binaryWriter: Option[DataOutputStream]
  ) {
    plainTextWriter.foreach(_.write(s"${source}\t${target}\t${relation}\n"))
    binaryWriter.foreach(w => {
      w.writeInt(source)
      w.writeInt(target)
      w.writeInt(relation)
    })
  }

  def replaceRelation(relation: String): String = {
    if (replaceRelationsWith != null) {
      replaceRelationsWith
    } else {
      relation
    }
  }

  def getEmbeddedRelations(relation: String, embeddings: Map[String, List[String]]) = {
    if (embeddings != null) {
      if (keepOriginalEdges) {
        relation :: embeddings.getOrElse(relation, Nil)
      } else {
        embeddings.getOrElse(relation, List(relation))
      }
    } else {
      List(relation)
    }
  }

  def loadEmbeddings(): Map[String, List[String]] = {
    if (embeddingsFile != null) {
      outputter.info("Reading embeddings from file " + embeddingsFile)
      fileUtil.readMapListFromTsvFile(embeddingsFile).mapValues(_.toList)
    } else {
      null
    }
  }
}
