package edu.cmu.ml.rtw.pra.models.mallet_svm.kernel;

import edu.cmu.ml.rtw.pra.models.mallet_svm.common.SparseVector;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_node;
import edu.cmu.ml.rtw.pra.models.mallet_svm.common.SparseVector.Element;
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_parameter;

/**
 *  <code>RBFKernel</code> implements an RBF kernel.
 * @author Syeed Ibn Faiz
 */
public class RBFKernel implements CustomKernel {

  svm_parameter param;
  public RBFKernel(svm_parameter param) {
    this.param = param;
  }


  @Override
  public double evaluate(svm_node x, svm_node y) {
    if (!(x.data instanceof SparseVector) || !(y.data instanceof SparseVector)) {
      throw new RuntimeException("svm_nodes should contain sparse vectors.");
    }

    SparseVector v1 = (SparseVector) x.data;
    SparseVector v2 = (SparseVector) y.data;
    double result = 0.0;
    double norm = 0.0;
    int i = 0;
    int j = 0;

    while (i < v1.size() && j < v2.size()) {
      Element e1 = v1.get(i);
      Element e2 = v2.get(j);

      if (e1.index == e2.index) {
        double d = e1.value - e2.value;
        result += d * d;
        i++;
        j++;
      } else if (e1.index < e2.index) {
        result += e1.value * e1.value;
        i++;
      } else {
        result += e2.value * e2.value;
        j++;
      }
    }

    while (i < v1.size()) {
      Element e1 = v1.get(i);
      result += e1.value * e1.value;
      i++;
    }

    while (j < v2.size()) {
      Element e2 = v2.get(j);
      result += e2.value * e2.value;
      j++;
    }

    return Math.exp(-param.gamma * result);
  }
}
