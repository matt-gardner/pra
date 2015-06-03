package edu.cmu.ml.rtw.users.matt.util;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;

public class FakeFileWriter extends FileWriter {

  private List<String> written;
  private String filename;
  private boolean isClosed = false;

  public FakeFileWriter() throws IOException {
    this(null);
  }

  public FakeFileWriter(String filename) throws IOException {
    super("/dev/null");
    this.filename = filename;
    written = Lists.newArrayList();
  }

  @Override
  public void write(String line) {
    written.add(line);
  }

  @Override
  public void close() throws IOException {
    super.close();
    isClosed = true;
  }

  public boolean isClosed() {
    return isClosed;
  }

  public List<String> getWritten() {
    List<String> lines = Lists.newArrayList();
    lines.addAll(written);
    return lines;
  }

  public void expectWritten(String expected) {
    TestCase.assertTrue("File " + filename + " not closed", isClosed);
    String concatenated = "";
    for (String line : written) {
      concatenated += line;
    }
    TestCase.assertEquals("File: " + filename, expected, concatenated);
    written = Lists.newArrayList();
  }

  /**
   * Assert that each of the lines passed in has been written, but don't care about the order.
   */
  public void expectWritten(Collection<String> lines) {
    TestCase.assertEquals("File: " + filename, lines.size(), written.size());
    Multiset<String> counts = HashMultiset.create(lines);
    for (Multiset.Entry<String> entry : counts.entrySet()) {
      TestUtil.assertCount(written, entry.getElement(), entry.getCount());
    }
    written = Lists.newArrayList();
  }

  /**
   * Essentially the same as expectWritten(String), except you can pass in a list if you want.
   */
  public void expectWrittenInOrder(List<String> lines) {
    TestCase.assertEquals("File: " + filename, lines.size(), written.size());
    for (int i = 0; i < lines.size(); i++) {
      TestCase.assertEquals(lines.get(i), written.get(i));
    }
    written = Lists.newArrayList();
  }
}
