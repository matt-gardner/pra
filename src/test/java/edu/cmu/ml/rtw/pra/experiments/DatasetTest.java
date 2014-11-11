package edu.cmu.ml.rtw.pra.experiments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import junit.framework.TestCase;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.TestUtil;
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function;

public class DatasetTest extends TestCase {
  private String datasetFile =
      "node1\tnode2\t1\n" +
      "node1\tnode3\t-1\n";

  private String badDatasetFile = datasetFile +
      "node1\tnode4\tnode5\n";

  public void testFromReaderCrashesOnBadThirdColumn() throws IOException {
    TestUtil.expectError(RuntimeException.class, new Function() {
      @Override
      public void call() {
        try {
          Dataset d = Dataset.readFromReader(new BufferedReader(new StringReader(badDatasetFile)),
                                             new Dictionary());
        } catch (IOException e) {
        }
      }
    });
  }
}
