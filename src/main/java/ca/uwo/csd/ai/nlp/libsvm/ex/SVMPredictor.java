package ca.uwo.csd.ai.nlp.libsvm.ex;

import ca.uwo.csd.ai.nlp.libsvm.svm;
import ca.uwo.csd.ai.nlp.libsvm.svm_model;
import ca.uwo.csd.ai.nlp.libsvm.svm_node;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author Syeed Ibn Faiz
 */
public class SVMPredictor {

  public static double[] predict(List<Instance> instances, svm_model model) {
    return predict(instances, model, true);
  }

  public static double[] predict(List<Instance> instances, svm_model model, boolean displayResult) {
    Instance[] array = new Instance[instances.size()];
    array = instances.toArray(array);
    return predict(array, model, displayResult);
  }

  public static double predict(Instance instance, svm_model model, boolean displayResult) {
    return svm.svm_predict(model, new svm_node(instance.getData()));
  }

  public static double predictProbability(Instance instance, svm_model model, double[] probabilities) {
    return svm.svm_predict_probability(model, new svm_node(instance.getData()), probabilities);
  }

  // only call this if the data has 2 classes and type is C_SVC
  public static double myPredictValues(Instance instance, svm_model model, double[] scores){
    return svm.svm_my_predict_values(model, new svm_node(instance.getData()), scores);
  }
  public static double[] predict(Instance[] instances, svm_model model, boolean displayResult) {
    int total = 0;
    int correct = 0;

    int tp = 0;
    int fp = 0;
    int fn = 0;

    boolean binary = model.nr_class == 2;
    double[] predictions = new double[instances.length];
    int count = 0;

    for (Instance instance : instances) {
      double target = instance.getLabel();
      double p = svm.svm_predict(model, new svm_node(instance.getData()));
      predictions[count++] = p;

      ++total;
      if (p == target) {
        correct++;
        if (target > 0) {
          tp++;
        }
      } else if (target > 0) {
        fn++;
      } else {
        fp++;
      }
    }
    if (displayResult) {
      System.out.print("Accuracy = " + (double) correct / total * 100
                       + "% (" + correct + "/" + total + ") (classification)\n");

      if (binary) {
        double precision = (double) tp / (tp + fp);
        double recall = (double) tp / (tp + fn);
        System.out.println("Precision: " + precision);
        System.out.println("Recall: " + recall);
        System.out.println("Fscore: " + 2 * precision * recall / (precision + recall));
      }
    }
    return predictions;
  }

  public static svm_model loadModel(String filePath) throws IOException, ClassNotFoundException {
    return svm.svm_load_model(filePath);
  }
}
