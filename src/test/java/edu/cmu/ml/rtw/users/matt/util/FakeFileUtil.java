package edu.cmu.ml.rtw.users.matt.util;


import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class FakeFileUtil extends FileUtil {

    private Set<String> existingPaths;
    private List<Double> doubleList;

    public FakeFileUtil() {
        existingPaths = Sets.newHashSet();
        doubleList = Lists.newArrayList();
    }

    @Override
    public boolean fileExists(String path) {
        return existingPaths.contains(path);
    }

    @Override
    public List<Double> readDoubleListFromFile(String filename) {
        return doubleList;
    }

    public void addExistingFile(String path) {
        existingPaths.add(path);
    }

    public void setDoubleList(List<Double> doubleList) {
        this.doubleList = doubleList;
    }
}
