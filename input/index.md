---
layout: page
title: Input Files
---
# Input File Specifications

This documentation might be sparse, but hopefully will be enough to get you
started using the code.  If you have questions that aren't answered, please
feel free to ask your questions via the issue tracker, and I will update the
documentation to answer your question.

## Compiling and running the code

This code uses maven to manage its dependencies. You will need to have maven
installed on your system in order to compile this code correctly. The following
commands will clone the repository, compile the code, and run the tests:

```
git clone https://github.com/matt-gardner/pra
cd pra
mvn test
```

If that succeeds (and it does for me), you can run the code with KbPraDriver as
your main class (as is recommended) with the following command (should all be
on one line in your shell):

```
mvn exec:java
-Dexec.mainClass="edu.cmu.ml.rtw.pra.experiments.KbPraDriver"
-Dexec.args="[args]"
```

The most likely use of the code by people who might read this page is in the
class `experiments.KbPraDriver`, though `experiments.PraDriver` may also be
useful if you want to use the PRA code in a context that is not KB relation
inference.

`KbPraDriver` takes 5 arguments, each of which is described in more detail in
its own section below:

* `-k` or `--kb-files`: A directory containing a set of KB files in a
  particular format needed for PRA.

* `-g` or `--graph-files`: A directory containing a set of graph files.

* `-s` or `--split`: A directory defining the training and testing split to use
  for PRA.

* `-p` or `--param-file`: A file that specifies certain parameters for PRA
  (like, how many walks to do, whether or not to use vector space walks, L1 and
  L2 weight, etc.)

* `-o` or `--outdir`: Where to put the results.

You can call `KbPraDriver.main` from the command line using these arguments, or
you can call `KbPraDriver.runPra` from some other java code (that perhaps runs
several experiments, for example).

Where I mention examples below, they probably all refer to the EMNLP 2014 data
download found [here](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra/).  I
copied this documentation from another place, and there may be old paths lying
around - just look in the data download for the examples.

## Arguments to KbPraDriver

### KB Files

This is a directory that contains information about a KB.  It generally has the
following entries:

* `category_instances/`: A directory containing all of the instances of each
  category, one category per file.  This is necessary for restricting the
  predictions that PRA makes - the category_instances/ file has all acceptable
  target nodes for each relation.

* `domains.tsv`: A mapping from relations to the category corresponding to the
  relation's domain.  Currently unused.

* `ranges.tsv`: A mapping from relations to the category corresponding to the
  relation's range.  Used to restrict PRA's predictions.

* `inverses.tsv`: A mapping from relations to their inverses.  This is used for
  restricting the random walks.  When training a PRA classifier to predict a
  relation, you need to "remove" the edges that correspond to the training
  data, so you don't learn things that won't be useful, and edges that
  correspond to the testing data, so you're not cheating.  If there are known
  inverses in the graph, we need to remove both the original edge and its
  inverse, when applicable.

* `labeled_edges.tsv`: Contains all relation instances in the KB, each of which
  will correspond to a single edge in a PRA graph.  This file is generally
  referenced in a relation set, when creating graphs, as described below.

* `relations/`: A directory containing all of the instances of each relation,
  one relation per file.  This is used to simplify the process of cross
  validation, when a fixed training/testing set isn't specified in a split
  directory (see below).

If you are using Freebase, you shouldn't have to create these directories
manually.  There is a class in the PRA code base,
graphs.FreebaseKbFilesCreator, that does this for you.  You could also download
the data files from my EMNLP 2014 paper, as it contains KB files for NELL and
Freebase.  (I didn't include the NellKbFilesCreator in this repository, because
it has too many dependencies on the NELL codebase - I would have had to copy
too many files over.  If you really want to create new KB files for a newer
version of NELL than you can get from the data files in the EMNLP 2014 paper,
talk to me.)

### Graph Files

This directory contains the graph that's used by GraphChi, and some files for
mapping between the strings used in other parts of the configuration files and
the integers that are used in the graph.  Specifically:

