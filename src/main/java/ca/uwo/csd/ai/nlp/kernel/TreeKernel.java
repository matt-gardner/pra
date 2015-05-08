package ca.uwo.csd.ai.nlp.kernel;

//import edu.stanford.nlp.trees.Tree;
import ca.uwo.csd.ai.nlp.common.Tree;
import java.util.ArrayList;
import java.util.List;
import ca.uwo.csd.ai.nlp.libsvm.svm_node;

/**
 * <code>TreeKernel</code> provides a naive implementation of the kernel function described in
 * 'Parsing with a single neuron: Convolution kernels for NLP problems'.
 * @author Syeed Ibn Faiz
 */
public class TreeKernel implements CustomKernel {
  private final static int MAX_NODE = 300;
  double mem[][] = new double[MAX_NODE][MAX_NODE];
  private double lambda; //penalizing factor

  public TreeKernel() {
    this(0.5);
  }

  public TreeKernel(double lambda) {
    this.lambda = lambda;
  }

  @Override
  public double evaluate(svm_node x, svm_node y) {
    Object k1 = x.data;
    Object k2 = y.data;

    if (!(k1 instanceof Tree) || !(k2 instanceof Tree)) {
      throw new IllegalArgumentException("svm_node does not contain tree data.");
    }

    Tree t1 = (Tree) k1;
    Tree t2 = (Tree) k2;

    List<Tree> nodes1 = getNodes(t1);
    List<Tree> nodes2 = getNodes(t2);

    int N1 = Math.min(MAX_NODE, nodes1.size());
    int N2 = Math.min(MAX_NODE, nodes2.size());

    //fill mem with -1.0
    initMem(mem, N1, N2);

    double result = 0.0;
    for (int i = 0; i < N1; i++) {
      for (int j = 0; j < N2; j++) {
        result += compute(i, j, nodes1, nodes2, mem);
      }
    }

    return result;
  }

  /**
   * Efficient computation avoiding costly equals method of Tree
   * @param trees
   * @param t
   * @return
   */
  private int indexOf(List<Tree> trees, Tree t) {
    for (int i = 0; i < trees.size(); i++) {
      if (t == trees.get(i)) {
        return i;
      }
    }
    return -1;
  }

  private double compute(int i, int j, List<Tree> nodes1, List<Tree> nodes2, double[][] mem) {
    if (mem[i][j] >= 0) {
      return mem[i][j];
    }
    //if (sameProduction(nodes1.get(i), nodes2.get(j))) {
    if (nodes1.get(i).value().equals(nodes2.get(j).value()) &&
        nodes1.get(i).hashCode() == nodes2.get(j).hashCode()) {     //similar hashCode -> same production

      mem[i][j] = lambda * lambda;
      if (!nodes1.get(i).isLeaf() && !nodes2.get(j).isLeaf()) {
        List<Tree> childList1 = nodes1.get(i).getChildrenAsList();
        List<Tree> childList2 = nodes2.get(j).getChildrenAsList();
        for (int k = 0; k < childList1.size(); k++) {
          //mem[i][j] *= 1 + compute(nodes1.indexOf(childList1.get(k)), nodes2.indexOf(childList2.get(k)), nodes1, nodes2, mem);
          mem[i][j] *= 1 + compute(indexOf(nodes1, childList1.get(k)), indexOf(nodes2, childList2.get(k)), nodes1, nodes2, mem);
        }
      }
    } else {
      mem[i][j] = 0.0;
    }

    return mem[i][j];
  }

  private boolean sameProduction(Tree t1, Tree t2) {
    if (t1.value().equals(t2.value())) {
      List<Tree> childList1 = t1.getChildrenAsList();
      List<Tree> childList2 = t2.getChildrenAsList();
      if (childList1.size() == childList2.size()) {
        for (int i = 0; i < childList1.size(); i++) {
          if (!childList1.get(i).value().equals(childList2.get(i).value())) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }
  private void initMem(double[][] mem, int N1, int N2) {
    for (int i = 0; i < N1; i++) {
      for (int j = 0; j < N2; j++) {
        mem[i][j] = -1.0;
      }
    }
  }
  private List<Tree> getNodes(Tree t) {
    ArrayList<Tree> nodes = new ArrayList<Tree>();
    addNodes(t, nodes);
    return nodes;
  }

  private void addNodes(Tree t, List<Tree> nodes) {
    nodes.add(t);
    List<Tree> childList = t.getChildrenAsList();
    for (Tree child : childList) {
      addNodes(child, nodes);
    }
  }
}
