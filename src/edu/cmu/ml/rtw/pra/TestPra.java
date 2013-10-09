package edu.cmu.ml.rtw.pra;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.cmu.ml.rtw.users.matt.util.CollectionsUtil;
import edu.cmu.ml.rtw.util.Dictionary;
import edu.cmu.ml.rtw.util.Pair;

/**
 * A simple class whose purpose is to provide a main method to test whatever I'm working on at the
 * moment.
 */
public class TestPra {

    public static void testLearnWeights() throws IOException {
        String base = "/home/mg1/pra/pca_d_svo_nell/results/concept:citycapitalofcountry/";
        PraConfig config = new PraConfig.Builder()
                .setL1Weight(1)
                .setOutputBase(base)
                .build();
        List<MatrixRow> featureMatrix = PraDriver.readFeatureMatrixFromFile(base + "matrix.tsv");
        Dataset dataset = Dataset.readFromFile(base + "training_examples.tsv");
        List<Pair<PathType, Double>> weights = PraDriver.readWeightsFromFile(base + "weights.tsv");
        List<PathType> pathTypes = new ArrayList<PathType>();
        for (Pair<PathType, Double> pathWeight : weights) {
            pathTypes.add(pathWeight.getLeft());
        }
        PraDriver.learnFeatureWeights(config, featureMatrix, dataset, pathTypes);
        String edgeDictionaryFilename = "/home/mg1/pra/pca_d_svo_nell/edge_dict.tsv";
        Dictionary edgeDict = new Dictionary();
        edgeDict.setFromFile(new File(edgeDictionaryFilename));
        PraDriver.translateWeightFile(base + "weights.tsv", edgeDict);
    }

    public static void translateExampleFiles() throws IOException {
        String base = "/home/mg1/pra/pca_d_svo_nell/";
        Dictionary nodeDict = new Dictionary();
        nodeDict.setFromFile(new File(base + "node_dict.tsv"));
        base += "results/concept:teamhomestadium/";
        PraDriver.translateExampleFile(base + "training_positive_examples.tsv", nodeDict);
        PraDriver.translateExampleFile(base + "training_negative_examples.tsv", nodeDict);
        PraDriver.translateExampleFile(base + "testing_positive_examples.tsv", nodeDict);
        PraDriver.translateExampleFile(base + "testing_negative_examples.tsv", nodeDict);
    }

    public static void main(String[] args) throws IOException {
        translateExampleFiles();
        //testLearnWeights();
    }
}
