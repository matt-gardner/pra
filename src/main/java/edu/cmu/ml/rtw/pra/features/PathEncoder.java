package edu.cmu.ml.rtw.pra.features;

public interface PathEncoder {
  /**
   * @return The length of the String array returned by encode().
   */
  public String[] encode(Path path);
}