* `graph_chi/`: This directory contains the graph file, and (after running the
  PRA code at least once) some output from GraphChi's processing (sharding) of
  the graph.

* `edge_dict.tsv`: A mapping from relation strings to integers.  If you want to
  know how many edge types there are in the graph, the length of this file will
  tell you that.

* `node_dict.tsv`: A mapping from node string to integers.  Nodes in the graph
  correspond to KB entities and noun phrases, so if you want to know how many
  of those there are in the graph, the length of this file will tell you that.

* `num_shards.tsv`: This specifies how many shards GraphChi should use when
  processing the graph, and affects runtime.  I have some rough heuristics for
  setting this number in the graph creation code.

The class graphs.GraphCreator creates this directory given some number of
_relation sets_.  A relation set specifies some number of triples to include in
the graph, as well as some parameters associated with them.  The set of NELL
relations is a relation set, for instance, as is the set of NELL-targeted SVO
instances used in my experiments, or the set of SVO instances extracted from
KBP documents.  You can see examples of relation set specifications in the
EMNLP 2014 data download.  The following parameters are recognized:

* `relation file`: the path to find the relation triples to include in the
  graph.

* `relation prefix`: if you want to prepend anything to the relation names (for
  instance, "KBP_SVO" or "OLLIE_"), you can do that with this parameter.

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
  See the examples in embeddings/*/mapped_embeddings.tsv for what this file
  should look like.  But you probably should leave this unspecified, preferring
  to use the vector space walks instead, which are specified elsewhere.

* `keep original edges`: This only matters in conjunction with "embeddings
  file" - if you're using a mapping from surface relations to a latent symbolic
  representation, this allows you to keep the original surface relation as
  well.

Once you have created some relation set specification files, you can use
graphs.GraphCreator to create the graph.  Usage would be roughly as follows
(possibly using `mvn exec:java` instead of `java`, for example):

`java edu.cmu.ml.rtw.pra.graphs.GraphCreator [outdir] [relation_set_spec]+`

### Split Directory

A split directory has three types of things:

* `relations_to_run.tsv` (required): this is a list of relations to run PRA on.
  KbPraDriver will train and test each relation in this file, one at a time.

* `[relation]/`: For each relation in relations_to_run.tsv, the code will check
  for a directory with the same name as the relation.  If that directory
  exists, it will look for training.tsv and testing.tsv, which contain the node
  name pairs for the relation.  Look in the splits/ directory of the EMNLP 2014
  data download for some examples.

* `percent_training.tsv`: As an alternative to specifying a training/testing
  split, you can specify how much of the data to use as training, and run cross
  validation.  If you want to compare methods, this is probably not a good
  idea, unless you want to do several runs of cross validation for each method,
  and report the difference between average results.  The only use I've made of
  this so far is to specify 100% training, for the knowledge on demand models.
  That saves you from having to create directories with training.tsv and
  testing.tsv files for each relation, because the code will look in the
  kb_files/relations/ directory to get training and testing instances.

### Parameter File

A parameter file specifies things like how many random walks to do, L2 and L1
weight, and other things.  The file should be tab separated, one parameter per
line.  See param_files/ for examples.

Available parameters:

* `L1 weight`

* `L2 weight`

* `walks per source`: during the path finding step, how many walks should we do
  from each training node?

* `walks per path`: during the path following step, how many walks should we do
  per (source node, path type) pair?

* `path finding iterations`

* `number of paths to keep`

* `only explicit negative evidence`: normally, we make use of (source, target)
  pairs that are found during the path following step as negative evidence, if
  they aren't given explicitly as positive examples (this corresponds to a
  closed world assumption over our training data).  Alternatively, you could
  specify negative examples explicitly in training, and then not use the
  unlabeled instances as negative.  If you want to know how to make this work
  right, ask me for help.

* `matrix accept policy`: related to the above parameter.  It determines which
  (source, target) pairs should be kept when computing a feature matrix.  Don't
  worry about it too much, just use "all-targets", as in the example files.

* `path accept policy`: also don't worry about this one too much, just use
  "paired-only".

* `path type embeddings`: this is how you specify that you want to use vector
  space random walks (my most recent, and best performing, method).  Specifying
  the parameter is a little cludgy, but it should look like this:
  "[spikiness],[reset prob],[path to embeddings]".  Good values for spikiness
  are around 3, and good values for reset prob are anywhere from .75 - .25 (it
  didn't seem to make a lot of difference).  Again, see the example files in
  param_files/vector*params.tsv

* `path type selector`: this was experimental, and it didn't turn out to help
  at all, so you don't need to worry about it.  I'm just putting it here for
  completeness.

You can see the default values I use for these in the example param files that
I've already created.

### Output directory

This is the directory containing the results.  The code will check to make sure
this directory does not exist before trying to run anything, and
scripts/run_tests.py (in the data download) uses the existence of the directory
to know which experiments still need to be run.

Inside the main results directory, there is a settings.tsv file that basically
lists all of the arguments and parameters that were used to run the code, so
hopefully if you lose track of which output was in which directory, this file
will help you figure it out.

Beyond that, there is one directory per relation tested.  Inside that relation
directory are files giving what paths were found, what paths were kept, what
the learned path weights were, what the resultant feature matrices were that
were used for training and testing, and what the final predictions were during
testing.

The most important file for you to know about is probably the scores.tsv file.
This file has all of the scores for each (source, target) pair found.  Each
source is listed in its own line chunk, with all found targets listed in the
order they were predicted, with a corresponding score.  There's a * in the last
column if the item was a known test example, and *^ if the item was a known
training example.

There are also some scripts I have written to make analysis of the results a
little easier.  See scripts/ in the EMNLP 2014 results download.  The important
ones are process_results.py and analyze_results.py.  process_results.py goes
through the results directories and computes some statistics for each relation
(like average precision, reciprocal rank, and a bunch of other things) and
stores them in a file (and they are timestamped, so it only processes newly run
results).  analyze_results.py then takes the file and computes MAP, MRR, and
some other stuff, and lets you sort by various metrics or only look at a few
relations.  Pass -h into analyze_results.py to see what options are available.

Note also that the 'AP' and 'RR' metrics as output by analyze_results.py are
actually MAP and MRR, and 'MAP' and 'MRR' are something like MMAP and MMRR.
The 'MAP' and 'MRR' correspond to computing the average precision and
reciprocal rank for each _test source node_, instead of for the relation as a
whole.  So it's a mean average precision over source nodes, whereas 'AP' as
output by analyze_results.py is a mean average precision over relations.  'MAP'
and 'MRR' have a lot of variance, because I ignore any source node that had no
predictions.  In my opinion, the most useful metric to look at when comparing
performance is 'AP', which is really mean average precision.

## Other Notes

### Embeddings for vector space walks

To get vector space walks, you need to get a vector embedding of your relations
from somewhere (the EMNLP 2013 and 2014 papers used PCA), then put them into
the embeddings/ folder and reference them in a parameter file that you use (see
the parameter file description).  The format of the embeddings file should be
one relation per line, with the first column being the relation name, then
every other (tab separated) column being an element in the PCA vector.  See the
embeddings.tsv files under embeddings/ in the data download for a lot of
examples of this.

### Some python scripts

I used some python scripts to make running experiments easier for the EMNLP
papers.  I didn't include them in this repository, as I would like to translate
them to java or scala before doing that.  But if you want to use them, you can
find them in the scripts/ directory in the EMNLP 2014 data download.  They will
need some path modifications to be useful, and I'm not sure they'll work right
since I've switched to using maven.  But, if you want to try using them, in the
scripts directory, there are two python scripts for creating the graph files
and for running experiments (create_graphs.py and run_tests.py).  In
create_graphs.py, you specify which relation sets should be used in which
graphs, and the code will check to see if the graph already exists, and create
it if not.  In run_tests.py, you specify the five arguments listed above, and
it will check if the out directory already exists, and run the experiment if
not.

I don't intend to offer support for using those scripts.
