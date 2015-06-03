package edu.cmu.ml.rtw.users.matt.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class FakeFileUtil extends FileUtil {

  private Set<String> existingPaths;
  private List<Double> doubleList;
  private Map<String, String> expectedFileContents;
  private Map<String, FakeFileWriter> fileWriters;
  private Map<String, String> readerFileContents;
  private boolean onlyAllowExpectedFiles;
  private boolean throwIOExceptionOnWrite;

  public FakeFileUtil() {
    existingPaths = Sets.newHashSet();
    doubleList = Lists.newArrayList();
    expectedFileContents = Maps.newHashMap();
    fileWriters = Maps.newHashMap();
    readerFileContents = Maps.newHashMap();
    onlyAllowExpectedFiles = false;
    throwIOExceptionOnWrite = false;
  }

  private Set<String> allFiles() {
    Set<String> knownFiles = Sets.newHashSet();
    knownFiles.addAll(readerFileContents.keySet());
    knownFiles.addAll(existingPaths);
    return knownFiles;
  }

  @Override
  public void mkdirOrDie(String dirName) {
  }

  @Override
  public void mkdirs(String dirName) {
  }

  @Override
  public boolean fileExists(String path) {
    return allFiles().contains(path);
  }

  @Override
  public BufferedReader getBufferedReader(String filename) {
    TestCase.assertNotNull("Unexpected file read: " + filename, readerFileContents.get(filename));
    return new BufferedReader(new StringReader(readerFileContents.get(filename)));
  }

  @Override
  public List<Double> readDoubleListFromFile(String filename) {
    return doubleList;
  }

  @Override
  public List<String> listDirectoryContents(String dirname) {
    if (dirname.endsWith("/")) dirname = dirname.substring(0, dirname.length() - 1);
    List<String> contents = Lists.newArrayList();
    for (String file : allFiles()) {
      if (new File(file).getParent().equals(dirname)) {
        contents.add(file.replace(dirname + "/", ""));
      }
    }
    return contents;
  }

  @Override
  public FileWriter getFileWriter(String filename, boolean append) throws IOException {
    if (throwIOExceptionOnWrite) throw new IOException("Writing not allowed");
    if (onlyAllowExpectedFiles) {
      TestCase.assertNotNull("Unexpected file written: " + filename,
                             expectedFileContents.get(filename));
    }
    if (!append || !fileWriters.containsKey(filename)) {
      fileWriters.put(filename, new FakeFileWriter(filename));
    }
    return fileWriters.get(filename);
  }

  @Override
  public void touchFile(String filename) throws IOException {
    if (throwIOExceptionOnWrite) throw new IOException("Writing not allowed");
    if (onlyAllowExpectedFiles) {
      TestCase.assertNotNull("Unexpected file written: " + filename,
                             expectedFileContents.get(filename));
    }
    existingPaths.add(filename);
    FakeFileWriter writer = new FakeFileWriter(filename);
    writer.close();
    fileWriters.put(filename, writer);
  }

  @Override
  public void deleteFile(String filename) throws IOException {
    // TODO(matt): This isn't very complete, but I don't have a test that needs this yet.  This
    // should probably do something more interesting, but I'll write it when I need it for a test.
    if (throwIOExceptionOnWrite) throw new IOException("Writing not allowed");
    existingPaths.remove(filename);
  }

  @Override
  public void blockOnFileDeletion(String filename) throws IOException {
  }

  public void addFileToBeRead(String filename, String contents) {
    readerFileContents.put(filename, contents);
  }

  public void addExpectedFileWritten(String filename, String expectedContents) {
    expectedFileContents.put(filename, expectedContents);
  }

  public void expectFilesWritten() {
    for (Map.Entry<String, String> entry : expectedFileContents.entrySet()) {
      if (fileWriters.get(entry.getKey()) == null) {
        TestCase.fail("Expected file not written: " + entry.getKey());
      }
      fileWriters.get(entry.getKey()).expectWritten(entry.getValue());
    }
  }

  public void addExistingFile(String path) {
    existingPaths.add(path);
  }

  public void setDoubleList(List<Double> doubleList) {
    this.doubleList = doubleList;
  }

  /**
   * If getFileWriter gets called with a path that was not given with addExpectedFileWritten, this
   * will check fail.
   */
  public void onlyAllowExpectedFiles() {
    onlyAllowExpectedFiles = true;
  }

  /**
   * Do not check fail if getFileWriter is called on an unexpected path.
   */
  public void allowUnexpectedFiles() {
    onlyAllowExpectedFiles = false;
  }

  public void throwIOExceptionOnWrite() {
    throwIOExceptionOnWrite = true;
  }

  public void unsetThrowIOExceptionOnWrite() {
    throwIOExceptionOnWrite = false;
  }
}
