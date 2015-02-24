package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.graphchi.ChiLogger;
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.config.SpecFileReader

import scala.collection.JavaConversions._

import org.json4s.{DefaultFormats,JValue}
import org.json4s.native.JsonMethods.{pretty,render}

class Driver(praBase: String, fileUtil: FileUtil = new FileUtil()) {
  private val logger = ChiLogger.getLogger("pra-driver");

  implicit val formats = DefaultFormats
  def runPra(_outputBase: String, params: JValue) {
    val outputBase = fileUtil.addDirectorySeparatorIfNecessary(_outputBase)
    val kbDirectory = fileUtil.addDirectorySeparatorIfNecessary((params \ "kb files").extract[String])
    val splitsDirectory = fileUtil.addDirectorySeparatorIfNecessary((params \ "split").extract[String])

    fileUtil.mkdirOrDie(outputBase);
    val start_time = System.currentTimeMillis

    val baseBuilder = new PraConfig.Builder();
    var writer = fileUtil.getFileWriter(outputBase + "settings.txt");
    writer.write("Parameters used:\n")
    writer.write(pretty(render(params)))
    writer.write("\n")
    writer.close()

    // This takes care of setting everything in the config builder that is consistent across
    // relations.
    new SpecFileReader(praBase, fileUtil).setPraConfigFromParams(params, baseBuilder)

    var nodeNames: java.util.Map[String, String] = null;
    if (fileUtil.fileExists(kbDirectory + "node_names.tsv")) {
      nodeNames = fileUtil.readMapFromTsvFile(kbDirectory + "node_names.tsv", true);
    }
    baseBuilder.setOutputter(new Outputter(baseBuilder.nodeDict, baseBuilder.edgeDict, nodeNames));

    val baseConfig = baseBuilder.noChecks().build();

    val relationsFile = splitsDirectory + "relations_to_run.tsv";
    for (relation <- fileUtil.readLinesFromFile(relationsFile)) {
      val relation_start = System.currentTimeMillis
      val builder = new PraConfig.Builder(baseConfig);
      logger.info("\n\n\n\nRunning PRA for relation " + relation);
      new KbPraDriver().parseKbFiles(kbDirectory, relation, builder, outputBase, fileUtil);

      val outdir = fileUtil.addDirectorySeparatorIfNecessary(outputBase + relation);
      fileUtil.mkdirs(outdir);
      builder.setOutputBase(outdir);

      val doCrossValidation = new KbPraDriver().initializeSplit(
        splitsDirectory,
        kbDirectory,
        relation,
        builder,
        new DatasetFactory(),
        fileUtil);

      val config = builder.build();

      // Run PRA
      if (doCrossValidation) {
        new PraTrainAndTester().crossValidate(config);
      } else {
        new PraTrainAndTester().trainAndTest(config);
      }
      val relation_end = System.currentTimeMillis
      val millis = relation_end - relation_start;
      var seconds = (millis / 1000).toInt;
      val minutes = seconds / 60;
      seconds = seconds - minutes * 60;
      writer = fileUtil.getFileWriter(outputBase + "settings.txt", true);  // true -> append to the file.
      writer.write(s"Time for relation $relation: $minutes minutes and $seconds seconds\n");
      writer.close()
    }
    val end_time = System.currentTimeMillis
    val millis = end_time - start_time;
    var seconds = (millis / 1000).toInt;
    val minutes = seconds / 60;
    seconds = seconds - minutes * 60;
    writer = fileUtil.getFileWriter(outputBase + "settings.txt", true);  // true -> append to the file.
    writer.write("PRA appears to have finished all relations successfully\n");
    writer.write(s"Total time: $minutes minutes and $seconds seconds\n");
    writer.close()
    System.out.println(s"Took $minutes minutes and $seconds seconds");
  }
}
