---
layout: page
title: Split directory
---
# Split Directory

A split directory has three types of things:

* `relations_to_run.tsv` (required): this is a list of relations to run PRA on.
  KbPraDriver will train and test each relation in this file, one at a time.

* `[relation]/`: For each relation in `relations_to_run.tsv`, the code will
  check for a directory with the same name as the relation.  If that directory
exists, it will look for `training.tsv` and `testing.tsv`, which contain the
node name pairs for the relation.  See below or look in the splits/ directory
of the EMNLP 2014 data download for some examples of the format required for
these files.

* `percent_training.tsv`: As an alternative to specifying a training/testing
  split, you can specify how much of the data to use as training, and run cross
validation.  If you want to compare methods, this is probably not a good idea,
unless you want to do several runs of cross validation for each method, and
report the difference between average results.  The only use I've made of this
so far is to specify 100% training, for the knowledge on demand models.  That
saves you from having to create directories with training.tsv and testing.tsv
files for each relation, because the code will look in the kb_files/relations/
directory to get training and testing instances.

# Data files

Data files (like the `training.tsv` and `testing.tsv` files mentioned above)
can have two main formats: a two column format where all instances in the file
are assumed positive, or a three column format where the third column says
whether the instance is positive or negative (a 1 in the third column
indicates a positive example, a -1 indicates a negative example).

Two column format example:

    source_node_1 [tab] target_node_1
    source_node_2 [tab] target_node_2
    ...           [tab] ...

Three column format example:

    positive source node [tab] positive target node [tab] 1
    negative source node [tab] negative target node [tab] -1
    ...                  [tab] ...                  [tab] {1|-1}

Supplying negative instances as training examples has some important
implications for how some of the rest of the code runs - the default
parameters I recommend assume that only positive examples are given.  If you
want to supply negative evidence, read the documentation for the `matrix accept
policy` parameter [here]({{ site.baseurl }}/input/param_file.html).

The code for reading in a data file is found in
`experiments.Dataset.readFromReader()` (and a few other related methods, but
that's the one that's called by the main code path).  If you have some problem
with how your data file is being read, that's where you should look first for
potential problems.
