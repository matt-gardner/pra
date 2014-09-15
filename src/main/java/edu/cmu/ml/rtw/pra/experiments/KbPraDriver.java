package edu.cmu.ml.rtw.pra.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import com.google.common.collect.Maps;

import edu.cmu.graphchi.ChiLogger;
import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy;
import edu.cmu.ml.rtw.pra.features.PathTypePolicy;
import edu.cmu.ml.rtw.pra.features.PathTypeSelector;
import edu.cmu.ml.rtw.pra.features.VectorClusteringPathTypeSelector;
import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.Vector;

/**
 * An interface to PraDriver for more easily running PRA experiments with a knowledge base.  The
 * PraDriver code is totally agnostic to what kind of graph is being used or what the relation and
 * node numbers actually refer to.  This gives an interface that can be used more easily if you are
 * using a KB graph like from Freebase or NELL (presumably created using the NellGraphCreator or
 * FreebaseGraphCreator for the KB files, then GraphCreator for the graph and dictionary files).
 *
 * @author Matt Gardner (mg1@cs.cmu.edu)
 */
public class KbPraDriver {
  private static final Logger logger = ChiLogger.getLogger("kb-pra-driver");

  public static void main(String[] args) throws IOException, InterruptedException {
    Options cmdLineOptions = createOptionParser();
    CommandLine cmdLine = null;
    try {
      CommandLineParser parser = new PosixParser();
      cmdLine =  parser.parse(cmdLineOptions, args);
    } catch(ParseException e) {
      printHelpAndExit("ParseException while processing arguments");
    }
    runPra(cmdLine);
    // Somewhere in DrunkardMobEngine, the threads aren't exitting properly
    System.exit(0);
  }

  public static Options createOptionParser() {
    Options cmdLineOptions = new Options();

    // KB files directory must have the following:
    //
    // A relations/ directory, with one file per relation containing known instances
    // A category_instances/ directory, with one file per category containin known instances
    // A domains.tsv file, with one entry per line: "[relation] \t [domain] \n"
    // A ranges.tsv file, with one entry per line: "[relation] \t [range] \n"
    // An inverses.tsv file, with one entry per line: "[relation] \t [inverse] \n"
    //
    // All of these files are in strings, not integers.  They will be translated using the node
    // and edge dictionaries found in the graph directory.
    cmdLineOptions.addOption("k", "kb-files", true, "KB files directory");

    // This directory must contain four things:
    //
    // node_dict.tsv: a Dictionary mapping node names to integers
    // edge_dict.tsv: a Dictionary mapping edge names to integers
    // graph_chi/edges.tsv: an edge file that will be input to GraphChi
    // num_shards.tsv: Contains a single number: the number of shards that should be in the
    // graph
    //
    // The edge file is in a separate directory because GraphChi will create a bunch of
    // auxiliary files in the same directory as edges.tsv.
    cmdLineOptions.addOption("g", "graph-files", true, "Graph files directory");

    // The directory containing information on which relations to run, and how to split the
    // data into train and test sets.  This directory must contain the following:
    //
    // relations_to_run.tsv: one relation per line - each relation will invoke PraDriver once.
    // [relation_name]/training.tsv: one relation instance per line, labeled as positive or
    // negative ("[source] \t [target] \t [label from {1,-1}] \n").
    // [relation_name]/testing.tsv: same as training.tsv
    //
    // The directories for each relation are optional - if no directory is found for a relation
    // in relations_to_run.tsv, we will just randomly split the known instances found in the
    // kb-files directory.
    cmdLineOptions.addOption("s", "split", true, "Split specification directory");

    // Parameters file: a tab-separated file that contains one option per line.  See the
    // parameter parsing method for more details on what can and must be in the file.
    cmdLineOptions.addOption("p", "param-file", true, "parameter file");

    // The directory where we store the results of this run.
    cmdLineOptions.addOption("o", "outdir", true, "base directory for output");
    return cmdLineOptions;
  }

  private static void printHelpAndExit() {
    printHelpAndExit(null);
  }

  private static void printHelpAndExit(String message) {
    if (message != null) System.out.println(message);
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("KbPraDriver", createOptionParser());
    System.exit(-1);
  }

  public static void runPra(CommandLine cmdLine) throws IOException, InterruptedException {
    String outputBase = cmdLine.getOptionValue("outdir");
    String kbDirectory = cmdLine.getOptionValue("kb-files");
    String graphDirectory = cmdLine.getOptionValue("graph-files");
    String splitsDirectory = cmdLine.getOptionValue("split");
    String parameterFile = cmdLine.getOptionValue("param-file");
    if (outputBase == null ||
        kbDirectory == null ||
        graphDirectory == null ||
        splitsDirectory == null ||
        parameterFile == null) {
      printHelpAndExit("One or more of the parameters was missing");
    }
    runPra(kbDirectory, graphDirectory, splitsDirectory, parameterFile, outputBase);
  }

