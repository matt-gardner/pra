package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;

public class FakePathTypeFactory implements PathTypeFactory {

    public PathType[] pathTypes;

    public FakePathTypeFactory() {
        pathTypes = new PathType[] {
            new FakePathType("fake path type 1"),
            new FakePathType("fake path type 2")};
    }

    @Override
    public PathType[] encode(Path path) {
        return pathTypes;
    }

    @Override
    public PathType fromString(String string) {
        return new FakePathType(string);
    }

    @Override
    public PathType emptyPathType() {
        return new FakePathType("NULL");
    }

    @Override
    public PathType concatenatePathTypes(PathType pathToSource, PathType pathFromTarget) {
        return new FakePathType(pathToSource.encodeAsString() + pathFromTarget.encodeAsString());
    }

    @Override
    public PathType collapseEdgeInverses(PathType pathType, Map<Integer, Integer> edgeInverses) {
        String description = pathType.encodeAsString();
        if (description.contains("INVERSE")) return pathType;
        return new FakePathType(description + " INVERSE");
    }

    public static class FakePathType implements PathType {

        private String description;
        private int numHops = 3;
        private int nextVertex;

        public FakePathType(String description) {
            this.description = description;
            nextVertex = 0;
        }

        public void setNextVertex(int nextVertex) {
            this.nextVertex = nextVertex;
        }

        @Override
        public int recommendedIters() {
            return numHops;
        }

        @Override
        public boolean isLastHop(int hopNum) {
            return hopNum == numHops - 1;
        }

        @Override
        public String encodeAsString() {
            return description;
        }

        @Override
        public String encodeAsHumanReadableString(Dictionary edgeDict, Dictionary nodeDict) {
            return description;
        }

        @Override
        public PathTypeVertexCache cacheVertexInformation(Vertex vertex, int hopNum) {
          return null;
        }

        @Override
        public int nextHop(int hopNum,
                           int sourceVertex,
                           Vertex vertex,
                           Random random,
                           EdgeExcluder edgeExcluder,
                           PathTypeVertexCache cache) {
            return nextVertex;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FakePathType other = (FakePathType) obj;
            if (numHops != other.numHops)
                return false;
            if (!description.equals(other.description))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + numHops;
            result = prime * result + description.hashCode();
            return result;
        }
    }
}
