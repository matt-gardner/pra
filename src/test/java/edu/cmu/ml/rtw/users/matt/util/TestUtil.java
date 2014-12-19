package edu.cmu.ml.rtw.users.matt.util;

import java.util.Arrays;
import java.util.Collection;

import junit.framework.TestCase;

public class TestUtil {

  public static <T> void assertContains(Collection<T> collection, T element) {
    for (T item : collection) {
      if (item.equals(element)) {
        TestCase.assertTrue(true);
        return;
      }
    }
    TestCase.assertTrue("collection does not contain element", false);
  }

  public static <T> void assertCount(Collection<T> collection, T element, int count) {
    int actualCount = 0;
    for (T item : collection) {
      if (item.equals(element)) {
        actualCount++;
      }
    }
    TestCase.assertEquals("Count of " + element + " should be " + count, count, actualCount);
  }

  public static <T extends Throwable> void expectError(Class<T> exceptionType, Function function){
    boolean thrown = false;
    try {
      function.call();
    } catch (Throwable e) {
      if (exceptionType.isInstance(e)) {
        thrown = true;
      } else {
        String message = "Threw the wrong kind of error.  Expected " + exceptionType + ", but saw "
            + e.getClass() + ".";
        e.printStackTrace();
        TestCase.assertTrue(message, false);
      }
    }
    if (!thrown) {
      String message = "Should have thrown an error of type " + exceptionType + ".\n";
      message += "Stack trace: " + Arrays.toString(Thread.currentThread().getStackTrace());
      TestCase.assertTrue(message, false);
    }
  }

  public interface Function {
    public void call();
  }
}
