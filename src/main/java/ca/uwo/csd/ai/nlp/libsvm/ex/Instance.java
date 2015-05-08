package ca.uwo.csd.ai.nlp.libsvm.ex;

/**
 *
 * @author Syeed Ibn Faiz
 */
public class Instance {
  private double label;
  private Object data;

  public Instance(double label, Object data) {
    this.label = label;
    this.data = data;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public double getLabel() {
    return label;
  }

  public void setLabel(double label) {
    this.label = label;
  }
}
