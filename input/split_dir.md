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
