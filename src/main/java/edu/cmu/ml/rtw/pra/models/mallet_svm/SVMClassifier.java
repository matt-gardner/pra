package edu.cmu.ml.rtw.pra.models.mallet_svm;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.io.Serializable;

import edu.cmu.ml.rtw.pra.models.mallet_svm.common.SparseVector;
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.CustomKernel;
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.KernelManager;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.SVMPredictor;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_model;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_parameter;

/**
 * A wrapper for LibSVM classifier.
 * @author Syeed Ibn Faiz
 */
public class SVMClassifier extends Classifier implements Serializable {

  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private svm_model model;
  private Map<String, Double> mltLabel2svmLabel;       //mapping from Mallet to SVM label
  private Map<Double, String> svmLabel2mltLabel;       //mapping from SVM label to Mallet Label
  private int[] svmIndex2mltIndex;                    //mapping from SVM Label indices (svm.label) to Mallet Label indices (targetLabelAlphabet)
  private boolean predictProbability;                //whether probability is predicted by SVM
  private CustomKernel kernel;                        //custom kernel used to train the model

  public SVMClassifier(svm_model model, CustomKernel kernel, Map<String, Double> mLabel2sLabel, Pipe instancePipe, boolean predictProbability) {
    super(instancePipe);
    this.model = model;
    this.kernel = kernel;
    this.mltLabel2svmLabel = mLabel2sLabel;
    this.predictProbability = predictProbability;
    init();
  }

  private void init() {
    svmLabel2mltLabel = new HashMap<Double, String>();
    for (Entry<String, Double> entry : mltLabel2svmLabel.entrySet()) {
      svmLabel2mltLabel.put(entry.getValue(), entry.getKey());
    }
    svmIndex2mltIndex = new int[model.nr_class + 1];
    int[] sLabels = model.label;
    LabelAlphabet labelAlphabet = getLabelAlphabet();
    for (int sIndex = 0; sIndex < sLabels.length; sIndex++) {
      double sLabel = sLabels[sIndex];
      String mLabel = svmLabel2mltLabel.get(sLabel * 1.0);
      System.out.println("mLabel is " + mLabel);
      //int mIndex = labelAlphabet.lookupIndex(mLabel.toString(), false);
      int mIndex = labelAlphabet.lookupIndex(sLabel * 1.0, false);
      svmIndex2mltIndex[sIndex] = mIndex;
    }
  }

  @Override
  public Classification classify(Instance instance) {
    //TODO: find a better approach
    KernelManager.setCustomKernel(this.kernel);
    SparseVector vector = SVMClassifierTrainer.getVector(instance);    // convert this into a sparse vector
    double[] scores = new double[model.nr_class];    // scores for each each class

    double sLabel = mltLabel2svmLabel.get(getLabelAlphabet().lookupLabel(instance.getTarget().toString()).toString());
    double p = SVMPredictor.predictProbability(new edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.Instance(sLabel, vector), model, scores);

    //if SVM is not predicting probability then assign a score of 1.0 to the best class(p)
    //and 0.0 to the other classes
    if (!predictProbability) {
      String label = svmLabel2mltLabel.get(p);
      int index = getLabelAlphabet().lookupIndex(label, false);
      scores[index] = 1.0;
    } else {
      rearrangeScores(scores);
    }
    Classification classification = new Classification(instance, this,
                                                       new LabelVector(getLabelAlphabet(), scores));

    return classification;
  }

  public double scoreInstance(Instance instance){
    KernelManager.setCustomKernel(this.kernel);
    SparseVector vector = SVMClassifierTrainer.getVector(instance);    // convert this into a sparse vector
    double[] scores = new double[model.nr_class];    // scores for each each class
    double sLabel = mltLabel2svmLabel.get(getLabelAlphabet().lookupLabel(instance.getTarget()).toString());
    double p = 0.0;
    // call myPredictprobability if the number of classes is 2
    if( model.param.svm_type == svm_parameter.C_SVC){
      if(model.nr_class == 2)
        p = SVMPredictor.myPredictValues(new edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.Instance(sLabel, vector), model, scores);
      else{
        System.out.println("Number of classes is not 2, printing out the most likely label instead.");
        p = SVMPredictor.predictProbability(new edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.ex.Instance(sLabel, vector), model, scores);
      }
    }
    else
      p = -5.0;
    return p;
  }

  /**
   * SVM model's label indices differ from labelAlphabet's label indices, which is why we
   * need to rearrange the score vector returned by the SVM model.
   * @param scores
   */
  private void rearrangeScores(double[] scores) {
    for (int i = 0; i < scores.length; i++) {
      int mIndex = svmIndex2mltIndex[i];
      double tmp = scores[i];
      scores[i] = scores[mIndex];
      scores[mIndex] = tmp;
    }
  }

  public svm_model getModel(){
    return this.model;
  }
}