  public static void runPra(String kbDirectory,
                            String graphDirectory,
                            String splitsDirectory,
                            String parameterFile,
                            String outputBase) throws IOException, InterruptedException {
    FileUtil fileUtil = new FileUtil();
    if (!outputBase.endsWith("/")) outputBase += "/";
    if (!kbDirectory.endsWith("/")) kbDirectory += "/";
    if (!graphDirectory.endsWith("/")) graphDirectory += "/";
    if (!splitsDirectory.endsWith("/")) splitsDirectory += "/";
    long start = System.currentTimeMillis();
    PraConfig.Builder baseBuilder = new PraConfig.Builder();
    baseBuilder.setFromParamFile(new BufferedReader(new FileReader(parameterFile)));

    if (new File(outputBase).exists()) {
      throw new RuntimeException("Output directory already exists!  Exiting...");
    }
    new File(outputBase).mkdirs();

    parseGraphFiles(graphDirectory, baseBuilder);

    Map<String, String> nodeNames = null;
    if (new File(kbDirectory + "node_names.tsv").exists()) {
      nodeNames = fileUtil.readMapFromTsvFile(kbDirectory + "node_names.tsv", true);
    }
    Outputter outputter = new Outputter(baseBuilder.nodeDict, baseBuilder.edgeDict, nodeNames);
    baseBuilder.setOutputter(outputter);

    FileWriter writer = new FileWriter(outputBase + "settings.txt");
    writer.write("KB used: " + kbDirectory + "\n");
    writer.write("Graph used: " + graphDirectory + "\n");
    writer.write("Splits used: " + splitsDirectory + "\n");
    writer.write("Parameter file used: " + parameterFile + "\n");
    writer.write("Parameters:\n");
    fileUtil.copyLines(new BufferedReader(new FileReader(parameterFile)), writer);
    writer.write("End of parameters\n");
    writer.close();

    PraConfig baseConfig = baseBuilder.build();
    // Make sure the graph is sharded
    PraDriver.processGraph(baseConfig.graph, baseConfig.numShards);

    String relationsFile = splitsDirectory + "relations_to_run.tsv";
    String line;
    BufferedReader reader = new BufferedReader(new FileReader(relationsFile));
    while ((line = reader.readLine()) != null) {
      PraConfig.Builder builder = new PraConfig.Builder(baseConfig);
      String relation = line;
      logger.info("\n\n\n\nRunning PRA for relation " + relation);
      boolean doCrossValidation = false;
      parseKbFiles(kbDirectory, relation, builder, outputBase);

      String outdir = outputBase + relation + "/";
      new File(outdir).mkdirs();
      builder.setOutputBase(outdir);

      initializeSplit(splitsDirectory,
                      kbDirectory,
                      relation,
                      builder,
                      new DatasetFactory(),
                      fileUtil);

      PraConfig config = builder.build();
      if (config.allData != null) {
        doCrossValidation = true;
      }

      // Run PRA
      if (doCrossValidation) {
        PraDriver.crossValidate(config);
      } else {
        PraDriver.trainAndTest(config);
      }
    }
    long end = System.currentTimeMillis();
    long millis = end - start;
    int seconds = (int) (millis / 1000);
    int minutes = seconds / 60;
    seconds = seconds - minutes * 60;
    writer = new FileWriter(outputBase + "settings.txt", true);  // true -> append to the file.
    writer.write("PRA appears to have finished all relations successfully\n");
    writer.write("Finished in " + minutes + " minutes and " + seconds + " seconds\n");
    System.out.println("Took " + minutes + " minutes and " + seconds + " seconds");
    writer.close();
  }

  /**
   * Reads from splitsDirectory and populates the data fields in builder.  Returns true if we
   * should be doing cross validation, false otherwise.
   */
  public static boolean initializeSplit(String splitsDirectory,
                                        String kbDirectory,
                                        String relation,
                                        PraConfig.Builder builder,
                                        DatasetFactory datasetFactory,
                                        FileUtil fileUtil) throws IOException {
    String fixed = relation.replace("/", "_");
    // We look in the splits directory for a fixed split; if we don't find one, we do cross
    // validation.
    if (fileUtil.fileExists(splitsDirectory + fixed)) {
      String training = splitsDirectory + fixed + "/training.tsv";
      String testing = splitsDirectory + fixed + "/testing.tsv";
      builder.setTrainingData(datasetFactory.fromFile(training, builder.nodeDict));
      builder.setTestingData(datasetFactory.fromFile(testing, builder.nodeDict));
      return false;
    } else {
      builder.setAllData(datasetFactory.fromFile(kbDirectory + "relations/" + fixed,
                                                 builder.nodeDict));
      String percent_training_file = splitsDirectory + "percent_training.tsv";
      builder.setPercentTraining(fileUtil.readDoubleListFromFile(percent_training_file).get(0));
      return true;
    }
  }

