package edu.cmu.ml.rtw.users.matt.util;

/**
 * A String index is called a dictionary, because that's what I originally called it.
 */
public class Dictionary extends Index<String> {
  public Dictionary() {
    this(false);
  }

  public Dictionary(boolean verbose) {
    super(new StringParser(), verbose);
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
