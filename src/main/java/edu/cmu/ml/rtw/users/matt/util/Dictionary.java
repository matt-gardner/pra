package edu.cmu.ml.rtw.users.matt.util;

/**
 * A String index is called a dictionary, because that's what I originally called it.
 */
public class Dictionary extends Index<String> {
  public Dictionary() {
    this(false);
  }

  public Dictionary(FileUtil fileUtil) {
    this(false, fileUtil);
  }

  public Dictionary(boolean verbose) {
    this(verbose, new FileUtil());
  }

  public Dictionary(boolean verbose, FileUtil fileUtil) {
    super(new StringParser(), verbose, fileUtil);
  }

  /**
   * Test if string is already in the dictionary
   */
  public boolean hasString(String string) {
    return hasKey(string);
  }

  public String getString(int index) {
    return getKey(index);
  }
}
