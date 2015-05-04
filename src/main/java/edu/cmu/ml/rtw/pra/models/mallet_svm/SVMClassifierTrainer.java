package edu.cmu.ml.rtw.pra.models.mallet_svm;

import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.ml.rtw.pra.models.mallet_svm.common.SparseVector;
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.CustomKernel;
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.KernelManager;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.SVMTrainer;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_model;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_parameter;

/**
 * Class used to generate an SVMClassifier.
 * @author Syeed Ibn Faiz
 */
public class SVMClassifierTrainer extends ClassifierTrainer<SVMClassifier> {

  private SVMClassifier classifier;
  private Map<String, Double> mLabel2sLabel;		// we don't use it here since input data already has double format
  private CustomKernel kernel;
  private int numClasses;
  private boolean predictProbability;
  private svm_parameter param;

  public SVMClassifierTrainer(CustomKernel kernel) {
    this(kernel, false);		// predictProbability set to false
  }

  public SVMClassifierTrainer(CustomKernel kernel, boolean predictProbability) {
    super();
    mLabel2sLabel = new HashMap<String, Double>();
    this.kernel = kernel;
    this.predictProbability = predictProbability;
    init();
  }

  private void init() {
    param = new svm_parameter();
    if (predictProbability) {
      param.probability = 1;
    }
  }

  public svm_parameter getParam() {
    return param;
  }

  public void setParam(svm_parameter param) {
    this.param = param;
  }

  public CustomKernel getKernel() {
    return kernel;
  }

  public void setKernel(CustomKernel kernel) {
    this.kernel = kernel;
  }

  @Override
  public SVMClassifier getClassifier() {
    return classifier;
  }

  @Override
  public SVMClassifier train(InstanceList trainingSet) {
    cleanUp();
    KernelManager.setCustomKernel(kernel);
    svm_model model = SVMTrainer.train(getSVMInstances(trainingSet), param);

    System.out.println("the label map used to create the svm classifier is ");
    System.out.println(mLabel2sLabel.toString());
    classifier = new SVMClassifier(model, kernel, mLabel2sLabel, trainingSet.getPipe(), predictProbability);

    return classifier;
  }

  private void cleanUp() {
    mLabel2sLabel.clear();
    numClasses = 0;
  }

  private List<edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.Instance> getSVMInstances(InstanceList instanceList) {
    List<edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.Instance> list = new ArrayList<edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.Instance>();
    for (Instance instance : instanceList) {
      SparseVector vector = getVector(instance);
      list.add(new edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.Instance(getLabel((Label) instance.getTarget()), vector));
    }
    return list;
  }

  private double getLabel(Label target) {
    Double label = mLabel2sLabel.get(target.toString());
    if (label == null) {
      numClasses++;
      label = 1.0 * numClasses;
      mLabel2sLabel.put(target.toString(), label);
    }
    return label;
  }

  static SparseVector getVector(Instance instance) {
    FeatureVector fv = (FeatureVector) instance.getData();
    int[] indices = fv.getIndices();
    double[] values = fv.getValues();
    SparseVector vector = new SparseVector();
    for (int i = 0; i < indices.length; i++) {
      vector.add(indices[i], values[i]);
    }
    vector.sortByIndices();
    return vector;
  }
}
