package edu.cmu.ml.rtw.pra.mallet_svm.kernel;

import edu.cmu.ml.rtw.pra.mallet_svm.libsvm.svm_node;

/**
 * Interface for a custom kernel function
 * @author Syeed Ibn Faiz
 */
public interface CustomKernel {
    double evaluate(svm_node x, svm_node y);
}
