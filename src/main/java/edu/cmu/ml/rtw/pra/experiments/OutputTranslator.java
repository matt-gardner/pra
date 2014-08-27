package edu.cmu.ml.rtw.pra.experiments;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.MapUtil;
import edu.cmu.ml.rtw.util.Pair;
import edu.cmu.ml.rtw.util.PairComparator;

/**
 * An object that converts the output of the main PRA code to something more human-digestible.
 *
 * The PRA code just works with integers, for node names and edge types.  This object takes a
 * couple of dictionaries and converts those integers into the strings they represent.  Optionally,
 * you can also supply a map from node names to new node names, if the node names in your
 * dictionary themselves aren't very useful (as might be the case with Freebase MIDs, for instance,
 * depending on how you constructed the graph that got input to PRA).
 */
public class OutputTranslator {
    private final Dictionary nodeDict;
    private final Dictionary edgeDict;
    private final Map<String, String> nodeNames;

    public OutputTranslator(Dictionary nodeDict, Dictionary edgeDict) {
        this(nodeDict, edgeDict, null);
    }

    public OutputTranslator(Dictionary nodeDict,
                            Dictionary edgeDict,
                            Map<String, String> nodeNames) {
        this.nodeDict = nodeDict;
        this.edgeDict = edgeDict;
        this.nodeNames = nodeNames;
    }

    //////////////////////////////////////////////
    // Example file: just a list of node pairs.
    //////////////////////////////////////////////

    public void translateExampleFile(String exampleFilename) throws IOException {
        translateExampleFile(new BufferedReader(new FileReader(exampleFilename)),
                             new FileWriter(exampleFilename + ".translated"));
    }

