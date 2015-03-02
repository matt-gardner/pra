package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.graphchi.ChiLogger
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.config.SpecFileReader
import edu.cmu.ml.rtw.pra.graphs.GraphCreator

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.json4s.{DefaultFormats,JValue,JNothing,JString}
import org.json4s.native.JsonMethods.{pretty,render,parse}

class Driver(praBase: String, fileUtil: FileUtil = new FileUtil()) {
  private val logger = ChiLogger.getLogger("pra-driver")

  implicit val formats = DefaultFormats
  def runPra(_outputBase: String, params: JValue) {
    val outputBase = fileUtil.addDirectorySeparatorIfNecessary(_outputBase)

    val metadataDirectory: String = (params \ "relation metadata") match {
      case JNothing => null
      case string: JString => fileUtil.addDirectorySeparatorIfNecessary(string.extract[String])
      case default => throw new IllegalStateException("relation metadata parameter must be " +
        "either a string or absent")
    }
    val splitsDirectory = (params \ "split").extract[String] match {
      case path if (path.startsWith("/")) => fileUtil.addDirectorySeparatorIfNecessary(path)
      case name => s"${praBase}splits/${name}/"
    }

    fileUtil.mkdirOrDie(outputBase)
    createGraphIfNecessary(params \ "graph")

    val start_time = System.currentTimeMillis

    val baseBuilder = new PraConfig.Builder()
    var writer = fileUtil.getFileWriter(outputBase + "settings.txt")
    writer.write("Parameters used:\n")
    writer.write(pretty(render(params)))
    writer.write("\n")
    writer.close()

    // This takes care of setting everything in the config builder that is consistent across
    // relations.
    new SpecFileReader(praBase, fileUtil).setPraConfigFromParams(params, baseBuilder)

    var nodeNames: java.util.Map[String, String] = null
    if (metadataDirectory != null && fileUtil.fileExists(metadataDirectory + "node_names.tsv")) {
      nodeNames = fileUtil.readMapFromTsvFile(metadataDirectory + "node_names.tsv", true)
    }
    baseBuilder.setOutputter(new Outputter(baseBuilder.nodeDict, baseBuilder.edgeDict, nodeNames))

    val baseConfig = baseBuilder.noChecks().build()

    val relationsFile = splitsDirectory + "relations_to_run.tsv"
    for (relation <- fileUtil.readLinesFromFile(relationsFile).asScala) {
      val relation_start = System.currentTimeMillis
      val builder = new PraConfig.Builder(baseConfig)
      logger.info("\n\n\n\nRunning PRA for relation " + relation)
      parseRelationMetadata(metadataDirectory, relation, builder, outputBase)

      val outdir = fileUtil.addDirectorySeparatorIfNecessary(outputBase + relation)
      fileUtil.mkdirs(outdir)
      builder.setOutputBase(outdir)

      val doCrossValidation = initializeSplit(
        splitsDirectory,
        metadataDirectory,
        relation,
        builder,
        new DatasetFactory())

      val config = builder.build()

      // Run PRA
      if (doCrossValidation) {
        new PraTrainAndTester().crossValidate(config)
      } else {
        new PraTrainAndTester().trainAndTest(config)
      }
      val relation_end = System.currentTimeMillis
      val millis = relation_end - relation_start
      var seconds = (millis / 1000).toInt
      val minutes = seconds / 60
      seconds = seconds - minutes * 60
      writer = fileUtil.getFileWriter(outputBase + "settings.txt", true)  // true -> append to the file.
      writer.write(s"Time for relation $relation: $minutes minutes and $seconds seconds\n")
      writer.close()
    }
    val end_time = System.currentTimeMillis
    val millis = end_time - start_time
    var seconds = (millis / 1000).toInt
    val minutes = seconds / 60
    seconds = seconds - minutes * 60
    writer = fileUtil.getFileWriter(outputBase + "settings.txt", true)  // true -> append to the file.
    writer.write("PRA appears to have finished all relations successfully\n")
    writer.write(s"Total time: $minutes minutes and $seconds seconds\n")
    writer.close()
    System.out.println(s"Took $minutes minutes and $seconds seconds")
  }

  def createGraphIfNecessary(params: JValue) {
    var graph_name = ""
    // First, is this just a path, or do the params specify a graph name?  If it's a path, we'll
    // just use the path as is.  Otherwise, we have some processing to do.
    params match {
      case path: JString if (path.extract[String].startsWith("/")) => {
        if (!fileUtil.fileExists(path.extract[String])) {
          throw new IllegalStateException("Specified path to graph does not exist!")
        }
      }
      case name: JString => {
        graph_name = name.extract[String]
      }
      case jval: JValue => {
        graph_name = (jval \ "name").extract[String]
      }
    }
    if (graph_name != "") {
      // Here we need to see if the graph has already been created, and (if so) whether the graph
      // as specified matches what's already been created.
      val graph_dir = s"${praBase}graphs/${graph_name}/"
      val creator = new GraphCreator(praBase, graph_dir, fileUtil)
      if (fileUtil.fileExists(graph_dir)) {
        fileUtil.blockOnFileDeletion(creator.inProgressFile)
        val current_params = parse(fileUtil.readLinesFromFile(creator.paramFile).asScala.mkString("\n"))
        if (current_params != params) {
          throw new IllegalStateException("Graph parameters don't match!")
        }
      } else {
        creator.createGraphChiRelationGraph(params)
      }
    }
  }

