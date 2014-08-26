package edu.cmu.ml.rtw.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mapping from strings to integers, for more compact representation of
 * features and mentions.
 *
 * Every string that is read into the sampler needs to go through the
 * dictionary and be converted into an int.
 */
public class Dictionary {
    private ConcurrentHashMap<String, Integer> map;
    private ConcurrentHashMap<Integer, String> reverse_map;
    private AtomicInteger nextIndex;
    private boolean verbose;

    public Dictionary() {
        this(false);
    }

    public Dictionary(boolean verbose) {
        map = new ConcurrentHashMap<String, Integer>();
        reverse_map = new ConcurrentHashMap<Integer, String>();
        nextIndex = new AtomicInteger(1);
        this.verbose = verbose;
    }

    /**
     * Test if string is already in the dictionary
     */
    public boolean hasString(String string) {
        return map.containsKey(string);
    }

    /**
     * Returns the index for string, adding to the dictionary if necessary.
     */
    public int getIndex(String string) {
        if (string == null) {
            throw new RuntimeException("A null string was passed to the " +
                    "dictionary!");
        }
        Integer i = map.get(string);
        if (i == null) {
            if (verbose) {
                System.out.println("String not in index: " + string);
            }
            Integer new_i = nextIndex.getAndIncrement();
            i = map.putIfAbsent(string, new_i);
            if (i == null) {
                if (verbose) {
                    System.out.println("String added to index at position " + new_i + ": "
                            + string + "; next index is " + nextIndex.get());
                }
                reverse_map.put(new_i, string);
                return new_i;
            }
        }
        return i;
    }

    public String getString(int index) {
        return reverse_map.get(index);
    }

    public int getNextIndex() {
        return nextIndex.get();
    }

    public void writeToFile(File outfile) throws IOException {
        writeToWriter(new FileWriter(outfile));
    }

    public void writeToWriter(FileWriter writer)
            throws IOException {
        writer.write(printToString());
        writer.close();
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
        map.clear();
        reverse_map.clear();
        String line;
        try {
            int max_index = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                int num = Integer.parseInt(parts[0]);
                if (num > max_index) {
                    max_index = num;
                }
                map.put(parts[1], num);
                reverse_map.put(num, parts[1]);
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
            builder.append(getString(i));
            builder.append("\n");
        }
        return builder.toString();
    }
}
