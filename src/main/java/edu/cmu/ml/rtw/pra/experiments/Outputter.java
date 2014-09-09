package edu.cmu.ml.rtw.pra.experiments;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.features.FeatureMatrix;
import edu.cmu.ml.rtw.pra.features.MatrixRow;
import edu.cmu.ml.rtw.pra.features.PathType;
import edu.cmu.ml.rtw.users.matt.util.CollectionsUtil;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.MapUtil;
import edu.cmu.ml.rtw.util.Pair;
import edu.cmu.ml.rtw.util.PairComparator;

/**
 * Handles outputting results and other information from running PRA to the file system.  When
 * initialized with node and edge dictionaries, this will output human-readable information.  If
 * node and edge dictionaries are not available, this will fall back on outputting information
 * using the integers used internally by GraphChi (i.e., not very useful).  So we _really_
 * recommend using node and edge dicts with this class.
 *
 * @author mgardner
 *
 */
public class Outputter {
  private final Dictionary nodeDict;
  private final Dictionary edgeDict;
  private final Map<String, String> nodeNames;

  public Outputter() {
    this(null, null, null);
    System.out.println("\n\n\n*************************************************\n\n\n");
    System.out.println("USING OUTPUTTER WITHOUT NODE AND EDGE DICTIONARIES");
    System.out.println("ARE YOU _SURE_ YOU WANT TO DO THIS?");
    System.out.println("\n\n\n*************************************************\n\n\n");
  }

  public Outputter(Dictionary nodeDict, Dictionary edgeDict) {
    this(nodeDict, edgeDict, null);
  }

  public Outputter(Dictionary nodeDict, Dictionary edgeDict, Map<String, String> nodeNames) {
    this.nodeDict = nodeDict;
    this.edgeDict = edgeDict;
    this.nodeNames = nodeNames;
  }

  private String getNode(int index) {
    if (nodeDict == null) return "" + index;
    String node = nodeDict.getString(index);
    return MapUtil.getWithDefaultAllowNullMap(nodeNames, node, node);
  }

  private String getPathType(PathType pathType) {
    if (edgeDict == null) return pathType.encodeAsString();
    return pathType.encodeAsHumanReadableString(edgeDict);
  }

