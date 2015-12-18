package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.data.Split
import edu.cmu.ml.rtw.pra.data.SplitCreator
import edu.cmu.ml.rtw.pra.graphs.GraphCreator
import edu.cmu.ml.rtw.pra.graphs.GraphDensifier
import edu.cmu.ml.rtw.pra.graphs.GraphExplorer
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.PcaDecomposer
import edu.cmu.ml.rtw.pra.graphs.SimilarityMatrixCreator
import edu.cmu.ml.rtw.pra.operations.Operation
import edu.cmu.ml.rtw.users.matt.util.JsonHelper
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.SpecFileReader

import scala.collection.mutable

import org.json4s._
import org.json4s.native.JsonMethods.{pretty,render,parse}

// This class has two jobs.  This first is to create all of the necessary input files from the
// parameter specification (e.g., create actual graph files from the relation sets that are
// specified in the parameters).  This part just looks at the parameters and creates things on the
// filesystem.
//
// The second job is to create all of the (in-memory) objects necessary for running the code, then
// run it.  The general design paradigm for this is that there should be one object per parameter
// key (e.g., "operation", "relation metadata", "split", "graph", etc.).  At each level, the object
// creates all of the sub-objects corresponding to the parameters it has, then performs its
// computation.  This Driver is the top-level object, and its main computation is an Operation.
//
// TODO(matt): The design paradigm mentioned above isn't finished yet.  I originally wrote this
// driver before coming up with that paradigm, so this is kind of kludgy.  In particular, the
// Graph, RelationMetadata, and Split objects need to be designed better (they currently are pretty
// much non-existant, except for Graph).
class Driver(praBase: String, fileUtil: FileUtil = new FileUtil()) {
  implicit val formats = DefaultFormats

  def runPra(_outputBase: String, params: JValue) {
    // The "create" key is special - it's not used for anything here, but if there's some object
    // you want to create with a PRA mode of "no op", and can't or don't want to put the object in
    // the proper nested place, you can put it under "create", and it will be found by the
    // "filterField" calls below.  This will work for creating embeddings, similarity matrices, and
    // (maybe) denser matrices.
    val baseKeys = Seq("graph", "split", "relation metadata", "operation", "output matrices", "create")
    JsonHelper.ensureNoExtras(params, "base", baseKeys)
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

    createSplitIfNecessary(params \ "split")

    val metadataDirectory: String = (params \ "relation metadata") match {
      case JNothing => null
      case JString(path) if (path.startsWith("/")) => fileUtil.addDirectorySeparatorIfNecessary(path)
      case JString(name) => s"${praBase}relation_metadata/${name}/"
      case other => throw new IllegalStateException("relation metadata parameter must be either "
        + "a string or absent")
    }

    val split = Split.create(params \ "split", praBase, fileUtil)

    val operationOption = Operation.create(params \ "operation", split, metadataDirectory, fileUtil)
    operationOption match {
      case None => { fileUtil.deleteFile(outputBase); return }
      case _ => { }
    }
    val operation = operationOption.get

    val start_time = System.currentTimeMillis

    val baseBuilder = new PraConfigBuilder()
    baseBuilder.setPraBase(praBase)
    var writer = fileUtil.getFileWriter(outputBase + "params.json")
    writer.write(pretty(render(params)))
    writer.close()

    val graphDirectory = getGraphDirectory(params)
    if (graphDirectory != null) {
      val dir = fileUtil.addDirectorySeparatorIfNecessary(graphDirectory)
      val graph = new GraphOnDisk(dir)
      baseBuilder.setGraph(graph)
    }

    val nodeNames =
      if (metadataDirectory != null && fileUtil.fileExists(metadataDirectory + "node_names.tsv")) {
        fileUtil.readMapFromTsvFile(metadataDirectory + "node_names.tsv", true)
      } else null
    baseBuilder.setOutputter(new Outputter(nodeNames))
    // TODO(matt): move this parameter to the outputter.
    baseBuilder.setOutputMatrices(JsonHelper.extractWithDefault(params, "output matrices", false))
    Outputter.info(s"Outputting matrices: ${baseBuilder.outputMatrices}")

    val baseConfig = baseBuilder.setNoChecks().build()

    for (relation <- split.relations()) {
      val relation_start = System.currentTimeMillis
      val builder = new PraConfigBuilder(baseConfig)
      builder.setRelation(relation)
      Outputter.info("\n\n\n\nRunning PRA for relation " + relation)

      val outdir = fileUtil.addDirectorySeparatorIfNecessary(outputBase + relation)
      fileUtil.mkdirs(outdir)
      builder.setOutputBase(outdir)

      operation.runRelation(builder)

      val relation_end = System.currentTimeMillis
      val millis = relation_end - relation_start
      var seconds = (millis / 1000).toInt
      val minutes = seconds / 60
      seconds = seconds - minutes * 60
      writer = fileUtil.getFileWriter(outputBase + "log.txt", true)  // true -> append to the file.
      writer.write(s"Time for relation $relation: $minutes minutes and $seconds seconds\n")
      writer.close()
    }
    val end_time = System.currentTimeMillis
    val millis = end_time - start_time
    var seconds = (millis / 1000).toInt
    val minutes = seconds / 60
    seconds = seconds - minutes * 60
    writer = fileUtil.getFileWriter(outputBase + "log.txt", true)  // true -> append to the file.
    writer.write("PRA appears to have finished all relations successfully\n")
    writer.write(s"Total time: $minutes minutes and $seconds seconds\n")
    writer.close()
    Outputter.info(s"Took $minutes minutes and $seconds seconds")
  }

