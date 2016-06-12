package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.graphs.Graph
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper

import scala.collection.mutable

import org.json4s._

class RelationMetadata(
  params: JValue,
  praBase: String,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
) {
  implicit val formats = DefaultFormats

  val baseDir: String = params match {
    case JNothing => null
    case JString(path) if (path.startsWith("/")) => fileUtil.addDirectorySeparatorIfNecessary(path)
    case JString(name) => s"${praBase}relation_metadata/${name}/"
    case jval => {
      jval \ "name" match {
        case JString(name) => s"${praBase}relation_metadata/${name}/"
        case _ => {
          jval \ "directory" match {
            case JString(dir) => dir
            case _ => {
              outputter.warn("Couldn't find a base directory for relation metadata...")
              null
            }
          }
        }
      }
    }
  }

  val embeddingsFile = JsonHelper.extractWithDefault(params, "embeddings", baseDir + "embeddings.tsv")
  val useEmbeddings = JsonHelper.extractWithDefault(params, "use embeddings", false)
  val inversesFile = JsonHelper.extractWithDefault(params, "inverses", baseDir + "inverses.tsv")
  val rangeFile = JsonHelper.extractWithDefault(params, "ranges", baseDir + "ranges.tsv")
  val domainFile = JsonHelper.extractWithDefault(params, "domains", baseDir + "domains.tsv")

  lazy val ranges = if (fileUtil.fileExists(rangeFile)) {
    Some(fileUtil.readMapFromTsvFile(rangeFile))
  } else {
    outputter.logToFile("No range file found! I hope your accept policy is as you want it...\n")
    outputter.warn(s"No range file found! (looked in $rangeFile)")
    None
  }
  lazy val domains = if (fileUtil.fileExists(domainFile)) {
    Some(fileUtil.readMapFromTsvFile(domainFile))
  } else {
    outputter.logToFile("No domain file found! I hope your accept policy is as you want it...\n")
    outputter.warn(s"No domain file found! (looked in $domainFile)")
    None
  }
  lazy val embeddings = if (useEmbeddings) fileUtil.readMapListFromTsvFile(embeddingsFile) else null
  lazy val inverses = {
    val _inverses = new mutable.HashMap[String, String]
    if (!fileUtil.fileExists(inversesFile)) {
      _inverses.toMap
    } else {
      for (line <- fileUtil.readLinesFromFile(inversesFile)) {
        val parts = line.split("\t")
        _inverses.put(parts(0), parts(1))
        // Just for good measure, in case the file only lists each relation once.
        _inverses.put(parts(1), parts(0))
      }
      _inverses.toMap
    }
  }

  def getUnallowedEdges(relation: String, graph: Graph): Seq[Int] = {
    val unallowedEdges = new mutable.ArrayBuffer[Int]

    if (!graph.hasEdge(relation)) return unallowedEdges.toSeq

    // The relation itself is an unallowed edge type.
    val relIndex = graph.getEdgeIndex(relation)
    unallowedEdges += relIndex

    val inverse = inverses.get(relation)
    inverse match {
      // If the relation has an inverse, it's an unallowed edge type.
      case Some(i) => unallowedEdges += graph.getEdgeIndex(i)
      case None => {}
    }

    // And if the relation has an embedding (really a set of cluster ids), those should be
    // added to the unallowed edge type list.
    if (embeddings != null) {
      for (embedded <- embeddings.getOrElse(relation, Nil)) {
        unallowedEdges += graph.getEdgeIndex(embedded)
      }
      inverse match {
        case Some(i) => {
          for (embedded <- embeddings.getOrElse(i, Nil)) {
            unallowedEdges += graph.getEdgeIndex(embedded)
          }
        }
        case None => {}
      }
    }
    unallowedEdges.toSeq
  }

  def getAllowedTargets(relation: String, graph: Option[Graph]): Option[Set[Int]] = {
    getDomainOrRange(relation, ranges, graph)
  }

  def getAllowedSources(relation: String, graph: Option[Graph]): Option[Set[Int]] = {
    getDomainOrRange(relation, domains, graph)
  }

  def getDomainOrRange(
    relation: String,
    domainOrRange: Option[Map[String, String]],
    graph: Option[Graph]
  ): Option[Set[Int]] = {
    domainOrRange match {
      case Some(mapping) => {
        val category = mapping.get(relation) match {
          case None => throw new IllegalStateException(
              "You specified a range/domain file, but it doesn't contain an entry for relation " + relation)
          case Some(r) => r
        }
        val fixed = category.replace("/", "_")
        val cat_file = baseDir + "category_instances/" + fixed

        val allowedNodes = graph match {
          case None => Some(Set[Int]())
          case Some(graph) => {
            val lines = fileUtil.readLinesFromFile(cat_file)
            Some(lines.flatMap(line => {
              if (graph.hasNode(line)) {
                Seq(graph.getNodeIndex(line))
              } else {
                Seq()
              }
            }).toSet)
          }
        }
        allowedNodes
      }
      case None => None
    }
  }
}

object RelationMetadata {
  val empty = new RelationMetadata(JNothing, "/dev/null", Outputter.justLogger)
}
