package edu.cmu.ml.rtw.pra.experiments;

import java.io.BufferedReader;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;


public class FakeDatasetFactory extends DatasetFactory {

    @Override
    public Dataset fromFile(String path, Dictionary dict) {
        return null;
    }

    @Override
    public Dataset fromReader(BufferedReader reader, Dictionary dict) {
        return null;
    }
}
