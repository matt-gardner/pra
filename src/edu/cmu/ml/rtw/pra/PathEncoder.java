package edu.cmu.ml.rtw.pra;

public interface PathEncoder {
    /**
     * @return The length of the String array returned by encode().
     */
    public int getNumPaths();
    public String[] encode(Path path);
}
