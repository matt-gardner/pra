package edu.cmu.ml.rtw.pra.features;

/**
 * Allows for some computation to be done for each vertex, instead of for each walk, if the
 * computation only depends on the vertex.  Classes that implement this interface will define their
 * own data structures and methods, and should cast to the subtype in the methods that use the
 * cache.
 *
 * One example of this is computing the distribution over edge types for a vector space walk - it
 * depends on the path type and the edges at the vertex, but not on the individual walk.
 */
public interface PathTypeVertexCache { }