    @VisibleForTesting
    protected void translateExampleFile(BufferedReader reader,
                                     FileWriter writer) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineFields = line.split("\t");
            String source = lineFields[0];
            String target = lineFields[1];
            String sourceNode = nodeDict.getString(Integer.parseInt(source));
            String targetNode = nodeDict.getString(Integer.parseInt(target));
            sourceNode = MapUtil.getWithDefaultAllowNullMap(nodeNames, sourceNode, sourceNode);
            targetNode = MapUtil.getWithDefaultAllowNullMap(nodeNames, targetNode, targetNode);
            writer.write(sourceNode + "\t" + targetNode + "\n");
        }
        writer.close();
        reader.close();
    }

    //////////////////////////////////////////////
    // Matrix file:
    //////////////////////////////////////////////

    // This is a kind of complicated format, that maps node pairs to (feature, value) pairs.

    public void translateMatrixFile(String matrixFilename,
                                    String pathFilename,
                                    String weightFilename) throws IOException {
        Set<String> pathsToRemove = readPathsToRemove(weightFilename);
        List<String> paths = readPathList(pathFilename, pathsToRemove);
        translateMatrixFile(new BufferedReader(new FileReader(matrixFilename)),
                            new FileWriter(matrixFilename + ".translated"),
                            paths);
    }

    @VisibleForTesting
    protected void translateMatrixFile(BufferedReader reader,
                                       FileWriter writer,
                                       List<String> paths) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineFields = line.split("\t");
            String pair = lineFields[0];
            String pathPairs = lineFields[1];
            String[] pairFields = pair.split(",");
            int sourceId = Integer.parseInt(pairFields[0]);
            String sourceNode = nodeDict.getString(sourceId);
            sourceNode = MapUtil.getWithDefaultAllowNullMap(nodeNames, sourceNode, sourceNode);
            int targetId = Integer.parseInt(pairFields[1]);
            String targetNode = nodeDict.getString(targetId);
            targetNode = MapUtil.getWithDefaultAllowNullMap(nodeNames, targetNode, targetNode);
            String[] pathPairFields = pathPairs.split(" -#- ");
            for (int i = 0; i < pathPairFields.length; i++) {
                String[] fields = pathPairFields[i].split(",");
                int pathId = Integer.parseInt(fields[0]);
                String translated = "";
                translated += sourceNode + "\t";
                translated += targetNode + "\t";
                translated += paths.get(pathId) + "\t";
                translated += fields[1] + "\n";
                writer.write(translated);
            }
            writer.write("\n");
        }
        writer.close();
        reader.close();
    }

    private Set<String> readPathsToRemove(String weightFilename) throws IOException {
        Set<String> pathsToRemove = Sets.newHashSet();
        if (weightFilename != null) {
            BufferedReader reader = new BufferedReader(new FileReader(weightFilename));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                String pathDescription = parts[0];
                if (parts[1].equals("0.0")) {
                    pathsToRemove.add(pathDescription);
                }
            }
            reader.close();
        }
        return pathsToRemove;
    }

    private List<String> readPathList(String pathFilename,
                                      Set<String> pathsToRemove) throws IOException {
        List<String> paths = new ArrayList<String>();
        String line;
        BufferedReader reader = new BufferedReader(new FileReader(pathFilename));
        while ((line = reader.readLine()) != null) {
            String path = line.split("\t")[0];
            if (pathsToRemove.contains(path)) continue;
            paths.add(path);
        }
        reader.close();
        return paths;
    }

    //////////////////////////////////////////////
    // Weight file: maps paths to floats
    //////////////////////////////////////////////

    public void translateWeightFile(String weightFilename) throws IOException {
        translateWeightFile(new BufferedReader(new FileReader(weightFilename)),
                            new FileWriter(weightFilename + ".translated"));
    }

    @VisibleForTesting
    protected void translateWeightFile(BufferedReader reader,
                                       FileWriter writer) throws IOException {
        List<Pair<Double, String>> weights = new ArrayList<Pair<Double, String>>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineFields = line.split("\t");
            String path = lineFields[0];
            double weight = Double.parseDouble(lineFields[1]);
            String translatedPath = translatePath(path);
            weights.add(new Pair<Double, String>(weight, translatedPath));
        }
        reader.close();
        Collections.sort(weights, new PairComparator<Double, String>(PairComparator.Side.NEGLEFT));
        for (Pair<Double, String> weightPath : weights) {
            writer.write(weightPath.getRight() + "\t" + weightPath.getLeft() + "\n");
        }
        writer.close();
    }

    //////////////////////////////////////////////
    // Path counts file: maps paths to counts
    //////////////////////////////////////////////

    public void translatePathCountsFile(String pathFilename) throws IOException {
        translatePathCountsFile(new BufferedReader(new FileReader(pathFilename)),
                          new FileWriter(pathFilename + ".translated"));
    }

    @VisibleForTesting
    protected void translatePathCountsFile(BufferedReader reader, FileWriter writer)
            throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineFields = line.split("\t");
            String path = lineFields[0];
            String count = lineFields[1];
            String translatedPath = translatePath(path);
            writer.write(translatedPath + "\t" + count + "\n");
        }
        writer.close();
        reader.close();
    }

    //////////////////////////////////////////////
    // Path file: just a list of path types
    //////////////////////////////////////////////

    public void translatePathFile(String pathFilename) throws IOException {
        translatePathFile(new BufferedReader(new FileReader(pathFilename)),
                          new FileWriter(pathFilename + ".translated"));
    }

    @VisibleForTesting
    protected void translatePathFile(BufferedReader reader, FileWriter writer)
            throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String path = line;
            String translatedPath = translatePath(path);
            writer.write(translatedPath + "\n");
        }
        writer.close();
        reader.close();
    }

    //////////////////////////////////////////////
    // Score file: kind of complicated file format
    //////////////////////////////////////////////

    public void translateScoreFile(String scoreFilename) throws IOException {
        translateScoreFile(new BufferedReader(new FileReader(scoreFilename)),
                           new FileWriter(scoreFilename + ".translated"));
    }

    @VisibleForTesting
    protected void translateScoreFile(BufferedReader reader,
                                      FileWriter writer) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals("")) {
                writer.write("\n");
                continue;
            }
            String[] lineFields = line.split("\t");
            String source = lineFields[0];
            String sourceString = nodeDict.getString(Integer.parseInt(source));
            sourceString = MapUtil.getWithDefaultAllowNullMap(
                    nodeNames, sourceString, sourceString);
            if (lineFields.length == 1) {
                writer.write(sourceString + "\t\t\t\n");
                continue;
            }
            String target = lineFields[1];
            String score = lineFields[2];
            String correct = "";
            if (lineFields.length == 4) {
                correct = lineFields[3];
            }
            String targetString = nodeDict.getString(Integer.parseInt(target));
            targetString = MapUtil.getWithDefaultAllowNullMap(
                    nodeNames, targetString, targetString);
            writer.write(sourceString + "\t" + targetString + "\t" + score
                         + "\t" + correct + "\n");
        }
        writer.close();
        reader.close();
    }

    public String translatePath(String path) {
        // path is formatted like -1-2-3-4-; doing split("-") would result in an empty string as
        // the first element, so we call substring(1) first.
        String[] pathFields = path.substring(1).split("-");
        String translatedPath = "-";
        for (int i = 0; i < pathFields.length; i++) {
            boolean reverse = false;
            if (pathFields[i].charAt(0) == '_') {
                reverse = true;
                pathFields[i] = pathFields[i].substring(1);
            }
            String edgeType = pathFields[i];
            int edgeId = Integer.parseInt(edgeType);
            String edge = edgeDict.getString(edgeId);
            if (reverse) {
                edge = "_" + edge;
            }
            translatedPath += edge + "-";
        }
        return translatedPath;
    }
}
