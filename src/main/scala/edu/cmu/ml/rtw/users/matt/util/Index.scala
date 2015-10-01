package edu.cmu.ml.rtw.users.matt.util

import scala.collection.mutable
import scala.collection.concurrent
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

/**
 * A mapping from some object to integers, for any application where such a mapping is useful
 * (generally because working with integers is much faster and less memory-intensive than working
 * with objects).
 */
class Index[T >: Null](factory: ObjectParser[T], verbose: Boolean = false, fileUtil: FileUtil = new FileUtil) {
  val map = new concurrent.TrieMap[T, Int]
  val reverse_map = new concurrent.TrieMap[Int, T]
  val nextIndex = new AtomicInteger(1)

  /**
   * Test if key is already in the dictionary
   */
  def hasKey(key: T): Boolean = map.contains(key)

  /**
   * Returns the index for key, adding to the dictionary if necessary.
   */
  def getIndex(key: T): Int = {
    if (key == null) {
      throw new RuntimeException("A null key was passed to the dictionary!")
    }
    map.get(key) match {
      case Some(i) => {
        ensureReverseIsPresent(i)
        i
      }
      case None => {
        if (verbose) {
          System.out.println("Key not in index: " + key)
        }
        val new_i = nextIndex.getAndIncrement()
        val i_added_concurrently = map.putIfAbsent(key, new_i)
        i_added_concurrently match {
          case Some(j) => {
            ensureReverseIsPresent(j)
            j
          }
          case None => {
            if (verbose) {
              System.out.println(s"Key added to index at position ${new_i}: ${key}")
              System.out.println(s"next index is ${nextIndex.get()}\n")
            }
            reverse_map.put(new_i, key)
            new_i
          }
        }
      }
    }
  }

  // I don't like this!  But I'm not sure how else to guarantee that this actually works right.  I
  // need the put in the map and the reverse_map to happen atomically, but I don't know how to do
  // that.  So instead, we just take this hit here...
  def ensureReverseIsPresent(i: Int) = while (reverse_map.getOrElse(i, null) == null) Thread.sleep(1)

  def getKey(index: Int): T = {
    val key = reverse_map.getOrElse(index, null)
    if (verbose) println(s"Key for ${index}: ${key}\n")
    key
  }

  def getNextIndex(): Int = nextIndex.get()

  def clear() {
    map.clear()
    reverse_map.clear()
    nextIndex.set(1)
  }

  def writeToFile(filename: String) {
    writeToWriter(fileUtil.getFileWriter(filename))
  }

  def writeToWriter(writer: FileWriter) {
    if (nextIndex.get() > 20000000) {
      // This is approaching the size of something that can't fit in a String object, so we
      // have to write it directly to disk, not use the printToString() method.
      var builder = new StringBuilder()
      for (i <- 1 until nextIndex.get()) {
        if (i % 1000000 == 0) {
          writer.write(builder.toString())
          builder = new StringBuilder()
        }
        builder.append(i)
        builder.append("\t")
        builder.append(getKey(i).toString())
        builder.append("\n")
      }
      writer.write(builder.toString())
    } else {
      writer.write(printToString())
    }
    writer.close()
  }

  def setFromFile(filename: String) {
    setFromReader(fileUtil.getBufferedReader(filename))
  }

  def setFromReader(reader: BufferedReader) {
    map.clear()
    reverse_map.clear()
    var line: String = null
    var max_index = 0
    while ({ line = reader.readLine(); line != null }) {
      val parts = line.split("\t")
      val num = parts(0).toInt
      if (num > max_index) {
        max_index = num
      }
      val key = factory.fromString(parts(1))
      map.put(key, num)
      reverse_map.put(num, key)
    }
    nextIndex.set(max_index+1)
  }

  def printToString(): String = {
    val builder = new StringBuilder()
    for (i <- 1 until nextIndex.get()) {
      builder.append(i)
      builder.append("\t")
      val key = getKey(i)
      if (key == null) {
        builder.append("__@NULL KEY@__")
      } else {
        builder.append(key.toString())
      }
      builder.append("\n")
    }
    return builder.toString()
  }
}
