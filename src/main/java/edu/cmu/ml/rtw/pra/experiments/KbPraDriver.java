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


    public static void main(String[] args)
            throws IOException, InterruptedException {
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
        // This directory must contain three things:
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

    public static void runPra(CommandLine cmdLine) throws IOException, InterruptedException {
        String outputBase = cmdLine.getOptionValue("outdir");
        if (!outputBase.endsWith("/")) outputBase += "/";

        String kbDirectory = cmdLine.getOptionValue("kb-files");
        if (!kbDirectory.endsWith("/")) kbDirectory += "/";

        String graphDirectory = cmdLine.getOptionValue("graph-files");
        if (!graphDirectory.endsWith("/")) graphDirectory += "/";

        String splitsDirectory = cmdLine.getOptionValue("split");
        if (!splitsDirectory.endsWith("/")) splitsDirectory += "/";

        String parameterFile = cmdLine.getOptionValue("param-file");

        runPra(kbDirectory, graphDirectory, splitsDirectory, paramterFile, outputBase);
    }

    public static void runPra(String kbDirectory,
                              String graphDirectory,
                              String splitsDirectory,
                              String paramterFile,
                              String outputBase) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        PraConfig.Builder baseBuilder = new PraConfig.Builder();

        if (new File(outputBase).exists()) {
            throw new RuntimeException("Output directory already exists!  Exiting...");
        }
        new File(outputBase).mkdirs();

        parseGraphFiles(graphDirectory, baseBuilder);

        FileWriter writer = new FileWriter(outputBase + "settings.txt");
        writer.write("KB used: " + kbDirectory + "\n");
        writer.write("Graph used: " + graphDirectory + "\n");
        writer.write("Splits used: " + splitsDirectory + "\n");
        writer.write("Parameter file used: " + parameterFile + "\n");
        writer.write("Parameters:\n");
        parseParameterFile(new BufferedReader(new FileReader(parameterFile)), baseBuilder, writer);
        writer.write("End of parameters\n");
        writer.close();

        PraConfig baseConfig = baseBuilder.build();
        // Make sure the graph is sharded
        PraDriver.processGraph(baseConfig.graph, baseConfig.numShards);

        Map<String, String> nodeNames = null;
        if (new File(kbDirectory + "node_names.tsv").exists()) {
            nodeNames = FileUtil.readMapFromTsvFile(kbDirectory + "node_names.tsv", true);
        }
        String relationsFile = splitsDirectory + "relations_to_run.tsv";
        String line;
        BufferedReader reader = new BufferedReader(new FileReader(relationsFile));
        OutputTranslator translator = new OutputTranslator(baseConfig.nodeDict,
                                                           baseConfig.edgeDict,
                                                           nodeNames);
        while ((line = reader.readLine()) != null) {
            PraConfig.Builder builder = new PraConfig.Builder(baseConfig);
            String relation = line;
            logger.info("\n\n\n\nRunning PRA for relation " + relation);
            boolean doCrossValidation = false;
            parseKbFiles(kbDirectory, relation, builder);

            String outdir = outputBase + relation + "/";
            new File(outdir).mkdirs();
            builder.setOutputBase(outdir);

            initializeSplit(splitsDirectory,
                            kbDirectory,
                            relation,
                            builder,
                            new DatasetFactory(),
                            new FileUtil());

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
            translator.translatePathCountsFile(outdir + "found_path_counts.tsv");
            translator.translatePathFile(outdir + "kept_paths.tsv");
            translator.translateWeightFile(outdir + "weights.tsv");
            translator.translateMatrixFile(outdir + "matrix.tsv",
                                           outdir + "kept_paths.tsv.translated",
                                           null);
            translator.translateMatrixFile(outdir + "positive_matrix.tsv",
                                           outdir + "kept_paths.tsv.translated",
                                           null);
            translator.translateMatrixFile(outdir + "negative_matrix.tsv",
                                           outdir + "kept_paths.tsv.translated",
                                           null);
            translator.translateMatrixFile(outdir + "unseen_matrix.tsv",
                                           outdir + "kept_paths.tsv.translated",
                                           null);
            translator.translateMatrixFile(outdir + "test_matrix.tsv",
                                           outdir + "kept_paths.tsv.translated",
                                           outdir + "weights.tsv.translated");
            translator.translateScoreFile(outdir + "scores.tsv");
            // TODO(matt): analyze the scores here and output a result to the command line.
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
            BufferedReader reader = new BufferedReader(new FileReader(percent_training_file));
            builder.setPercentTraining(Double.parseDouble(reader.readLine()));
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

    public static void parseParameterFile(BufferedReader reader,
                                          PraConfig.Builder builder,
                                          FileWriter writer) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            writer.write(line + "\n");
            String[] fields = line.split("\t");
            String parameter = fields[0];
            String value = fields[1];
            if (parameter.equalsIgnoreCase("L1 weight")) {
                builder.setL1Weight(Double.parseDouble(value));
            } else if (parameter.equalsIgnoreCase("L2 weight")) {
                builder.setL2Weight(Double.parseDouble(value));
            } else if (parameter.equalsIgnoreCase("walks per source")) {
                builder.setWalksPerSource(Integer.parseInt(value));
            } else if (parameter.equalsIgnoreCase("walks per path")) {
                builder.setWalksPerPath(Integer.parseInt(value));
            } else if (parameter.equalsIgnoreCase("path finding iterations")) {
                builder.setNumIters(Integer.parseInt(value));
            } else if (parameter.equalsIgnoreCase("number of paths to keep")) {
                builder.setNumPaths(Integer.parseInt(value));
            } else if (parameter.equalsIgnoreCase("only explicit negative evidence")) {
                builder.onlyExplicitNegatives();
            } else if (parameter.equalsIgnoreCase("matrix accept policy")) {
                builder.setAcceptPolicy(MatrixRowPolicy.parseFromString(value));
            } else if (parameter.equalsIgnoreCase("path accept policy")) {
                builder.setPathTypePolicy(PathTypePolicy.parseFromString(value));
            } else if (parameter.equalsIgnoreCase("path type embeddings")) {
                initializeVectorPathTypeFactory(builder, value);
            } else if (parameter.equalsIgnoreCase("path type selector")) {
                initializePathTypeSelector(builder, value);
            } else {
                throw new RuntimeException("Unrecognized parameter specification: " + line);
            }
        }
    }

    public static void initializeVectorPathTypeFactory(PraConfig.Builder builder,
                                                       String paramString) throws IOException {
        System.out.println("Initializing vector path type factory");
        String[] params = paramString.split(",");
        double spikiness = Double.parseDouble(params[0]);
        double resetWeight = Double.parseDouble(params[1]);
        Map<Integer, Vector> embeddings = Maps.newHashMap();
        for (int embeddingsIndex = 2; embeddingsIndex < params.length; embeddingsIndex++) {
            String embeddingsFile = params[embeddingsIndex];
            System.out.println("Embeddings file: " + embeddingsFile);
            BufferedReader reader = new BufferedReader(new FileReader(embeddingsFile));
            String line;
            // Embeddings files are formated as tsv, where the first column is the relation name
            // and the rest of the columns make up the vector.
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                int relationIndex = builder.edgeDict.getIndex(fields[0]);
                double[] vector = new double[fields.length - 1];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = Double.parseDouble(fields[i + 1]);
                }
                embeddings.put(relationIndex, new Vector(vector));
            }
        }
        builder.setPathTypeFactory(new VectorPathTypeFactory(builder.edgeDict,
                                                             embeddings,
                                                             spikiness,
                                                             resetWeight));
    }

    public static void initializePathTypeSelector(PraConfig.Builder builder,
                                                  String paramString) {
        if (paramString.startsWith("VectorClusteringPathTypeSelector")) {
            System.out.println("Using VectorClusteringPathTypeSelector");
            String[] params = paramString.split(",");
            double similarityThreshold = Double.parseDouble(params[1]);
            PathTypeSelector selector = new VectorClusteringPathTypeSelector(
                    (VectorPathTypeFactory) builder.pathTypeFactory,
                    similarityThreshold);
            builder.setPathTypeSelector(selector);
        } else {
            throw new RuntimeException("Unrecognized path type selector parameter!");
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
    public static void parseKbFiles(String directory,
                                    String relation,
                                    PraConfig.Builder builder) throws IOException {
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

        Map<String, String> ranges = FileUtil.readMapFromTsvFile(directory + "ranges.tsv");
        String range = ranges.get(relation);
        String fixed = range.replace("/", "_");
        String cat_file = directory + "category_instances/" + fixed;

        Set<Integer> allowedTargets = FileUtil.readIntegerSetFromFile(cat_file, builder.nodeDict);
        builder.setAllowedTargets(allowedTargets);
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
