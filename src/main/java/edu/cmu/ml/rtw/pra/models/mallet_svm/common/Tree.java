package edu.cmu.ml.rtw.pra.mallet_svm.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Dummy Tree data structure
 * @author Syeed Ibn Faiz
 */
public class Tree {
    private String value;
    private List<Tree> children;

    public Tree(String value) {
        this.value = value;
        children = new ArrayList<Tree>();
    }

    public Tree(String value, List<Tree> children) {
        this.value = value;
        this.children = children;
    }        
    
    public List<Tree> getChildrenAsList() {
        return children;
    }
    
    public String value() {
        return value;
    }
    
    public boolean isLeaf() {
        return children.isEmpty();
    }
}
