package edu.cmu.ml.rtw.pra.on_demand;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.pra.features.FeatureGenerator;
import edu.cmu.ml.rtw.pra.features.FeatureMatrix;
import edu.cmu.ml.rtw.pra.features.MatrixRow;
import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy;
import edu.cmu.ml.rtw.pra.features.PathType;
import edu.cmu.ml.rtw.pra.models.PraModel;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.Index;
import edu.cmu.ml.rtw.users.matt.util.Pair;
import edu.cmu.ml.rtw.users.matt.util.PairComparator;

/**
 * An object for loading and querying a trained PRA model.
 *
 * This class makes heavy use of methods in PRA driver, but it keeps the models as class variables,
 * so it can be queried for predictions repeatedly.
 *
 * @author Matt Gardner (mg1@cs.cmu.edu)
 */
public class OnlinePraPredictor {

  private final List<List<Pair<PathType, Double>>> models;
  private final List<String> relationNames;
  private final List<List<PathType>> pathTypes;
  private final List<List<Double>> weights;
  private final List<Set<Integer>> allowedTargets;
  private final Dictionary nodeDict;
  private final Dictionary edgeDict;
  private final PraConfig config;

  /**
   * Create and initialize a PRA model given a stored model file.
   *
   * @param modelFilename The location of a (path type, weight) file, preferably as written by
   *     outputWeights in PraDriver (calls to PraDriver.trainPraModel produce these files).
   * @param nodeDictionaryFilename Path to the mapping from strings to integers for the nodes in
   *     the graph.  This is generally saved alongside the graph file, and is necessary so that we
   *     can take queries as concept names and not indices.
   * @param edgeDictionaryFilename Path to the mapping from strings to integers for the edges in
   *     the graph.  This is generally saved alongside the graph file, and is necessary so that we
   *     can give human-interpretable provenance information for the predictions we come up with.
   * @param graphFilename Path to the graph file to use for the random walks
   * @param numShardsInGraph The number of shards that the graph is sharded into
   * @param walksPerPath The number of walks to start for each (source node, path) combination.
   * @param acceptPolicy Determines the set of acceptable rows in the feature matrix.  For OPRA
   *     this should almost certainly be MatrixRowPolicy.ALL_TARGETS, with a suitable set of
   *     allowedTargets (i.e., all known instances of a category).
   * @param allowedTargetsFile If not null, and in combination with the acceptPolicy, this
   *     specifies which nodes are allowed to be targets in the feature matrix.
   * @param outputBase If not null, a directory where we should save the output for later
   *     inspection.  If null, we do not save anything.  The main output that is saved here is a
   *     feature matrix for the predictions made.
   */
  public OnlinePraPredictor(String modelFilename,
                            String nodeDictionaryFilename, String edgeDictionaryFilename,
                            String graphFilename,
                            int numShardsInGraph,
                            int walksPerPath,
                            String allowedTargetsFile,
                            String pathTypeFactoryString,
                            String outputBase) {
    this(Arrays.asList(modelFilename),
         nodeDictionaryFilename,
         edgeDictionaryFilename,
         graphFilename,
         numShardsInGraph,
         walksPerPath,
         Arrays.asList(allowedTargetsFile),
         pathTypeFactoryString,
         outputBase);
  }

