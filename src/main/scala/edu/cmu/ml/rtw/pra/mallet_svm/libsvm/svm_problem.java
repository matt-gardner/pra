package edu.cmu.ml.rtw.pra.mallet_svm.libsvm;

public class svm_problem implements java.io.Serializable {

    public int l;
    public double[] y;
    public svm_node[] x;

    public svm_problem(int l, double[] y, svm_node[] x) {
        this.l = l;
        this.y = y;
        this.x = x;
    }

    public svm_problem() {
    }
    
}
