package edu.cmu.ml.rtw.users.matt.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class FileBuffer {
  private final int numToBuffer;
  private final String filename;

  private List<String> lines;

  public FileBuffer(String filename, int numToBuffer) {
    this.filename = filename;
    this.numToBuffer = numToBuffer;
    lines = new ArrayList<String>();
  }

  public void writeLine(String line) throws IOException {
    lines.add(line);
    if (lines.size() >= numToBuffer) {
      flush();
    }
  }

  public void flush() throws IOException {
    FileUtils.writeLines(new File(filename), lines, true);
    lines.clear();
  }
}