  /**
   * Here we set up the PraConfig items that have to do with the input KB files.  In particular,
   * that means deciding which relations are known to be inverses of each other, which edges
   * should be ignored because using them to predict new relations instances would consitute
   * cheating, and setting the range and domain of a relation to restrict new predictions.
   *
   * Also, if the relations have been embedded into a latent space, we perform a mapping here
   * when deciding which edges to ignore.  This means that each embedding of a KB graph has to
   * have a different directory.
   */
  def parseRelationMetadata(
      directory: String,
      relation: String,
      builder: PraConfig.Builder,
      outputBase: String) {
    val inverses = createInverses(directory, builder.edgeDict)
    builder.setRelationInverses(inverses.map(x => (Integer.valueOf(x._1), Integer.valueOf(x._2))).asJava)

    val embeddings = {
      if (directory != null && fileUtil.fileExists(directory + "embeddings.tsv")) {
        fileUtil.readMapListFromTsvFile(directory + "embeddings.tsv").asScala
          .mapValues(_.asScala.toList).toMap
      } else {
        null
      }
    }
    val unallowedEdges = createUnallowedEdges(relation, inverses, embeddings, builder.edgeDict)
    builder.setUnallowedEdges(unallowedEdges.map(x => Integer.valueOf(x)).asJava)

    if (directory != null && fileUtil.fileExists(directory + "ranges.tsv")) {
      val ranges = fileUtil.readMapFromTsvFile(directory + "ranges.tsv")
      val range = ranges.get(relation)
      if (range == null) {
        throw new IllegalStateException(
            "You specified a range file, but it doesn't contain an entry for relation " + relation)
      }
      val fixed = range.replace("/", "_")
      val cat_file = directory + "category_instances/" + fixed

      val allowedTargets = fileUtil.readIntegerSetFromFile(cat_file, builder.nodeDict)
      builder.setAllowedTargets(allowedTargets)
    } else {
      val writer = fileUtil.getFileWriter(outputBase + "settings.txt", true)  // true -> append
      writer.write("No range file found! I hope your accept policy is as you want it...\n")
      println("No range file found!")
      writer.close()
    }
  }

  def createUnallowedEdges(
      relation: String,
      inverses: Map[Int, Int],
      embeddings: Map[String, List[String]],
      edgeDict: Dictionary): List[Int] = {
    val unallowedEdges = new mutable.ArrayBuffer[Int]

    // The relation itself is an unallowed edge type.
    val relIndex = edgeDict.getIndex(relation)
    unallowedEdges += relIndex

    // If the relation has an inverse, it's an unallowed edge type.
    inverses.get(relIndex).map(index => unallowedEdges += index)

    val inverse = inverses.get(relIndex) match {
      case Some(index) => edgeDict.getString(index)
      case _ => null
    }

    // And if the relation has an embedding (really a set of cluster ids), those should be
    // added to the unallowed edge type list.
    if (embeddings != null) {
      for (embedded <- embeddings.getOrElse(relation, Nil)) {
        unallowedEdges += edgeDict.getIndex(embedded)
      }
      if (inverse != null) {
        for (embedded <- embeddings.getOrElse(inverse, Nil)) {
          unallowedEdges += edgeDict.getIndex(embedded)
        }
      }
    }
    unallowedEdges.toList
  }

  /**
   * Reads a file containing a mapping between relations and their inverses, and returns the
   * result as a map.
   */
  def createInverses(directory: String, dict: Dictionary): Map[Int, Int] = {
    val inverses = new mutable.HashMap[Int, Int]
    if (directory == null) {
      inverses.toMap
    } else {
      val filename = directory + "inverses.tsv"
      if (!fileUtil.fileExists(filename)) {
        inverses.toMap
      } else {
        for (line <- fileUtil.readLinesFromFile(filename).asScala) {
          val parts = line.split("\t")
          val rel1 = dict.getIndex(parts(0))
          val rel2 = dict.getIndex(parts(1))
          inverses.put(rel1, rel2)
          // Just for good measure, in case the file only lists each relation once.
          inverses.put(rel2, rel1)
        }
        inverses.toMap
      }
    }
  }

  def initializeSplit(
      splitsDirectory: String,
      relationMetadataDirectory: String,
      relation: String,
      builder: PraConfig.Builder,
      datasetFactory: DatasetFactory) = {
    val fixed = relation.replace("/", "_")
    // We look in the splits directory for a fixed split if we don't find one, we do cross
    // validation.
    if (fileUtil.fileExists(splitsDirectory + fixed)) {
      val training = splitsDirectory + fixed + "/training.tsv"
      val testing = splitsDirectory + fixed + "/testing.tsv"
      builder.setTrainingData(datasetFactory.fromFile(training, builder.nodeDict))
      builder.setTestingData(datasetFactory.fromFile(testing, builder.nodeDict))
      false
    } else {
      if (relationMetadataDirectory == null) {
        throw new IllegalStateException("Must specify a relation metadata directory if you do not "
          + "have a fixed split!")
      }
      builder.setAllData(datasetFactory.fromFile(relationMetadataDirectory + "relations/" + fixed,
                                                 builder.nodeDict))
      val percent_training_file = splitsDirectory + "percent_training.tsv"
      builder.setPercentTraining(fileUtil.readDoubleListFromFile(percent_training_file).get(0))
      true
    }
  }
}