  // TODO(matt): add javadoc for this version (it's basically the same as above, but with more than
  // one relation).
  public OnlinePraPredictor(List<String> modelFilenames,
                            String nodeDictionaryFilename,
                            String edgeDictionaryFilename,
                            String graphFilename,
                            int numShardsInGraph,
                            int walksPerPath,
                            List<String> allowedTargetsFiles,
                            String pathTypeFactoryString,
                            String outputBase) {
    // allowedTargets is used to restrict the feature matrix that is returned.  Because we're doing
    // more than one relation at once, we can't reasonably specify allowedTargets.  So we set our
    // acceptPolicy to EVERYTHING, and leave allowedTargets unset, doing the target filtering
    // ourselves, down below.  unallowedEdges is more important during training time, and so we
    // omit it here.
    PraConfig.Builder builder = new PraConfig.Builder()
        .setGraph(graphFilename)
        .setNumShards(numShardsInGraph)
        .setWalksPerPath(walksPerPath)
        .setAcceptPolicy(MatrixRowPolicy.EVERYTHING)
        .setOutputBase(outputBase);
    if (pathTypeFactoryString != null) {
      try {
        builder.initializeVectorPathTypeFactory(pathTypeFactoryString);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    this.config = builder.build();
    models = new ArrayList<List<Pair<PathType, Double>>>();
    relationNames = new ArrayList<String>();
    pathTypes = new ArrayList<List<PathType>>();
    weights = new ArrayList<List<Double>>();
    allowedTargets = new ArrayList<Set<Integer>>();
    nodeDict = new Dictionary();
    nodeDict.setFromFile(new File(nodeDictionaryFilename));
    edgeDict = new Dictionary();
    edgeDict.setFromFile(new File(edgeDictionaryFilename));
    for (int i = 0; i < modelFilenames.size(); i++) {
      List<Pair<PathType, Double>> model;
      try {
        // TODO(matt): this is ugly and needs to be fixed.  I need to make PraModel better so that
        // I can just use it instead of the list of pairs that I have.
        PraModel praModel = new PraModel(config);
        model = praModel.readWeightsFromFile(modelFilenames.get(i));
        models.add(model);
        String[] parts = modelFilenames.get(i).split("/");
        String relationName = parts[parts.length-2];
        relationNames.add(relationName);
      } catch(IOException e) {
        throw new RuntimeException(e);
      }
      try {
        FileUtil fileUtil = new FileUtil();
        allowedTargets.add(fileUtil.readIntegerSetFromFile(allowedTargetsFiles.get(i), nodeDict));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      List<PathType> pathTypeList = new ArrayList<PathType>();
      pathTypes.add(pathTypeList);
      List<Double> weightList = new ArrayList<Double>();
      weights.add(weightList);
      for (int j=0; j<model.size(); j++) {
        pathTypeList.add(model.get(j).getLeft());
        weightList.add(model.get(j).getRight());
      }
    }
  }

  /**
   * Given a node name from the graph (most likely a NELL concept), run PRA and return a list of
   * predicted target nodes.  The list is sorted by confidence, but not thresholded in any way -
   * predictions with negative scores are returned.
   */
  public List<PraPrediction> predictTargets(String nodeName) {
    return predictTargets(nodeName, null);
  }

  /**
   * @param nodeName The name of the node to return predictions for.
   * @param relations The relations to restrict predictions to.  If this is null, we return
   *     predictions for all relations (which could be _incredibly_ slow).
   */
  public List<PraPrediction> predictTargets(String nodeName, List<String> relations) {
    Map<String, List<PraPrediction>> predictionMap = predictTargets(Arrays.asList(nodeName),
                                                                    relations);
    List<PraPrediction> predictions = predictionMap.get(nodeName);
    if (predictions == null) {
      predictions = new ArrayList<PraPrediction>();
    }
    return predictions;
  }

  public Map<String, List<PraPrediction>> predictTargets(List<String> nodeNames) {
    return predictTargets(nodeNames, null);
  }

  /**
   * Given a list of node name from the graph (most likely NELL concepts), run PRA and return a
   * mapping from input nodes to lists of predictions.  Like the above predictTargets, but a batch
   * method instead.  This should be more efficient for multiple queries, as it makes smarter use
   * of GraphChi.
   *
   * If one of the nodeNames is not in my node dictionary (i.e., for some reason you gave me the
   * name of a NELL concept that was not in the graph I was trained on), there will not be an entry
   * for it in the returned map.
   *
   * @param nodeNames The set of nodes to return predictions for.
   * @param relations The set of relations to restrict predictions to.  If this is null, we return
   *     predictions for all relations (which could be _incredibly_ slow).
   */
  public Map<String, List<PraPrediction>> predictTargets(List<String> nodeNames,
                                                         List<String> relations) {
    Map<String, List<PraPrediction>> predictionMap = new HashMap<String, List<PraPrediction>>();
    List<Integer> sourceNodes = new ArrayList<Integer>();
    for (String nodeName : nodeNames) {
      if (!nodeDict.hasString(nodeName)) {
        continue;
      }
      sourceNodes.add(nodeDict.getIndex(nodeName));
    }
    if (sourceNodes.size() == 0) {
      // No need to waste time starting up GraphChi if we have nothing to predict.
      return predictionMap;
    }
    Dataset dataset = new Dataset.Builder().setPositiveSources(sourceNodes).build();
    String matrixOutput = null;
    if (config.outputBase != null) {
      // TODO?: make this so it doesn't overwrite files?  Not sure if it's worth it
      matrixOutput = config.outputBase + "batch_prediction_matrix.tsv";
    }
    List<Integer> modelNums = new ArrayList<Integer>();
    if (relations == null) {
      for (int i = 0; i < models.size(); i++) {
        modelNums.add(i);
      }
    } else {
      for (String relation : relations) {
        int modelNum = relationNames.indexOf(relation);
        if (modelNum == -1) {
          System.out.println("NOTE: You requested predictions for a relation that isn't "
                             + "loaded: " + relation);
          continue;
        }
        modelNums.add(modelNum);
      }
    }
    List<PathType> mergedPathTypes = new ArrayList<PathType>();
    Index<PathType> pathDict = new Index<PathType>(config.pathTypeFactory);
    Map<Integer, Map<Integer, Integer>> pathTranslationMaps =
        createPathTranslationMaps(pathDict, modelNums);
    for (int i = 1; i < pathDict.getNextIndex(); i++) {
      mergedPathTypes.add(pathDict.getKey(i));
    }
    FeatureGenerator generator = new FeatureGenerator(config);
    FeatureMatrix featureMatrix = generator.computeFeatureValues(mergedPathTypes,
                                                                 dataset,
                                                                 matrixOutput);
    for (int m : modelNums) {
      for (MatrixRow row : featureMatrix.getRows()) {
        if (!allowedTargets.get(m).contains(row.targetNode)) {
          continue;
        }
        String sourceNode = nodeDict.getString(row.sourceNode);
        List<PraPrediction> predictions = predictionMap.get(sourceNode);
        if (predictions == null) {
          predictions = new ArrayList<PraPrediction>();
          predictionMap.put(sourceNode, predictions);
        }
        MatrixRow translatedRow = translateRow(row, pathTranslationMaps.get(m));
        if (translatedRow != null) {
          predictions.add(getPredictionForRow(translatedRow, m));
        }
      }
    }
    for (List<PraPrediction> predictions : predictionMap.values()) {
      Collections.sort(predictions);
    }
    return predictionMap;
  }

  /**
   * For various kinds of testing, depending on what I need to test.  Right now this is for
   * profiling PathFinder, so I'm just calling computeFeatureRows and ignoring the output.
   */
  public void testPredictTargets(List<String> nodeNames, List<String> relations) {
    List<Integer> sources = new ArrayList<Integer>();
    for (String nodeName : nodeNames) {
      if (!nodeDict.hasString(nodeName)) {
        continue;
      }
      sources.add(nodeDict.getIndex(nodeName));
    }
  }

  public PraPrediction getPredictionForRow(MatrixRow row, int modelNum) {
    // TODO(matt): again, this is ugly.  Improve PraModel so that it can handle this kind of usage.
    PraModel model = new PraModel(null);
    double score = model.classifyMatrixRow(row, weights.get(modelNum));
    String provenance = getProvenance(row, models.get(modelNum), edgeDict);
    String sourceNode = nodeDict.getString(row.sourceNode);
    String targetNode = nodeDict.getString(row.targetNode);
    return new PraPrediction(relationNames.get(modelNum),
                             sourceNode,
                             targetNode,
                             provenance,
                             score);
  }

  public Map<Integer, Map<Integer, Integer>> createPathTranslationMaps(Index<PathType> pathDict,
                                                                       List<Integer> modelNums) {
    Map<Integer, Map<Integer, Integer>> maps = new HashMap<Integer, Map<Integer, Integer>>();
    for (int m : modelNums) {
      List<PathType> pathTypeList = pathTypes.get(m);
      Map<Integer, Integer> map = new HashMap<Integer, Integer>();
      maps.put(m, map);
      for (int i = 0; i < pathTypeList.size(); i++) {
        // Path type lists are 0-indexed, while the Dictionary is 1-indexed, so we need to subtract
        // one.
        int translated = pathDict.getIndex(pathTypeList.get(i)) - 1;
        map.put(translated, i);
      }
    }
    return maps;
  }

  // TODO(matt): move these static methods to some kind of utility class, or something.
  public static MatrixRow translateRow(MatrixRow row, Map<Integer, Integer> pathTranslationMap) {
    List<Pair<Integer, Double>> features = new ArrayList<Pair<Integer, Double>>();
    for (int i=0; i < row.columns; i++) {
      Integer translatedType = pathTranslationMap.get(row.pathTypes[i]);
      if (translatedType == null) {
        continue;
      }
      features.add(new Pair<Integer, Double>(translatedType, row.values[i]));
    }
    if (features.size() == 0) {
      return null;
    }
    int[] pathTypes = new int[features.size()];
    double[] values = new double[features.size()];
    for (int i=0; i<features.size(); i++) {
      pathTypes[i] = features.get(i).getLeft();
      values[i] = features.get(i).getRight();
    }
    return new MatrixRow(row.sourceNode, row.targetNode, pathTypes, values);
  }

  public static String getProvenance(MatrixRow row, List<Pair<PathType, Double>> model,
                                     Dictionary edgeDict) {
    int featuresToShow = 10;
    String separator = "\t";
    List<Pair<String, Double>> provenanceStrings = new ArrayList<Pair<String, Double>>();
    for (int i=0; i<row.columns; i++) {
      Pair<PathType, Double> featureType = model.get(row.pathTypes[i]);
      PathType pathType = featureType.getLeft();
      String description = pathType.encodeAsHumanReadableString(edgeDict);
      double weight = featureType.getRight();
      double influence = row.values[i] * weight;
      String provenanceBit = String.format("%s\t%.3f (%.2f * %.2f)",
                                           description,
                                           influence,
                                           row.values[i],
                                           weight);
      provenanceStrings.add(new Pair<String, Double>(provenanceBit, Math.abs(influence)));
    }
    Collections.sort(provenanceStrings, PairComparator.<String, Double>negativeRight());
    String provenance = "";
    for (int i=0; i<featuresToShow; i++) {
      if (i >= provenanceStrings.size()) break;
      provenance += provenanceStrings.get(i).getLeft();
      if (i < featuresToShow-1 && i != provenanceStrings.size()-1) {
        provenance += separator;
      }
    }
    return provenance;
  }

  /**
   * A sample (and test) invocation of the methods in this class.
   */
  public static void main(String[] args) throws IOException {
    String base = "/home/mg1/pra/kod_models/";
    List<Pair<String, String>> relationsAndDomains = Lists.newArrayList();
    relationsAndDomains.add(new Pair<String, String>("concept:citylocatedinstate", "concept:stateorprovince"));
    relationsAndDomains.add(new Pair<String, String>("concept:cityparks", "concept:park"));
    relationsAndDomains.add(new Pair<String, String>("concept:citytelevisionstation", "concept:televisionstation"));
    relationsAndDomains.add(new Pair<String, String>("concept:cityliesonriver", "concept:river"));
    String modelBase = base + "results/";
    List<String> models = new ArrayList<String>();
    String targetsBase = base + "category_instances/";
    List<String> allowedTargets = new ArrayList<String>();
    List<String> relationNames = new ArrayList<String>();
    for (Pair<String, String> relationDomain : relationsAndDomains) {
      models.add(modelBase + relationDomain.getLeft() + "/weights.tsv");
      relationNames.add(relationDomain.getLeft());
      allowedTargets.add(targetsBase + relationDomain.getRight());
    }
    String nodeDict = base + "node_dict.tsv";
    String edgeDict = base + "edge_dict.tsv";
    String graph = base + "graph_chi/edges.tsv";
    String pathTypeFactoryString = null;
    try {
      String pathTypeFactoryDescription = base + "factory_description.txt";
      BufferedReader reader = new BufferedReader(new FileReader(pathTypeFactoryDescription));
      pathTypeFactoryString = reader.readLine();
      reader.close();
    } catch (IOException e) { }
    int numShards = -1;
    try {
      String numShardsFile = base + "num_shards.tsv";
      BufferedReader reader = new BufferedReader(new FileReader(numShardsFile));
      numShards = Integer.parseInt(reader.readLine());
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException("Missing num_shards.tsv file!", e);
    }
    int walksPerPath = 50;
    String outputBase = "test_pra_output/";
    new File(outputBase).mkdirs();
    OnlinePraPredictor predictor = new OnlinePraPredictor(models,
                                                          nodeDict,
                                                          edgeDict,
                                                          graph,
                                                          numShards,
                                                          walksPerPath,
                                                          allowedTargets,
                                                          pathTypeFactoryString,
                                                          outputBase);
    /*
    predictor.testPredictTargets(Arrays.asList("concept:city:pittsburgh"), relationNames);
     */
    List<PraPrediction> predictions = predictor.predictTargets("concept:city:pittsburgh",
                                                               relationNames);
    System.out.println(predictions.size() + " predictions found");
    for (String relation : relationNames) {
      System.out.println(relation + ":");
      int count = 0;
      for (PraPrediction prediction : predictions) {
        if (!prediction.relation.equals(relation)) continue;
        count++;
        if (count > 5) break;
        System.out.print(prediction.targetNode + " ");
        System.out.print(prediction.score + " ");
        System.out.println(prediction.provenance);
      }
      System.out.println();
    }
    // Because NELL tends to hang things...
    System.exit(0);
  }
}
