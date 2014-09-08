package edu.cmu.ml.rtw.users.matt.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.util.Pair;

public class FileUtil {

    // Tese logEvery methods fit here, for now, because I only ever use them when I'm parsing
    // through a really long file and want to see progress updates as I go.
    public static void logEvery(int logFrequency, int current) {
        logEvery(logFrequency, current, Integer.toString(current));
    }

    public static void logEvery(int logFrequency, int current, String toLog) {
        if (current % logFrequency == 0) System.out.println(toLog);
    }

    /**
     * Attempts to create the directory dirName, and exits if the directory already exists.
     */
    public static void mkdirOrDie(String dirName) {
        if (!dirName.endsWith("/")) {
            dirName += "/";
        }
        if (new File(dirName).exists()) {
            System.out.println("Out directory already exists! Exiting...");
            System.exit(-1);
        }
        new File(dirName).mkdirs();
    }

    public static List<Pair<String, String>> readStringPairsFromFile(String filename)
            throws IOException {
        return readStringPairsFromReader(new BufferedReader(new FileReader(filename)));
    }

    public static List<Pair<String, String>> readStringPairsFromReader(BufferedReader reader)
            throws IOException {
        List<Pair<String, String>> list = new ArrayList<Pair<String, String>>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split("\t");
            if (fields.length != 2) {
                System.out.println("Offending line: " + line);
                throw new RuntimeException(
                        "readStringPairsFromReader called on file that didn't have two columns");
            }
            list.add(new Pair<String, String>(fields[0], fields[1]));
        }
        return list;
    }

    public static Map<String, String> readMapFromTsvFile(String filename) throws IOException {
        return readMapFromTsvFile(filename, false);
    }

    public static Map<String, String> readMapFromTsvFile(String filename,
                                                         boolean skipErrors) throws IOException {
        return readMapFromTsvReader(new BufferedReader(new FileReader(filename)), skipErrors);
    }

    public static Map<String, String> readMapFromTsvReader(BufferedReader reader,
                                                           boolean skipErrors) throws IOException {
        Map<String, String> map = new HashMap<String, String>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split("\t");
            if (fields.length != 2) {
                if (skipErrors) continue;
                System.out.println("Offending line: " + line);
                throw new RuntimeException(
                        "readMapFromTsvReader called on file that didn't have two columns");
            }
            map.put(fields[0], fields[1]);
        }
        return map;
    }

    public static Map<String, List<String>> readMapListFromTsvFile(String filename)
            throws IOException {
        return readMapListFromTsvReader(new BufferedReader(new FileReader(filename)));
    }

    public static Map<String, List<String>> readMapListFromTsvFile(String filename,
                                                                   int keyIndex,
                                                                   boolean overwrite,
                                                                   LineFilter filter)
            throws IOException {
        return readMapListFromTsvReader(new BufferedReader(new FileReader(filename)),
                                        keyIndex,
                                        overwrite,
                                        filter);
    }

    public static Map<String, List<String>> readMapListFromTsvReader(BufferedReader reader)
            throws IOException {
        return readMapListFromTsvReader(reader, 0, false, null);
    }

    /**
     * Reads a tab-separated file and puts the contents into a map.
     *
     * We give a few options:
     * - You can set the index for the key to the map.  If the key is not zero, we only add the
     *   first column as a value to this map (and so setting overwrite to true in this case doesn't
     *   make a whole lot of sense - just use readMapFromTsv instead).
     * - If overwrite is true, we don't bother checking to see if the key is already in the map.
     *   This will speed up the processing if you know that your file only has one line per unique
     *   key.
     * - You can provide a LineFilter object that wlil be called with each line to determine if it
     *   should be skipped.
     */
    public static Map<String, List<String>> readMapListFromTsvReader(BufferedReader reader,
                                                                     int keyIndex,
                                                                     boolean overwrite,
                                                                     LineFilter filter)
            throws IOException {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split("\t");
            if (filter != null && filter.filter(fields)) continue;
            String key = fields[keyIndex];
            List<String> list;
            if (overwrite) {
                list = new ArrayList<String>();
                map.put(key, list);
            } else {
                list = map.get(key);
                if (list == null) {
                    list = new ArrayList<String>();
                    map.put(key, list);
                }
            }
            if (keyIndex == 0) {
                for (int i = 1; i < fields.length; i++) {
                    list.add(fields[i]);
                }
            } else {
                list.add(fields[0]);
            }
        }
        return map;
    }

    public static Map<String, List<String>> readInvertedMapListFromTsvFile(String filename)
            throws IOException {
        return readInvertedMapListFromTsvReader(new BufferedReader(new FileReader(filename)));
    }

    /**
     * Assuming the file is formatted as (key, value, value, ...), read an _inverted_ map from the
     * file.  That is, take all of the values, and make them keys, with the original key as a
     * values.
     */
    public static Map<String, List<String>> readInvertedMapListFromTsvReader(BufferedReader reader)
            throws IOException {
        Map<String, List<String>> map = Maps.newHashMap();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split("\t");
            String key = fields[0];
            for (int i = 1; i < fields.length; i++) {
                String value = fields[i];
                // Remember, this is an _inverted_ map, so switching the value and key is correct.
                MapUtil.addValueToKeyList(map, value, key);
            }
        }
        return map;
    }

    public static Set<Integer> readIntegerSetFromFile(String filename) throws IOException {
        return readIntegerSetFromFile(filename, null);
    }

    /**
     * The file is assumed to be a series of lines, one string per line.  If the provided
     * dictionary is null, we parse the string to an integer before adding it to a set.  Otherwise,
     * we look up the string in the dictionary to convert the string to an integer.
     */
    public static Set<Integer> readIntegerSetFromFile(String filename,
                                                      Dictionary dict) throws IOException {
        try {
            Set<Integer> integers = new HashSet<Integer>();
            BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (dict == null) {
                        integers.add(Integer.parseInt(line));
                    } else {
                        integers.add(dict.getIndex(line));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("line=" + line, e);
                }
            }
            reader.close();
            return integers;
        } catch (Exception e) {
            throw new RuntimeException("readIntegerSetFromFile(\"" + filename + "\", <dict>)", e);
        }
    }

    public static List<Integer> readIntegerListFromFile(String filename) throws IOException {
        return readIntegerListFromFile(filename, null);
    }

    /**
     * The file is assumed to be a series of lines, one string per line.  If the provided
     * dictionary is null, we parse the string to an integer before adding it to a list.  Otherwise,
     * we look up the string in the dictionary to convert the string to an integer.
     */
    public static List<Integer> readIntegerListFromFile(String filename,
                                                      Dictionary dict) throws IOException {
        List<Integer> integers = Lists.newArrayList();
        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        String line;
        while ((line = reader.readLine()) != null) {
            if (dict == null) {
                integers.add(Integer.parseInt(line));
            } else {
                integers.add(dict.getIndex(line));
            }
        }
        reader.close();
        return integers;
    }

    public List<Double> readDoubleListFromFile(String filename) throws IOException {
        List<Double> doubles = Lists.newArrayList();
        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        String line;
        while ((line = reader.readLine()) != null) {
            doubles.add(Double.parseDouble(line));
        }
        reader.close();
        return doubles;
    }

    public interface LineFilter {
        public boolean filter(String[] fields);
    }

    public boolean fileExists(String path) {
        return new File(path).exists();
    }

    /**
     * Copies the lines in reader to writer.  Does not close writer.
     */
    public void copyLines(BufferedReader reader, FileWriter writer) throws IOException {
      String line;
      while ((line = reader.readLine()) != null) {
        writer.write(line + "\n");
      }
    }
}