  def createGraphIfNecessary(params: JValue) {
    var graph_name = ""
    var params_specified = false
    // First, is this just a path, or do the params specify a graph name?  If it's a path, we'll
    // just use the path as is.  Otherwise, we have some processing to do.
    params match {
      case JNothing => {}
      case JString(path) if (path.startsWith("/")) => {
        if (!fileUtil.fileExists(path)) {
          throw new IllegalStateException("Specified path to graph does not exist!")
        }
      }
      case JString(name) => graph_name = name
      case jval => {
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
        val current_params = parse(fileUtil.readLinesFromFile(creator.paramFile).mkString("\n"))
        if (params_specified == true && !graphParamsMatch(current_params, params)) {
          Outputter.fatal(s"Parameters found in ${creator.paramFile}: ${pretty(render(current_params))}")
          Outputter.fatal(s"Parameters specified in spec file: ${pretty(render(params))}")
          Outputter.fatal(s"Difference: ${current_params.diff(params)}")
          throw new IllegalStateException("Graph parameters don't match!")
        }
      } else {
        creator.createGraphChiRelationGraph(params)
      }
    }
  }

  // There is a check in the code to make sure that the graph parameters used to create a
  // particular graph in a directory match the parameters you're trying to use with the same graph
  // directory.  But, some things might not matter in that check, like which dense matrices have
  // been created for that graph.  This method specifies which things, exactly, don't matter when
  // comparing two graph parameter specifications.
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
        Outputter.info(s"Checking for embeddings with name ${name}")
        val embeddingsDir = s"${praBase}embeddings/$name/"
        val paramFile = embeddingsDir + "params.json"
        val graph = praBase + "graphs/" + (embedding_params \ "graph").extract[String] + "/"
        val decomposer = new PcaDecomposer(graph, embeddingsDir)
        if (!fileUtil.fileExists(embeddingsDir)) {
          Outputter.info(s"Creating embeddings with name ${name}")
          val dims = (embedding_params \ "dims").extract[Int]
          decomposer.createPcaRelationEmbeddings(dims)
          val out = fileUtil.getFileWriter(paramFile)
          out.write(pretty(render(embedding_params)))
          out.close
        } else {
          fileUtil.blockOnFileDeletion(decomposer.in_progress_file)
          val current_params = parse(fileUtil.readLinesFromFile(paramFile).mkString("\n"))
          if (current_params != embedding_params) {
            Outputter.fatal(s"Parameters found in ${paramFile}: ${pretty(render(current_params))}")
            Outputter.fatal(s"Parameters specified in spec file: ${pretty(render(embedding_params))}")
            Outputter.fatal(s"Difference: ${current_params.diff(embedding_params)}")
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
          val current_params = parse(fileUtil.readLinesFromFile(creator.paramFile).mkString("\n"))
          if (current_params != matrixParams) {
            Outputter.fatal(s"Parameters found in ${creator.paramFile}: ${pretty(render(current_params))}")
            Outputter.fatal(s"Parameters specified in spec file: ${pretty(render(matrixParams))}")
            Outputter.fatal(s"Difference: ${current_params.diff(matrixParams)}")
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
          val current_params = parse(fileUtil.readLinesFromFile(densifier.paramFile).mkString("\n"))
          if (current_params != matrixParams) {
            Outputter.fatal(s"Parameters found in ${densifier.paramFile}: ${pretty(render(current_params))}")
            Outputter.fatal(s"Parameters specified in spec file: ${pretty(render(matrixParams))}")
            Outputter.fatal(s"Difference: ${current_params.diff(matrixParams)}")
            throw new IllegalStateException("Denser matrix parameters don't match!")
          }
        }
    })
  }

  def createSplitIfNecessary(params: JValue) {
    var split_name = ""
    var params_specified = false
    // First, is this just a path, or do the params specify a split name?  If it's a path, we'll
    // just use the path as is.  Otherwise, we have some processing to do.
    params match {
      case JString(path) if (path.startsWith("/")) => {
        if (!fileUtil.fileExists(path)) {
          throw new IllegalStateException("Specified path to split does not exist!")
        }
      }
      case JString(name) => split_name = name
      case jval => {
        split_name = (jval \ "name").extract[String]
        params_specified = true
      }
    }
    if (split_name != "") {
      // Here we need to see if the split has already been created, and (if so) whether the split
      // as specified matches what's already been created.
      val split_dir = s"${praBase}splits/${split_name}/"
      val in_progress_file = SplitCreator.inProgressFile(split_dir)
      val param_file = SplitCreator.paramFile(split_dir)
      if (fileUtil.fileExists(split_dir)) {
        Outputter.info(s"Split found in ${split_dir}")
        fileUtil.blockOnFileDeletion(in_progress_file)
        if (fileUtil.fileExists(param_file)) {
          val current_params = parse(fileUtil.readLinesFromFile(param_file).mkString("\n"))
          if (params_specified == true && current_params != params) {
            Outputter.fatal(s"Parameters found in ${param_file}: ${pretty(render(current_params))}")
            Outputter.fatal(s"Parameters specified in spec file: ${pretty(render(params))}")
            Outputter.fatal(s"Difference: ${current_params.diff(params)}")
            throw new IllegalStateException("Split parameters don't match!")
          }
        }
      } else {
        Outputter.info(s"Split not found at ${split_dir}; creating it...")
        val creator = new SplitCreator(params, praBase, split_dir, fileUtil)
        creator.createSplit()
      }
    }
  }

  def getGraphDirectory(params: JValue): String = {
    (params \ "graph") match {
      case JNothing => null
      case JString(path) if (path.startsWith("/")) => path
      case JString(name) => praBase + "/graphs/" + name + "/"
      case jval => praBase + "/graphs/" + (jval \ "name").extract[String] + "/"
    }
  }
}
