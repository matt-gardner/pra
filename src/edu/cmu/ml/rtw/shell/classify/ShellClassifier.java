package edu.cmu.ml.rtw.shell.classify;

import java.io.File;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * Abstract class for classifiers in the ShellToy framework.
 * 
 * @author bsettles
 */
public abstract class ShellClassifier {

    protected String name = "generic";

    // feature alphabet (string <--> ID mapping)
    protected Alphabet alphabet;

    public ShellClassifier(Alphabet alphabet) {
        this.alphabet = alphabet;
    }

    // take an instance and return a posterior class estimate
    public abstract double classify(Instance instance);

    // take an InstanceList and train the classifier
    public abstract void train(InstanceList ilist);

    // do any housecleaning to reduce classifier's resource footprint (without compromising its
    // ability to classify or to be trained).
    public abstract void compact();

    // various provenance info
    public abstract String getModelSummary();
    public abstract String getProvenance(Instance instance);
    
    // model IO stuff ...
    public void readModelFromFile(String modelFileName) {
        readModelFromFile(new File(modelFileName));
    }

    public void writeModelToFile(String modelFileName) {
        writeModelToFile(new File(modelFileName));
    }

    public void readModelFromFile(File modelFile) {
        readModelFromFile(modelFile, null);
    }

    public abstract void readModelFromFile(File modelFile, int[] alphabetMap);
    public abstract void writeModelToFile(File modelFile);

}
