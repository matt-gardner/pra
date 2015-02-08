package edu.cmu.ml.rtw.pra.experiments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;


public class FakeDatasetFactory extends DatasetFactory {

    @Override
    public Dataset fromFile(String path, Dictionary dict) throws IOException {
        return fromReader(null, dict);
    }

    @Override
    public Dataset fromReader(BufferedReader reader, Dictionary dict) throws IOException {
        return Dataset.readFromReader(new BufferedReader(new StringReader("")), dict);
    }
}
