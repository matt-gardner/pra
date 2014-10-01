package edu.cmu.ml.rtw.pra.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.features.FeatureGenerator;
import edu.cmu.ml.rtw.pra.features.PathType;
import edu.cmu.ml.rtw.users.matt.util.Pair;

/**
 * A class for finding which paths exist between any pair of nodes.  This is the same as the first
 * step of PRA - explore the graph to find common paths between a set of (source, target) node
 * pairs.  But when running the PRA code we just keep an overall count.  This will instead show you
 * the paths that exist between each input pair.  At the moment, the point is just for some
 * exploratory analysis, so you can manually judge whether you think there are enough paths in the
 * graph to be able to make a correct prediction, for instance.
 */
public class PathExplorer {
  public static void main(String[] args) throws IOException, InterruptedException {
    Options cmdLineOptions = createOptionParser();
    CommandLine cmdLine = null;
    try {
      CommandLineParser parser = new PosixParser();
      cmdLine =  parser.parse(cmdLineOptions, args);
    } catch(ParseException e) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("KbPraDriver", cmdLineOptions);
      System.exit(-1);
    }
    exploreFeatures(cmdLine);
    // Somewhere in DrunkardMobEngine, the threads aren't exitting properly
    System.exit(0);
  }

  public static Options createOptionParser() {
    Options cmdLineOptions = new Options();

    // A graph directory, the same as seen in KbPraDriver.
    cmdLineOptions.addOption("g", "graph-files", true, "Graph files directory");

    // A tab-separated file containing at least two columns.  The first column should be the source
    // node (name, not id - we'll use the graph's node dictionary).  The second column should be
    // the target node.  All other columns will be ignored.  A training or testing file found in a
    // split directory is a natural input to this.
    cmdLineOptions.addOption("i", "input-file", true, "node pair file");

    // Parameters file: a tab-separated file that contains one option per line.  See the
    // parameter parsing method for more details on what can and must be in the file.
    // We can read a KbPraDriver param file here, but we just use one parameter in it: walks per
    // source.
    cmdLineOptions.addOption("p", "param-file", true, "parameter file");

    // The place where we store the results of this exploration.
    cmdLineOptions.addOption("o", "out-dir", true, "output directory");
    return cmdLineOptions;
  }

  public static void exploreFeatures(CommandLine cmdLine) throws IOException, InterruptedException {
    String outputBase = cmdLine.getOptionValue("out-dir");
    String graphDirectory = cmdLine.getOptionValue("graph-files");
    String inputFile = cmdLine.getOptionValue("input-file");
    String parameterFile = cmdLine.getOptionValue("param-file");
    exploreFeatures(graphDirectory, inputFile, parameterFile, outputBase);
  }

  public static void exploreFeatures(String graphDirectory,
                                     String inputFile,
                                     String parameterFile,
                                     String outputBase) throws IOException, InterruptedException {
    if (!graphDirectory.endsWith("/")) graphDirectory += "/";
    PraConfig.Builder builder = new PraConfig.Builder();
    builder.setFromParamFile(new BufferedReader(new FileReader(parameterFile)));
    new File(outputBase).mkdirs();
    builder.setOutputBase(outputBase);
    DatasetFactory datasetFactory = new DatasetFactory();
    new KbPraDriver().parseGraphFiles(graphDirectory, builder);
    builder.setTrainingData(datasetFactory.fromFile(inputFile, builder.nodeDict));
    // TODO(matt): this will need to be fixed if we ever want to use this for something more than
    // exploratory analysis!
    builder.setUnallowedEdges(new ArrayList<Integer>());
    PraConfig config = builder.build();

    FeatureGenerator generator = new FeatureGenerator(config);
    Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCounts =
        generator.findConnectingPaths(config.trainingData);
  }

}
