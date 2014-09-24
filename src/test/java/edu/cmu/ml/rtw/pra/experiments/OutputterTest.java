package edu.cmu.ml.rtw.pra.experiments;

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory;
import edu.cmu.ml.rtw.pra.features.FeatureMatrix;
import edu.cmu.ml.rtw.pra.features.MatrixRow;
import edu.cmu.ml.rtw.pra.features.PathType;
import edu.cmu.ml.rtw.pra.features.PathTypeFactory;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class OutputterTest extends TestCase {
  private FakeFileUtil fileUtil;
  private Dictionary nodeDict = new Dictionary();
  private Dictionary edgeDict = new Dictionary();
  private Map<String, String> nodeNames;
  private Outputter outputter;
  private PathTypeFactory factory = new BasicPathTypeFactory();

  private String rel1 = "rel1";
  private int rel1Index = edgeDict.getIndex(rel1);

  private String rel2 = "rel2";
  private int rel2Index = edgeDict.getIndex(rel2);

  private String node1 = "node1";
  private int node1Index = nodeDict.getIndex(node1);

  private String node2 = "node2";
  private int node2Index = nodeDict.getIndex(node2);

  @Override
  public void setUp() {
    fileUtil = new FakeFileUtil();
    nodeNames = Maps.newHashMap();
    outputter = new Outputter(null, null, null, fileUtil);
  }

  public void testGetNodeReturnsIndexWithNoDictionary() {
    outputter = new Outputter();
    assertEquals("1", outputter.getNode(1));
  }

  public void testGetNodeReturnsNodeStringWithDictionary() {
    outputter = new Outputter(nodeDict, edgeDict, nodeNames, fileUtil);
    assertEquals(node1, outputter.getNode(node1Index));
  }

  public void testGetNodeReturnsNodeNameWhenPresent() {
    nodeNames.put(node1, "node1name");
    outputter = new Outputter(nodeDict, edgeDict, nodeNames, fileUtil);
    assertEquals("node1name", outputter.getNode(node1Index));
    assertEquals(node2, outputter.getNode(node2Index));
  }

  public void testGetPathTypeReturnsEncodedStringWithNoDictionary() {
    String pathType = "-1-2-3-";
    assertEquals(pathType, outputter.getPathType(factory.fromString(pathType)));
  }

  public void testGetPathTypeReturnsHumanReadableStringWithDictionary() {
    String pathType = "-1-2-";
    outputter = new Outputter(nodeDict, edgeDict, nodeNames, fileUtil);
    assertEquals("-rel1-rel2-", outputter.getPathType(factory.fromString(pathType)));
  }

  public void testOutputWeightsSortsWeightsAndIsFormattedCorrectly() {
    List<Double> weights = Lists.newArrayList();
    weights.add(.2);
    weights.add(.9);
    List<PathType> pathTypes = Lists.newArrayList();
    pathTypes.add(factory.fromString("-1-"));
    pathTypes.add(factory.fromString("-2-"));
    String weightFile = "weight_file";
    String expectedWeightFileContents =
        "-2-\t0.9\n" +
        "-1-\t0.2\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(weightFile, expectedWeightFileContents);
    outputter.outputWeights(weightFile, weights, pathTypes);
    fileUtil.expectFilesWritten();
  }

  public void testOutputPathCountsSortsCountsAndIsFormattedCorrectly() {
    String baseDir = "/";
    String filename = "path counts file";
    Map<PathType, Integer> pathCounts = Maps.newHashMap();
    pathCounts.put(factory.fromString("-1-"), 1);
    String pathCountFile = "/path counts file";
    String expectedPathCountFileContents = "-1-\t1\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(pathCountFile, expectedPathCountFileContents);
    outputter.outputPathCounts(baseDir, filename, pathCounts);
    fileUtil.expectFilesWritten();
  }

  public void testOutputScoresProducesACorrectScoresFile() {
    String filename = "/scores file";
    Dataset.Builder builder = new Dataset.Builder();
    builder.setPositiveSources(Lists.newArrayList(1, 3));
    builder.setPositiveTargets(Lists.newArrayList(2, 8));
    Dataset trainingData = builder.build();

    builder = new Dataset.Builder();
    builder.setPositiveSources(Lists.newArrayList(3, 10));
    builder.setPositiveTargets(Lists.newArrayList(4, 11));
    builder.setNegativeSources(Lists.newArrayList(5, 12));
    builder.setNegativeTargets(Lists.newArrayList(6, 13));
    Dataset testingData = builder.build();
    PraConfig config = new PraConfig.Builder()
        .setTrainingData(trainingData)
        .setTestingData(testingData)
        .build();

    Map<Integer, List<Pair<Integer, Double>>> sourceScores = Maps.newHashMap();
    sourceScores.put(3, Lists.newArrayList(Pair.makePair(7, 0.1),
                                           Pair.makePair(4, 0.6),
                                           Pair.makePair(8, 0.3)));
    sourceScores.put(12, Lists.newArrayList(Pair.makePair(1, 0.1)));

    String scoresFile = "/scores file";
    String expectedScoresFileContents =
        "3\t4\t0.6\t*\n" +
        "3\t8\t0.3\t*^\n" +
        "3\t7\t0.1\t\n" +
        "\n" +
        "5\t\t\t-\n" +
        "\n" +
        "10\t\t\t\n" +
        "\n" +
        "12\t1\t0.1\t-\n" +
        "\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(scoresFile, expectedScoresFileContents);
    outputter.outputScores(filename, sourceScores, config);
    fileUtil.expectFilesWritten();
  }

  public void testOutputSplitFilesWritesFourCorrectFiles() {
    String baseDir = "/";
    Dataset.Builder builder = new Dataset.Builder();
    builder.setPositiveSources(Lists.newArrayList(1, 2, 3));
    builder.setPositiveTargets(Lists.newArrayList(4, 5, 6));
    builder.setNegativeSources(Lists.newArrayList(9));
    builder.setNegativeTargets(Lists.newArrayList(10));
    Dataset trainingData = builder.build();

    builder = new Dataset.Builder();
    builder.setPositiveSources(Lists.newArrayList(7));
    builder.setPositiveTargets(Lists.newArrayList(8));
    builder.setNegativeSources(Lists.newArrayList(1));
    builder.setNegativeTargets(Lists.newArrayList(2));
    Dataset testingData = builder.build();

    String positiveTrainingFile = "/training_positive_examples.tsv";
    String expectedPositiveTrainingFileContents = "1\t4\n2\t5\n3\t6\n";
    String positiveTestingFile = "/testing_positive_examples.tsv";
    String expectedPositiveTestingFileContents = "7\t8\n";
    String negativeTrainingFile = "/training_negative_examples.tsv";
    String expectedNegativeTrainingFileContents = "9\t10\n";
    String negativeTestingFile = "/testing_negative_examples.tsv";
    String expectedNegativeTestingFileContents = "1\t2\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(positiveTrainingFile, expectedPositiveTrainingFileContents);
    fileUtil.addExpectedFileWritten(positiveTestingFile, expectedPositiveTestingFileContents);
    fileUtil.addExpectedFileWritten(negativeTrainingFile, expectedNegativeTrainingFileContents);
    fileUtil.addExpectedFileWritten(negativeTestingFile, expectedNegativeTestingFileContents);
    outputter.outputSplitFiles(baseDir, trainingData, testingData);
    fileUtil.expectFilesWritten();
  }

  public void testOutputSplitFilesWritesJustTwoFilesWhenThereIsNoNegativeData() {
    String baseDir = "/";
    Dataset.Builder builder = new Dataset.Builder();
    builder.setPositiveSources(Lists.newArrayList(1, 2, 3));
    builder.setPositiveTargets(Lists.newArrayList(4, 5, 6));
    Dataset trainingData = builder.build();

    builder = new Dataset.Builder();
    builder.setPositiveSources(Lists.newArrayList(7));
    builder.setPositiveTargets(Lists.newArrayList(8));
    Dataset testingData = builder.build();

    String positiveTrainingFile = "/training_positive_examples.tsv";
    String expectedPositiveTrainingFileContents = "1\t4\n2\t5\n3\t6\n";
    String positiveTestingFile = "/testing_positive_examples.tsv";
    String expectedPositiveTestingFileContents = "7\t8\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(positiveTrainingFile, expectedPositiveTrainingFileContents);
    fileUtil.addExpectedFileWritten(positiveTestingFile, expectedPositiveTestingFileContents);
    outputter.outputSplitFiles(baseDir, trainingData, testingData);
    fileUtil.expectFilesWritten();
  }

  public void testOutputPathCountMapFormatsDataCorrectly() {
    String baseDir = "/";
    String filename = "path count map";
    Dataset.Builder builder = new Dataset.Builder();
    builder.setPositiveSources(Lists.newArrayList(1));
    builder.setPositiveTargets(Lists.newArrayList(2));
    builder.setNegativeSources(Lists.newArrayList(3));
    builder.setNegativeTargets(Lists.newArrayList(4));
    Dataset data = builder.build();

    Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCountMap = Maps.newHashMap();
    Map<PathType, Integer> pathCounts = Maps.newHashMap();
    pathCounts.put(factory.fromString("-1-"), 22);
    pathCountMap.put(Pair.makePair(1, 2), pathCounts);

    String pathCountMapFile = "/path count map";
    String expectedPathCountMapFileContents =
        "1\t2\t+\n" +
        "\t-1-\t22\n" +
        "\n" +
        "3\t4\t-\n" +
        "\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(pathCountMapFile, expectedPathCountMapFileContents);
    outputter.outputPathCountMap(baseDir, filename, pathCountMap, data);
    fileUtil.expectFilesWritten();
  }

  public void testOutputPathsFormatsDataCorrectly() {
    String baseDir = "/";
    String filename = "path file";
    List<PathType> pathTypes = Lists.newArrayList();
    pathTypes.add(factory.fromString("-1-"));

    String pathFile = "/path file";
    String expectedPathFileContents = "-1-\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(pathFile, expectedPathFileContents);
    outputter.outputPaths(baseDir, filename, pathTypes);
    fileUtil.expectFilesWritten();
  }

  public void testFeatureMatrixFormatsMatrixCorrectly() {
    String filename = "/matrix file";
    List<MatrixRow> rows = Lists.newArrayList();
    rows.add(new MatrixRow(1, 2, new int[]{0, 1}, new double[]{0.1, 0.2}));
    List<PathType> pathTypes = Lists.newArrayList();
    pathTypes.add(factory.fromString("-1-"));
    pathTypes.add(factory.fromString("-2-"));

    String matrixFile = "/matrix file";
    String expectedMatrixFileContents = "1,2\t-1-,0.1 -#- -2-,0.2\n";

    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(matrixFile, expectedMatrixFileContents);
    outputter.outputFeatureMatrix(filename, new FeatureMatrix(rows), pathTypes);
    fileUtil.expectFilesWritten();
  }

  public void testOutputMethodsDoNothingWithNullBaseDir() {
    fileUtil.onlyAllowExpectedFiles();
    outputter.outputSplitFiles(null, null, null);
    outputter.outputPathCounts(null, null, null);
    outputter.outputPathCountMap(null, null, null, null);
    outputter.outputPaths(null, null, null);
    fileUtil.expectFilesWritten();
  }

  // Mostly just here for completeness, because I am OCD about coverage numbers.
  public void testOutputMethodsTurnIOExceptionsToRuntimeExceptions() {
    fileUtil.throwIOExceptionOnWrite();
    Map<PathType, Integer> pathCounts = Maps.newHashMap();
    boolean thrown = false;
    try {
      outputter.outputPathCounts("/", "non-existant-path", pathCounts);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      outputter.outputWeights("/non-existant-path", null, null);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      outputter.outputScores("/non-existant-path", null, null);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      outputter.outputPathCountMap("/", "non-existant-path", null, null);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      outputter.outputPaths("/", "non-existant-path", null);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      outputter.outputFeatureMatrix("/non-existant-path", null, null);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      outputter.outputSplitFiles("/", null, null);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);

    thrown = false;
    try {
      outputter.outputScores("/non-existant-path", null, null);
    } catch (RuntimeException e) {
      thrown = true;
    }
    assertTrue(thrown);
  }
}

