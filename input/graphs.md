---
layout: page
title: Graphs
---
# Graph Specification Parameters

The `graph` entry in the experiment specification can be a path, a name, or an object.  If it is a
path, the code will just use the given as the graph directory (you should only do this if you know
what you're doing).  If it's a name, it will look under the `graphs/` for a directory with that
name, which (likely) was created by the PRA code previously.  If it's an object, the code will
attempt to create the graph, or check that the parameters match if the graph directory already
exists.

These are the parameters that can be specified in the `graph` object:

* `name`: This specifies where the graph will be stored.  The code will look for the graph under
  `graphs/$name`, and create it there if it doesn't already exist.

* `relation sets`: a list of relation sets.  Each relation set is a set of relation triples, and
  the combination of them make up a graph.  Separating them this way allows you to pair different
relation sets in different graphs, so you can, for example, use a set of Freebase triples in
conjunction with a set of SVO triples, or use those same SVO triples in some other graph.  See
below for more information on how to specify relation sets.  Here, each item in the list can be a
`load` statement, a name, or an object.  If it's a name, the code will look for a json file under
`relation_sets/`.

* `deduplicate edges`: either `true` or `false` (or just not present, which defaults to `false`).
  If `true`, graph creation takes slightly more time and memory, but all duplicate edges are
removed from the graph.

And two parameters that you shouldn't have to bother with, as their defaults are what you probably
should use:

* `create matrices`: either `true` or `false`, defaulting to `true`.  This says whether to create
  files that can be used with the matrix multiplication implementation of PRA.  For efficiency
reasons, I store an adjacency matrix for each edge type for use with the matrix multiplication
version of PRA, instead of lumping them all into one file like GraphChi expects.

* `max matrix file size`: when creating the matrices mentioned above, if you have lots of small
  relations it might not be a good idea to have separate files for each one.  So I group them into
files of this many lines.  Default is 100000.

Oh, and one more that's a bit complicated, and that I'm still experimenting with:

* `denser matrices`: this is an attempt to use matrix multiplication to get the same behavior as
  the vector space random walks.  The gist is that you can use relation embeddings to create a
similarity matrix, then use the similarity matrix to densify the KB tensor.  Then, if you use
these denser matrices instead of the original adjacency matrices when computing feature values,
you should get basically the same thing as the vector space walks.  I'm not going to give too much
information about the possibilities here, because I'm still trying to get this right myself, but
if you want to play around with it, I gave an example below.  And feel free to ask me about this
if you're interested.

An example graph specification is as follows:

```
{
  "graph": {
    "name": "nell/kb_svo"
    "relation sets": [
      "nell",
      "svo",
      {
        ... (see below for examples of what to put here)
      }
    ],
    "deduplicate edges": "true",
    "max matrix file size": 10000,
    "denser matrices": [
      {
        "name": "low_threshold_denser_matrix",
        "similarity matrix": {
          "name": "similarity_matrix_0.2_2_1",
          "embeddings": {
            "name": "nell/kb_svo",
            "graph": "nell/kb_svo",
            "dims": 50
          }
          "threshold": 0.2,
          "num_hashes": 2,
          "hash_size": 1
        }
      }
    ]
  }
}
```

Upon running `ExperimentRunner`, any graph specification encountered that does not exist under
`graphs/` will be created.  And it does this in such a way that you should be able to run multiple
instances of `ExperimentRunner`, to run several experiments at a time, and things should just work,
without two processes trying to create the same graph.

And, if you're curious about the `denser matrices` parameter, note that it's not an issue that the
`embeddings` recursively points to the graph being specified.  The graph will get created first,
then the code will run SVD on the graph to create 50-dimensional embeddings, then it will create a
similarity matrix with a low threshold (I use a kind of locality sensitive hash when constructing
this, and that's what the hash parameters are about), then it will create a denser KB tensor using
the similarity matrix, and save slices of the tensor as matrices in a directory with the given
name.  Just try it out with some synthetic data if you want to see more about what this does.

# (Non-synthetic) Relation Set Parameters

A relation set specifies some number of triples to include in the graph, as well as some parameters
associated with them.  The set of NELL relations is a relation set, for instance, as is the set of
NELL-targeted SVO instances used in my experiments, or the set of SVO instances extracted from KBP
documents.  The following parameters are recognized:

* `relation file`: the path to find the relation triples to include in the graph.  The format for
  this file is three tab separated columns (if there are more columns, like a count, they will just
be ignored).  For historical reasons (read: because of how the data I got was formatted when I was
originally writing this code), the order of the columns is _different_ depending on the value of
the `is kb` parameter.  If `is kb` is `true`, the format is `[entity1] [entity2] [relation]`.  If
`is kb` is `false`, the format is `[entity1] [relation] [entity2]`.  I realize this difference is
somewhat insane, and I really should just make the order a parameter you can specify.  But I
haven't done that yet.  Sorry.

* `is kb`: whether or not these relations are KB relations or surface relations.  The difference is
  in how alias edges (edges that connect KB nodes to noun phrase nodes) are treated, and the
relation file format, as described above.

* `alias file`: the path to find the alias edges for the graph (i.e., a mapping between KB nodes
  and noun phrase nodes).  Only applicable if `is kb` is true.

* `alias file format`: Currently must be one of `freebase` or `nell`.  The `nell` format is `[kb
  node] (\t [NP node])+` (i.e., one KB node per line, followed by a tab-separated list of noun
phrases that can refer to the KB node).  The `freebase` format is `[mid] \t [relation] \t
[language] \t [NP]`.  The `[relation]` should be either `/type/object/name` or
`/common/topic/alias`, and currently all languages except `/lang/en` are ignored.

And the parameters below here you probably don't need to worry about, but you can if you want to.

* `relation prefix`: if you want to prepend anything to the relation names (for instance, "KBP_SVO"
  or "OLLIE"), you can do that with this parameter.

* `aliases only`: mostly experimental.  If you want to leave out all KB relation edges, but still
  predict relations between KB nodes, use this.

* `alias relation`: If you want to specify what the name for the alias relation should be for this
  relation set, instead of using the default, you can do that here.  So if you want to distinguish
between NELL and Freebase alias edges, you can do that.  I'm not sure you should, though.

* `embeddings file`: This must be a _mapped_ embeddings file, which maps relations to a symbolic
  representation of cluster labels.  This is for doing the Latent PRA (clustered SVO) that was
described in the EMNLP 2013 paper.  See the examples in `embeddings/*/mapped_embeddings.tsv` from
the [EMNLP data download](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra) for what this file
should look like.  But you probably should leave this unspecified, preferring to use the vector
space walks instead, which are specified elsewhere.

* `keep original edges`: This only matters in conjunction with "embeddings file" - if you're using
  a mapping from surface relations to a latent symbolic representation, this allows you to keep the
original surface relation as well.

A couple of example relation sets follow (recall that these are json-encoded):

```
{
  "relation file": "/path/to/relation/set/file.tsv",
  "is kb": false
}
```

```
{
  "relation file": "/path/to/nell_830/labeled_edges.tsv",
  "is kb": "true",
  "alias file": "/path/to/nell_830/aliases.tsv",
  "alias file format": "nell"
}
```

More examples can be seen in the `examples/` directory in the [PRA
codebase](http://github.com/matt-gardner/pra)

# Synthetic Relation Set Parameters

If you want to _generate_ data to use with PRA, you can do so.  The idea here was to generate data
specifically geared towards various algorithms, to see exactly where methods succeed and fail.
Obviously this is only useful to the extent that the generated data has characteristics that
actually match the data that you care about, but at least we can characterize the performance of
these algorithms in terms of something that's simple to understand.

There are two top-level parameters for specifying a generated dataset.  The first is `type`, and
it must be set to `generated`.  This tells the code to generate the data, instead of looking for a
file containing relation instances.

The following parameters are all required under `generation params`:

* `name`: what to call the data set (and where to find it under `relation_sets/`)

* `num_entities`: how many entities should be in the graph.  The larger this number, the less
  chance there is for overlap among the generated edges, and the easier classification will be.
Basically, more entities means less noise, fewer entities means more noise.

* `num_base_relations`: the number of relations PRA rules can draw from.  Again here, more base
  relations means less potential for confusion, or less noise in the data.

* `num_base_relation_training_duplicates`: these are essentially synonyms of the base relation.
  The more of these you have, the harder time PRA will have, because they are all distinct symbols.
But an approach that uses factorization should (theoretically) be able to tell that these are all
synonyms.  If this is set to 1, PRA has the easiest time (though with enough examples, it does
fine with this set to at least 5; I haven't tried higher yet).

* `num_base_relation_testing_duplicates`: these are synonyms that are only seen between test
  instances of PRA relations.  That is, at training time we draw from the training synonyms, and at
test time we draw from the testing synonyms.  PRA will totally fail in this case, unless it's able
to make use of some kind of factorization to discover that the training and testing relation types
are synonyms.  If this is zero, we use the training relations when generating test instances.

* `num_base_relation_overlapping_instances`: this is what tells the factorization methods that the
  base relation synonyms are indeed synonyms.  We generate this many pairs of edges by picking two
of the base relation synonyms and two entities (each without replacement), and putting both edges
between the entities.

* `num_base_relation_noise_instances`: each noise instance picks a random synonym, then picks two
  random entities, and puts an edge there.  All other generated base relation edges are either
created because there's a PRA instance that caused them, or to establish synonymy.  These
instances weaken both of those, by letting a PRA path potentially follow noisy edges, or by
reducing the percentage of correspondence between synonyms.

* `num_pra_relations`: these are the relations we'll learn models for and test on.  How many
  should we do?  The more there are, the more potential there is for overlap between the edges
they generate, so the noisier the graph will be.  But also, the more there are, the stronger any
statistical results will be (if you care about that on the synthetic data).

* `num_pra_relation_training_instances`: should be obvious.

* `num_pra_relation_testing_instances`: also obvious.

* `num_rules`: to generate the PRA instances we do two things.  First we generate the actual PRA
  relation edge, then we generate some number of other edges that the model should learn to use to
predict new instances of the PRA relations.  We do this explicitly in the way that PRA tries to
find them - by picking some number of "rules" that are predictive of the PRA relations.  Each rule
is a sequence of edge types (called a path in other places; I'm not sure why I used rule here
instead...).  So for each rule we randomly pick a sequence of edge types, then an associated
probability.  When generating a PRA instance, for each rule we sample based on the probability to
see whether the rule applies for this instance, and if so we generate a path from the source to the
target instance using the rule.  The more rules there are, the more potential paths PRA can
leverage to predict new instances.  But, if there is a small number of entities and a lot of rules,
there might be so many paths seen between training instances that it's hard for PRA to find the
right ones.

* `min_rule_length`: the length of each rule is sampled uniformly in the range of integers with
  starting with this parameter...

* `max_rule_length`: ...and ending with this parameter.  The longer the rule length, the harder
  time factorization-based approaches should have predicting the test instances.  So varying this
parameter should demonstrate pretty well where PRA succeeds and factorization-only methods fail.

* `rule_prob_mean`: we draw the rule probability from a guassian with this as the mean...

* `rule_prob_stddev`: ...and this as the standard deviation.

* `num_noise_relations`: these relations just draw entities totally randomly and are never used for
  anything that actually contains information useful for predicting the PRA relations.  If PRA
finds a path with a noise relation, it is totally meaningless.  Adding lots of these can
potentially crowd out PRA's feature space, making it very difficult for PRA to find the correct
path types.  The difference between this and the base relation noise instances is that the base
relations are actually used in the PRA rules, and so those noise instances make each real feature
that PRA is able to use less informative.

* `num_noise_relation_instances`: how many instances of each of the above relations should be
  sampled.

Here's an example parameter specification for a generated relation set:

```
{
  "type": "generated",
  "generation params": {
    "name": "synthetic_hard",
    "num_entities": 1000,
    "num_base_relations": 25,
    "num_base_relation_training_duplicates": 3,
    "num_base_relation_testing_duplicates": 2,
    "num_base_relation_overlapping_instances": 500,
    "num_base_relation_noise_instances": 250,
    "num_pra_relations": 4,
    "num_pra_relation_training_instances": 500,
    "num_pra_relation_testing_instances": 100,
    "num_rules": 10,
    "min_rule_length": 1,
    "max_rule_length": 5,
    "rule_prob_mean": 0.6,
    "rule_prob_stddev": 0.2,
    "num_noise_relations": 20,
    "num_noise_relation_instances": 2500
  }
}
```


# Graph directory contents

This directory is generated when `ExperimentRunner` comes across a graph that does not yet exist.
You should not have to mess with the contents of this directory yourself.  But for your
information, this is what is generally found in these directories.  It contains the graph that's
used by GraphChi, and some files for mapping between the strings used in other parts of the
configuration files and the integers that are used in the graph.  Specifically:

* `graph_chi/`: This directory contains the graph file itself (`edges.tsv`) and some output from
  GraphChi's processing (sharding) of the graph.

* `edge_dict.tsv`: A mapping from relation strings to integers.  If you want to know how many edge
  types there are in the graph, the length of this file will tell you that.

* `node_dict.tsv`: A mapping from node string to integers.  Nodes in the graph correspond to KB
  entities and noun phrases, so if you want to know how many of those there are in the graph, the
length of this file will tell you that.

* `num_shards.tsv`: This specifies how many shards GraphChi should use when processing the graph,
  and affects runtime.  I have some rough heuristics for setting this number in the graph creation
code.

* `matrices/`: A directory containing a list of files with adjacency matrices, for use with the
  matrix multiplication implementation of PRA.