  public static void parseGraphFiles(String directory, PraConfig.Builder builder)
      throws IOException {
        if (!directory.endsWith("/")) directory += "/";
        builder.setGraph(directory + "graph_chi/edges.tsv");
        System.out.println("Loading node and edge dictionaries from graph directory: " + directory);
        BufferedReader reader = new BufferedReader(new FileReader(directory + "num_shards.tsv"));
        builder.setNumShards(Integer.parseInt(reader.readLine()));
        Dictionary nodeDict = new Dictionary();
        nodeDict.setFromFile(directory + "node_dict.tsv");
        builder.setNodeDictionary(nodeDict);
        Dictionary edgeDict = new Dictionary();
        edgeDict.setFromFile(directory + "edge_dict.tsv");
        builder.setEdgeDictionary(edgeDict);
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
  public static void parseKbFiles(String directory,
                                  String relation,
                                  PraConfig.Builder builder,
                                  String outputBase) throws IOException {
    // TODO(matt): allow this to be left unspecified.
    Map<Integer, Integer> inverses = createInverses(directory + "inverses.tsv", builder.edgeDict);
    builder.setRelationInverses(inverses);

    Map<String, List<String>> embeddings = null;
    if (new File(directory + "embeddings.tsv").exists()) {
      embeddings = FileUtil.readMapListFromTsvFile(directory + "embeddings.tsv");
    }
    List<Integer> unallowedEdges = createUnallowedEdges(relation,
                                                        inverses,
                                                        embeddings,
                                                        builder.edgeDict);
    builder.setUnallowedEdges(unallowedEdges);

    if (new File(directory + "ranges.tsv").exists()) {
      Map<String, String> ranges = FileUtil.readMapFromTsvFile(directory + "ranges.tsv");
      String range = ranges.get(relation);
      String fixed = range.replace("/", "_");
      String cat_file = directory + "category_instances/" + fixed;

      Set<Integer> allowedTargets = FileUtil.readIntegerSetFromFile(cat_file, builder.nodeDict);
      builder.setAllowedTargets(allowedTargets);
    } else {
      FileWriter writer = new FileWriter(outputBase + "settings.txt", true);  // true -> append
      writer.write("No range file found! I hope your accept policy is as you want it...\n");
      System.out.println("No range file found!");
      writer.close();
    }
  }

  public static List<Integer> createUnallowedEdges(String relation,
                                                   Map<Integer, Integer> inverses,
                                                   Map<String, List<String>> embeddings,
                                                   Dictionary edgeDict) {
    List<Integer> unallowedEdges = new ArrayList<Integer>();

    // The relation itself is an unallowed edge type.
    int relIndex = edgeDict.getIndex(relation);
    unallowedEdges.add(relIndex);

    // If the relation has an inverse, it's an unallowed edge type.
    Integer inverseIndex = inverses.get(relIndex);
    String inverse = null;
    if (inverseIndex != null) {
      unallowedEdges.add(inverseIndex);
      inverse = edgeDict.getString(inverseIndex);
    }

    // And if the relation has an embedding (really a set of cluster ids), those should be
    // added to the unallowed edge type list.
    if (embeddings != null) {
      List<String> relationEmbeddings = embeddings.get(relation);
      if (relationEmbeddings != null) {
        for (String embedded : embeddings.get(relation)) {
          unallowedEdges.add(edgeDict.getIndex(embedded));
        }
      }
      if (inverse != null) {
        List<String> inverseEmbeddings = embeddings.get(inverse);
        if (inverseEmbeddings != null) {
          for (String embedded : embeddings.get(inverse)) {
            unallowedEdges.add(edgeDict.getIndex(embedded));
          }
        }
      }
    }
    return unallowedEdges;
  }

  /**
   * Reads a file containing a mapping between relations and their inverses, and returns the
   * result as a map.
   */
  public static Map<Integer, Integer> createInverses(String filename,
                                                     Dictionary dict) throws IOException {
    Map<Integer, Integer> inverses = new HashMap<Integer, Integer>();
    BufferedReader reader = new BufferedReader(new FileReader(filename));
    String line;
    while ((line = reader.readLine()) != null) {
      String[] parts = line.split("\t");
      int rel1 = dict.getIndex(parts[0]);
      int rel2 = dict.getIndex(parts[1]);
      inverses.put(rel1, rel2);
      // Just for good measure, in case the file only lists each relation once.
      inverses.put(rel2, rel1);
    }
    reader.close();
    return inverses;
  }
}