  /**
   * Output a file containing the supplied scores.
   *
   * We take as input all three source maps (found in the config object), in addition to the source
   * scores, because they all have useful information for this.
   *
   * @param filename Place to write the output file
   * @param sourceScores The set of scores for each of the sources
   * @param config We use this to get to the training and testing data, so we know which sources
   *     score and how well we did on them.
   */
  public void outputScores(String filename,
                           Map<Integer, List<Pair<Integer, Double>>> sourceScores,
                           PraConfig config) {
    // TODO
    // These first few lines are for finding out if our prediction was _correct_ or not.
    Map<Integer, Set<Integer>> trainingSourcesMap = config.trainingData.getPositiveSourceMap();
    Map<Integer, Set<Integer>> testingSourcesMap = config.testingData.getPositiveSourceMap();
    Map<Integer, Set<Integer>> allPositiveSourcesMap =
        CollectionsUtil.combineMapSets(trainingSourcesMap, testingSourcesMap);
    Set<Integer> positiveTestSources = testingSourcesMap.keySet();

    // And this is to know which tuples to _score_ (which might include negative test
    // instances).
    Set<Integer> allTestSources = config.testingData.getCombinedSourceMap().keySet();
    try {
      FileWriter writer = new FileWriter(filename);
      for (int source : allTestSources) {
        String sourceStr = getNode(source);
        List<Pair<Integer, Double>> scores = sourceScores.get(source);
        if (scores == null) {
          writer.write(sourceStr + "\t\t\t");
          if (!positiveTestSources.contains(source)) {
            writer.write("-");
          }
          writer.write("\n\n");
          continue;
        }
        Collections.sort(scores,
                         new PairComparator<Integer, Double>(PairComparator.Side.NEGRIGHT));
        Set<Integer> targetSet = allPositiveSourcesMap.get(source);
        if (targetSet == null) {
          targetSet = new HashSet<Integer>();
        }
        Set<Integer> trainingTargetSet = trainingSourcesMap.get(source);
        if (trainingTargetSet == null) {
          trainingTargetSet = new HashSet<Integer>();
        }
        for (Pair<Integer, Double> pair : scores) {
          writer.write(sourceStr + "\t" + getNode(pair.getLeft()) + "\t" + pair.getRight() + "\t");
          if (targetSet.contains(pair.getLeft().intValue())) {
            writer.write("*");
          }
          if (trainingTargetSet.contains(pair.getLeft().intValue())) {
            writer.write("^");
          }
          if (!positiveTestSources.contains(source)) {
            writer.write("-");
          }
          writer.write("\n");
        }
        writer.write("\n");
      }
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void outputWeights(String filename,
                            List<Double> weights,
                            List<PathType> pathTypes) {
    try {
      FileWriter writer = new FileWriter(filename);
      List<Pair<PathType, Double>> zipped = CollectionsUtil.zipLists(pathTypes, weights);
      Collections.sort(zipped, new PairComparator(PairComparator.Side.NEGRIGHT));
      for (Pair<PathType, Double> pair : zipped) {
        writer.write(getPathType(pair.getLeft()) + "\t" + pair.getRight() + "\n");
      }
      writer.close();
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void outputSplitFiles(String outputBase, Dataset trainingData, Dataset testingData) {
    if (outputBase == null) return;
    try {
      FileWriter writer = new FileWriter(outputBase
                                         + "training_positive_examples.tsv");
      for (Pair<Integer, Integer> pair : trainingData.getPositiveInstances()) {
        writer.write(getNode(pair.getLeft()) + "\t" + getNode(pair.getRight()) + "\n");
      }
      writer.close();
      if (trainingData.getNegativeInstances() != null) {
        writer = new FileWriter(outputBase + "training_negative_examples.tsv");
        for (Pair<Integer, Integer> pair : trainingData.getNegativeInstances()) {
          writer.write(getNode(pair.getLeft()) + "\t" + getNode(pair.getRight()) + "\n");
        }
        writer.close();
      }
      writer = new FileWriter(outputBase + "testing_positive_examples.tsv");
      for (Pair<Integer, Integer> pair : testingData.getPositiveInstances()) {
        writer.write(getNode(pair.getLeft()) + "\t" + getNode(pair.getRight()) + "\n");
      }
      writer.close();
      if (testingData.getNegativeInstances() != null) {
        writer = new FileWriter(outputBase + "testing_negative_examples.tsv");
        for (Pair<Integer, Integer> pair : testingData.getNegativeInstances()) {
          writer.write(getNode(pair.getLeft()) + "\t" + getNode(pair.getRight()) + "\n");
        }
        writer.close();
      }
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void outputPathCounts(String baseDir,
                               String filename,
                               Map<PathType, Integer> pathCounts) {
    if (baseDir == null) return;
    try {
      FileWriter writer = new FileWriter(baseDir + filename);
      List<Map.Entry<PathType, Integer>> list = MapUtil.sortByValue(pathCounts, true);
      for (Map.Entry<PathType, Integer> entry : list) {
        writer.write(getPathType(entry.getKey()) + "\t" + entry.getValue() + "\n");
      }
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void outputPathCountMap(
      String baseDir,
      String filename,
      Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCountMap,
      Dataset data) {
    if (baseDir == null) return;
    try {
      FileWriter writer = new FileWriter(baseDir + filename);
      for (Pair<Integer, Integer> pair : data.getPositiveInstances()) {
        outputPathCountPair(pair, true, pathCountMap.get(pair), writer);
      }
      for (Pair<Integer, Integer> pair : data.getNegativeInstances()) {
        outputPathCountPair(pair, false, pathCountMap.get(pair), writer);
      }
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void outputPathCountPair(Pair<Integer, Integer> pair,
                                   boolean isPositive,
                                   Map<PathType, Integer> pathCounts,
                                   FileWriter writer) throws IOException {
    writer.write(getNode(pair.getLeft()) + "\t" + getNode(pair.getRight()) + "\t");
    if (isPositive) {
      writer.write("+\n");
    } else {
      writer.write("-\n");
    }
    if (pathCounts == null) {
      writer.write("\n");
      return;
    }
    List<Map.Entry<PathType, Integer>> list = MapUtil.sortByValue(pathCounts, true);
    for (Map.Entry<PathType, Integer> entry : list) {
      writer.write("\t" + getPathType(entry.getKey()) + "\t" + entry.getValue() + "\n");
    }
    writer.write("\n");
  }

  public void outputPaths(String baseDir, String filename, List<PathType> pathTypes) {
    if (baseDir == null) return;
    try {
      FileWriter writer = new FileWriter(baseDir + filename);
      for (PathType pathType : pathTypes) {
        writer.write(getPathType(pathType) + "\n");
      }
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void outputFeatureMatrix(String filename, FeatureMatrix matrix, List<PathType> pathTypes) {
    try {
      outputFeatureMatrix(new FileWriter(filename), matrix, pathTypes);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // This is a kind of complicated format, that maps node pairs to (feature, value) pairs.
  public void outputFeatureMatrix(FileWriter writer, FeatureMatrix matrix, List<PathType> pathTypes) {
    try {
      for (MatrixRow row : matrix.getRows()) {
        writer.write(getNode(row.sourceNode) + "," + getNode(row.targetNode) + "\t");
        for (int i=0; i<row.columns; i++) {
          String pathType = getPathType(pathTypes.get(row.pathTypes[i]));
          writer.write(pathType + "," + row.values[i] + " -#- ");
        }
        writer.write("\n");
      }
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
