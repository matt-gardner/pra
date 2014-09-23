package edu.cmu.ml.rtw.users.matt.util;

import junit.framework.TestCase;

public class IndexTest extends TestCase {

    public void testGetIndexInserts() {
      Index<String> index = new Index<String>(new StringParser());
      assertEquals(1, index.getIndex("string 1"));
      assertEquals(2, index.getIndex("string 2"));
      assertEquals(3, index.getIndex("string 3"));
      assertEquals(4, index.getIndex("string 4"));
      assertEquals(1, index.getIndex("string 1"));
      assertEquals(2, index.getIndex("string 2"));
      assertEquals(3, index.getIndex("string 3"));
      assertEquals(4, index.getIndex("string 4"));
      assertEquals("string 1", index.getKey(1));
      assertEquals("string 2", index.getKey(2));
      assertEquals("string 3", index.getKey(3));
      assertEquals("string 4", index.getKey(4));
    }

    public void testClearActuallyClears() {
      Index<String> index = new Index<String>(new StringParser());
      assertEquals(1, index.getIndex("string 1"));
      index.clear();
      assertEquals(1, index.getIndex("string 2"));
    }
}
