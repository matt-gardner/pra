---
layout: page
title: Relation Sets
---
# Relation Set File Format

A relation set specifies some number of triples to include in the graph, as
well as some parameters associated with them.  The set of NELL relations is a
relation set, for instance, as is the set of NELL-targeted SVO instances used
in my experiments, or the set of SVO instances extracted from KBP documents.
The following parameters are recognized:

* `relation file`: the path to find the relation triples to include in the
  graph.

* `relation prefix`: if you want to prepend anything to the relation names (for
  instance, "KBP_SVO" or "OLLIE"), you can do that with this parameter.

* `is kb`: whether or not these relations are KB relations or surface
  relations.  The difference is in how alias edges (edges that connect KB nodes
  to noun phrase nodes) are treated.

* `alias file`: the path to find the alias edges for the graph (i.e., a mapping
  between KB nodes and noun phrase nodes).  Only applicable if "is kb" is true.

* `aliases only`: mostly experimental.  If you want to leave out all KB
  relation edges, but still predict relations between KB nodes, use this.

* `alias relation`: If you want to specify what the name for the alias relation
  should be for this relation set, instead of using the default, you can do
  that here.  So if you want to distinguish between NELL and Freebase alias
  edges, you can do that.  I'm not sure you should, though.

* `alias file format`: Currently must be one of "freebase" or "nell".  See the
  example files for what these should look like (or ask me).

* `embeddings file`: This must be a _mapped_ embeddings file, which maps
  relations to a symbolic representation of cluster labels.  This is for doing
  the Latent PRA (clustered SVO) that was described in the EMNLP 2013 paper.
  See the examples in `embeddings/*/mapped_embeddings.tsv` from the [EMNLP data
  download](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra) for what this
  file should look like.  But you probably should leave this unspecified,
  preferring to use the vector space walks instead, which are specified
  elsewhere.

* `keep original edges`: This only matters in conjunction with "embeddings
  file" - if you're using a mapping from surface relations to a latent symbolic
  representation, this allows you to keep the original surface relation as
  well.

A couple of example relation set file follow (note that tabs may not appear
correctly below, but must be used in the relation set file):

```
relation file	/path/to/relation/set/file.tsv
is kb	false
```

```
relation file	/path/to/nell_830/labeled_edges.tsv
is kb	true
alias file	/path/to/nell_830/aliases.tsv
alias file format	nell
```

More examples can be found in the [EMNLP data
download](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra).
