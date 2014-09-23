package edu.cmu.ml.rtw.users.matt.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

/**
 * A mapping from some object to integers, for any application where such a mapping is useful
 * (generally because working with integers is much faster and less memory-intensive than working
 * with objects).
 */
public class Index<T> {
  private final BiMap<T, Integer> bimap;
  private final AtomicInteger nextIndex;
  private final boolean verbose;
  private final ObjectParser<T> factory;

  public Index(ObjectParser<T> factory) {
    this(factory, false);
  }

  public Index(ObjectParser<T> factory, boolean verbose) {
    bimap = Maps.synchronizedBiMap(HashBiMap.<T, Integer>create());
    nextIndex = new AtomicInteger(1);
    this.verbose = verbose;
    this.factory = factory;
  }

  /**
   * Test if key is already in the dictionary
   */
  public boolean hasKey(T key) {
    return bimap.containsKey(key);
  }

  /**
   * Returns the index for key, adding to the dictionary if necessary.
   */
  public int getIndex(T key) {
    if (key == null) {
      throw new RuntimeException("A null key was passed to the dictionary!");
    }
    Integer i = bimap.get(key);
    if (i == null) {
      if (verbose) {
        System.out.println("Key not in index: " + key);
      }
      Integer new_i = nextIndex.getAndIncrement();
      bimap.put(key, new_i);
      i = new_i;
    }
    return i;
  }

  public T getKey(int index) {
    return bimap.inverse().get(index);
  }

  public int getNextIndex() {
    return nextIndex.get();
  }

  public void clear() {
    bimap.clear();
    nextIndex.set(1);
  }

  public void writeToFile(File outfile) throws IOException {
    writeToWriter(new FileWriter(outfile));
  }

  public void writeToWriter(FileWriter writer)
      throws IOException {
        if (nextIndex.get() > 20000000) {
          // This is approaching the size of something that can't fit in a String object, so we
          // have to write it directly to disk, not use the printToString() method.
          StringBuilder builder = new StringBuilder();
          for (int i=1; i<nextIndex.get(); i++) {
            if (i % 1000000 == 0) {
              writer.write(builder.toString());
              builder = new StringBuilder();
            }
            builder.append(i);
            builder.append("\t");
            builder.append(getKey(i).toString());
            builder.append("\n");
          }
          writer.write(builder.toString());
        } else {
          writer.write(printToString());
        }
        writer.close();
      }

  public void setFromFile(String filename) {
    setFromFile(new File(filename));
  }

  public void setFromFile(File file) {
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(
          new FileInputStream(file)));
      setFromReader(reader);
    } catch (IOException e) {
      System.out.println("IOException thrown in setFromFile()");
      e.printStackTrace();
    }
  }

  public void setFromReader(BufferedReader reader) {
    bimap.clear();
    String line;
    try {
      int max_index = 0;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\t");
        int num = Integer.parseInt(parts[0]);
        if (num > max_index) {
          max_index = num;
        }
        T key = factory.fromString(parts[1]);
        bimap.put(key, num);
      }
      nextIndex.set(max_index+1);
    } catch (IOException e) {
      System.out.println("IOException thrown in setFromReader()");
      e.printStackTrace();
    }
  }

  public String printToString() throws IOException {
    StringBuilder builder = new StringBuilder();
    for (int i=1; i<nextIndex.get(); i++) {
      builder.append(i);
      builder.append("\t");
      builder.append(getKey(i).toString());
      builder.append("\n");
    }
    return builder.toString();
  }
}
