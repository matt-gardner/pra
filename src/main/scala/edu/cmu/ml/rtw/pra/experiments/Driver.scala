package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.config.SpecFileReader
import edu.cmu.ml.rtw.pra.features.FeatureGenerator
import edu.cmu.ml.rtw.pra.graphs.GraphCreator
import edu.cmu.ml.rtw.pra.graphs.GraphDensifier
import edu.cmu.ml.rtw.pra.graphs.PcaDecomposer
import edu.cmu.ml.rtw.pra.graphs.SimilarityMatrixCreator
import edu.cmu.ml.rtw.pra.models.PraModel

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.json4s._
import org.json4s.native.JsonMethods.{pretty,render,parse}

// TODO(matt): This class has acrued a few too many functions, I think.  It's not focused enough.
// Maybe I should move the graph creation code somewhere else...
class Driver(praBase: String, fileUtil: FileUtil = new FileUtil()) {
  implicit val formats = DefaultFormats

  def runPra(_outputBase: String, params: JValue) {
    val outputBase = fileUtil.addDirectorySeparatorIfNecessary(_outputBase)
    fileUtil.mkdirOrDie(outputBase)

    // We create the graph first here, because we allow a "no op" PRA mode, which means just create
    // the graph and quit.  But we have to do this _after_ we create the output directory, or we
    // could get two threads trying to do the same experiment when one of them has to create a
    // graph first.  We'll delete the output directory in the case of a no op.
    createGraphIfNecessary(params \ "graph")

    // And these are all part of "creating the graph", basically, they just deal with augmenting
    // the graph by doing some factorization.
    createEmbeddingsIfNecessary(params)
    createSimilarityMatricesIfNecessary(params)
    createDenserMatricesIfNecessary(params)

    val mode = (params \ "pra parameters" \ "mode") match {
      case JNothing => "standard"
      case JString(m) => m
      case other => throw new IllegalStateException("something is wrong in specifying the pra mode")
    }
    if (mode == "no op") {
      fileUtil.deleteFile(outputBase)
      return
    }

    val metadataDirectory: String = (params \ "relation metadata") match {
      case JNothing => null
      case JString(path) if (path.startsWith("/")) => fileUtil.addDirectorySeparatorIfNecessary(path)
      case JString(name) => s"${praBase}relation_metadata/${name}/"
      case other => throw new IllegalStateException("relation metadata parameter must be either "
        + "a string or absent")
    }
    val splitsDirectory = (params \ "split") match {
      case JString(path) if (path.startsWith("/")) => fileUtil.addDirectorySeparatorIfNecessary(path)
      case JString(name) => s"${praBase}splits/${name}/"
      case other => throw new IllegalStateException("split must be a string")
    }

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
      builder.setRelation(relation)
      println("\n\n\n\nRunning PRA for relation " + relation)
      parseRelationMetadata(metadataDirectory, relation, builder, outputBase)

      val outdir = fileUtil.addDirectorySeparatorIfNecessary(outputBase + relation)
      fileUtil.mkdirs(outdir)
      builder.setOutputBase(outdir)

      if (mode == "standard") {
        val doCrossValidation = Driver.initializeSplit(
          splitsDirectory,
          metadataDirectory,
          relation,
          builder,
          new DatasetFactory(),
          fileUtil)

        val config = builder.build()

        // Split the data if we're doing cross validation instead of a fixed split.
        if (doCrossValidation) {
          val splitData = config.allData.splitData(config.percentTraining)
          val trainingData = splitData.getLeft()
          val testingData = splitData.getRight()
          config.outputter.outputSplitFiles(config.outputBase, trainingData, testingData)
          val builder = new PraConfig.Builder(config)
          builder.setAllData(null)
          builder.setPercentTraining(0)
          builder.setTrainingData(trainingData)
          builder.setTestingData(testingData)
        }

        // Now we actually run PRA.

        // First we train the model.
        val generator = new FeatureGenerator(config)
        val pathTypes = generator.selectPathFeatures(config.trainingData)
        val trainingMatrix = generator.computeFeatureValues(pathTypes, config.trainingData, null)
        val praModel = new PraModel(config)
        val weights = praModel.learnFeatureWeights(trainingMatrix, config.trainingData, pathTypes.asJava)
        val finalModel = pathTypes.zip(weights.asScala).filter(_._2 != 0.0)
        val finalPathTypes = finalModel.map(_._1)
        val finalWeights = finalModel.map(_._2).asJava

        // Then we test it.
        val output = if (config.outputBase == null) null else config.outputBase + "test_matrix.tsv"
        val testMatrix = generator.computeFeatureValues(finalPathTypes, config.testingData, output)
        val scores = praModel.classifyInstances(testMatrix, finalWeights)
        config.outputter.outputScores(config.outputBase + "scores.tsv", scores, config)
      } else if (mode == "exploration") {
        val dataToUse = (params \ "pra parameters" \ "explore") match {
          case JNothing => "both"
          case JString("training") => "training"
          case JString("testing") => "testing"
          case JString("both") => "both"
          case other => throw new IllegalStateException("explore parameter must be a string, " +
            "either \"training\", \"testing\", or \"both\"")
        }
        val datasetFactory = new DatasetFactory()
        if (dataToUse == "both") {
          val trainingFile = s"${splitsDirectory}${relation}/training.tsv"
          val trainingData = datasetFactory.fromFile(trainingFile, builder.nodeDict)
          val testingFile = s"${splitsDirectory}${relation}/testing.tsv"
          val testingData = datasetFactory.fromFile(testingFile, builder.nodeDict)
          builder.setTrainingData(trainingData.merge(testingData))
        } else {
          val inputFile = s"${splitsDirectory}${relation}/${dataToUse}.tsv"
          builder.setTrainingData(datasetFactory.fromFile(inputFile, builder.nodeDict))
        }
        val config = builder.build()

        val generator = new FeatureGenerator(config)
        generator.findConnectingPaths(config.trainingData)
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
    var params_specified = false
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
        params_specified = true
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
        if (params_specified == true && !graphParamsMatch(current_params, params)) {
          println(s"Parameters found in ${creator.paramFile}: ${pretty(render(current_params))}")
          println(s"Parameters specified in spec file: ${pretty(render(params))}")
          println(s"Difference: ${current_params.diff(params)}")
          throw new IllegalStateException("Graph parameters don't match!")
        }
      } else {
        creator.createGraphChiRelationGraph(params)
      }
    }
  }

  def graphParamsMatch(params1: JValue, params2: JValue): Boolean = {
    return params1.removeField(_._1.equals("denser matrices")) ==
      params2.removeField(_._1.equals("denser matrices"))
  }

  def createEmbeddingsIfNecessary(params: JValue) {
    val embeddings = params.filterField(field => field._1.equals("embeddings")).flatMap(_._2 match {
      case JArray(list) => list
      case other => List(other)
    })
    embeddings.filter(_ match {case JString(name) => false; case other => true })
      .par.map(embedding_params => {
        val name = (embedding_params \ "name").extract[String]
        val embeddingsDir = s"${praBase}embeddings/$name/"
        val paramFile = embeddingsDir + "params.json"
        val graph = praBase + "graphs/" + (embedding_params \ "graph").extract[String] + "/"
        val decomposer = new PcaDecomposer(graph, embeddingsDir)
        if (!fileUtil.fileExists(embeddingsDir)) {
          val dims = (embedding_params \ "dims").extract[Int]
          decomposer.createPcaRelationEmbeddings(dims)
          val out = fileUtil.getFileWriter(paramFile)
          out.write(pretty(render(embedding_params)))
          out.close
        } else {
          fileUtil.blockOnFileDeletion(decomposer.in_progress_file)
          val current_params = parse(fileUtil.readLinesFromFile(paramFile).asScala.mkString("\n"))
          if (current_params != embedding_params) {
            println(s"Parameters found in ${paramFile}: ${pretty(render(current_params))}")
            println(s"Parameters specified in spec file: ${pretty(render(embedding_params))}")
            println(s"Difference: ${current_params.diff(embedding_params)}")
            throw new IllegalStateException("Embedding parameters don't match!")
          }
        }
    })
  }

  def createSimilarityMatricesIfNecessary(params: JValue) {
    val matrices = params.filterField(field => field._1.equals("similarity matrix")).flatMap(_._2 match {
      case JArray(list) => list
      case other => List(other)
    })
    matrices.filter(_ match {case JString(name) => false; case other => true })
      .par.map(matrixParams => {
        val embeddingsDir = getEmbeddingsDir(matrixParams \ "embeddings")
        val name = (matrixParams \ "name").extract[String]
        val creator = new SimilarityMatrixCreator(embeddingsDir, name)
        if (!fileUtil.fileExists(creator.matrixDir)) {
          creator.createSimilarityMatrix(matrixParams)
        } else {
          fileUtil.blockOnFileDeletion(creator.inProgressFile)
          val current_params = parse(fileUtil.readLinesFromFile(creator.paramFile).asScala.mkString("\n"))
          if (current_params != matrixParams) {
            println(s"Parameters found in ${creator.paramFile}: ${pretty(render(current_params))}")
            println(s"Parameters specified in spec file: ${pretty(render(matrixParams))}")
            println(s"Difference: ${current_params.diff(matrixParams)}")
            throw new IllegalStateException("Similarity matrix parameters don't match!")
          }
        }
    })
  }

  def getEmbeddingsDir(params: JValue): String = {
    params match {
      case JString(path) if (path.startsWith("/")) => path
      case JString(name) => s"${praBase}embeddings/$name/"
      case jval => {
        val name = (jval \ "name").extract[String]
        s"${praBase}embeddings/$name/"
      }
    }
  }

  def createDenserMatricesIfNecessary(params: JValue) {
    val matrices = params.filterField(field => field._1.equals("denser matrices")).flatMap(_._2 match {
      case JArray(list) => list
      case other => List(other)
    })
    matrices.filter(_ match {case JString(name) => false; case other => true })
      .par.map(matrixParams => {
        val graphName = (params \ "graph" \ "name").extract[String]
        val graphDir = s"${praBase}/graphs/${graphName}/"
        val name = (matrixParams \ "name").extract[String]
        val densifier = new GraphDensifier(praBase, graphDir, name)
        if (!fileUtil.fileExists(densifier.matrixDir)) {
          densifier.densifyGraph(matrixParams)
        } else {
          fileUtil.blockOnFileDeletion(densifier.inProgressFile)
          val current_params = parse(fileUtil.readLinesFromFile(densifier.paramFile).asScala.mkString("\n"))
          if (current_params != matrixParams) {
            println(s"Parameters found in ${densifier.paramFile}: ${pretty(render(current_params))}")
            println(s"Parameters specified in spec file: ${pretty(render(matrixParams))}")
            println(s"Difference: ${current_params.diff(matrixParams)}")
            throw new IllegalStateException("Denser matrix parameters don't match!")
          }
        }
    })
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
    val inverses = Driver.createInverses(directory, builder.edgeDict, fileUtil)
    builder.setRelationInverses(inverses.map(x => (Integer.valueOf(x._1), Integer.valueOf(x._2))).asJava)

    val embeddings = {
      if (directory != null && fileUtil.fileExists(directory + "embeddings.tsv")) {
        fileUtil.readMapListFromTsvFile(directory + "embeddings.tsv").asScala
          .mapValues(_.asScala.toList).toMap
      } else {
        null
      }
    }
    val unallowedEdges = Driver.createUnallowedEdges(relation, inverses, embeddings, builder.edgeDict)
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
}

object Driver {

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
  def createInverses(
      directory: String,
      dict: Dictionary,
      fileUtil: FileUtil = new FileUtil): Map[Int, Int] = {
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
      datasetFactory: DatasetFactory,
      fileUtil: FileUtil = new FileUtil) = {
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
