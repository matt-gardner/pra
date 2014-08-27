package edu.cmu.ml.rtw.pra.features;

import java.util.Map;
import java.util.Random;

import com.google.common.annotations.VisibleForTesting;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.Vector;

/**
 * Represents a path type as a sequence of (edge, vector) pairs.  We assume that each edge type has
 * a vector representation that captures some amount of its semantic meaning.  The vector is used
 * during feature computation to allow walks across semantically similar edge types, as defined by
 * an exponentiated dot product.
 */
public class VectorPathTypeFactory extends BaseEdgeSequencePathTypeFactory {

    private final VectorPathType emptyPathType = new VectorPathType();
    // We're wasting a little bit of memory by making this an array instead of a map, but we're
    // gaining in computation time, by avoiding autoboxing and hashing.  Also, I expect that the
    // array will be mostly full, in most uses of this PathTypeFactory.  It's just KB edges that
    // don't have vectors, generally, and the number of relations types is dominated by surface
    // edges, not KB edges.
    private Vector[] embeddings;
    private final double resetWeight;
    private final double spikiness;
    // We keep these two around so that we can reinitialize the embeddings vector if things change.
    // This costs us some memory, but it should be pretty negligible, as these are just references.
    private final Dictionary edgeDict;
    private final Map<Integer, Vector> embeddingsMap;

    public VectorPathTypeFactory(Dictionary edgeDict,
                                 Map<Integer, Vector> embeddingsMap,
                                 double spikiness,
                                 double resetWeight) {
        this.spikiness = spikiness;
        this.resetWeight = Math.exp(spikiness * resetWeight);
        this.edgeDict = edgeDict;
        this.embeddingsMap = embeddingsMap;
        initializeEmbeddings();
    }

    public Dictionary getEdgeDict() {
        return edgeDict;
    }

    public Map<Integer, Vector> getEmbeddingsMap() {
        return embeddingsMap;
    }

    public void initializeEmbeddings() {
        int maxIndex = edgeDict.getNextIndex();
        embeddings = new Vector[maxIndex + 1];
        for (Map.Entry<Integer, Vector> entry : embeddingsMap.entrySet()) {
            embeddings[entry.getKey()] = entry.getValue();
        }
    }

    @Override
    protected BaseEdgeSequencePathType newInstance(int[] edgeTypes, boolean[] reverse) {
        return new VectorPathType(edgeTypes, reverse);
    }

    @Override
    public PathType emptyPathType() {
        return emptyPathType;
    }

    public Vector getEmbedding(int edgeType) {
        return embeddings[edgeType];
    }

    @VisibleForTesting
    protected class VectorPathType extends BaseEdgeSequencePathType {

        public VectorPathType(int[] edgeTypes, boolean[] reverse) {
            super(edgeTypes, reverse);
        }

        private VectorPathType() {
            super(new int[0], new boolean[0]);
        }

        protected int[] getEdgeTypes() {
            return edgeTypes;
        }

        protected boolean[] getReverse() {
            return reverse;
        }

        @Override
        protected int getNextEdgeType(int hopNum, Vertex vertex, Random random) {
            Vector baseVector = embeddings[edgeTypes[hopNum]];
            // If we have no embeddings for the edge type corresponding to this hop num (for
            // instance, if it is the "alias" relation), then just return the edge type itself.
            if (baseVector == null) {
                return edgeTypes[hopNum];
            }
            int[] vertexEdgeTypes;
            if (reverse[hopNum]) {
                vertexEdgeTypes = vertex.getInEdgeTypes();
            } else {
                vertexEdgeTypes = vertex.getOutEdgeTypes();
            }
            // If there's only one possible edge type, and it matches the edge type we're supposed
            // to be walking on, don't bother messing with the reset probability, just take it.
            if (vertexEdgeTypes.length == 1 && vertexEdgeTypes[0] == edgeTypes[hopNum]) {
                return edgeTypes[hopNum];
            }
            double[] weights = new double[vertexEdgeTypes.length];
            double totalWeight = 0.0;
            for (int i = 0; i < vertexEdgeTypes.length; i++) {
                Vector edgeVector = embeddings[vertexEdgeTypes[i]];
                if (edgeVector == null) {
                    weights[i] = 0.0;
                    continue;
                }
                double dotProduct = baseVector.dotProduct(edgeVector);
                double weight = Math.exp(spikiness * dotProduct);
                weights[i] = weight;
                totalWeight += weight;
            }

            totalWeight += resetWeight;
            double randomWeight = random.nextDouble() * totalWeight;
            for (int i = 0; i < weights.length; i++) {
                if (randomWeight < weights[i]) {
                    return vertexEdgeTypes[i];
                }
                randomWeight -= weights[i];
            }
            // This corresponds to the reset weight that we added, in case all of the individual
            // weights were very low.  So return -1 to indicate that we should just reset the walk.
            return -1;
        }
    }
}
