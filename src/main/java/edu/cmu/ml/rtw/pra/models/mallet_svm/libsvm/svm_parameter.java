package edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm;

public class svm_parameter implements Cloneable, java.io.Serializable {
  /* svm_type */

  public static final int C_SVC = 0;
  public static final int NU_SVC = 1;
  public static final int ONE_CLASS = 2;
  public static final int EPSILON_SVR = 3;
  public static final int NU_SVR = 4;

  /* kernel_type */
  public static final int CUSTOM = 0;

  public int svm_type;
  public int kernel_type;
  public int degree;    // for poly
  public double gamma;    // for poly/rbf/sigmoid
  public double coef0;    // for poly/sigmoid
  // these are for training only
  public double cache_size; // in MB
  public double eps;    // stopping criteria
  public double C;    // for C_SVC, EPSILON_SVR and NU_SVR
  public int nr_weight;        // for C_SVC
  public int[] weight_label;    // for C_SVC
  public double[] weight;        // for C_SVC
  public double nu;    // for NU_SVC, ONE_CLASS, and NU_SVR
  public double p;    // for EPSILON_SVR
  public int shrinking;    // use the shrinking heuristics
  public int probability; // do probability estimates


  public svm_parameter() {
    svm_type = svm_parameter.C_SVC;
    kernel_type = svm_parameter.CUSTOM;
    degree = 3;
    gamma = 0;    // 1/num_features
    coef0 = 0;
    nu = 0.5;
    cache_size = 100;
    C = 1;
    eps = 1e-3;
    p = 0.1;
    shrinking = 1;
    probability = 0;
    nr_weight = 0;
    weight_label = new int[0];
    weight = new double[0];
  }

  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      return null;
    }
  }
}
