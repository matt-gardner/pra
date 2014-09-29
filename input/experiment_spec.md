---
layout: page
title: Experiment Spec
---
# Experiment Spec File Format

An experiment specification file goes in the directory `experiment_specs/`,
and must end with `.spec`.  This file specifies four parameters that define an
experiment:

* A path to the [KB files]({{ site.baseurl }}/input/kb_files.html) to use for
  the experiment.  This directory defines relation metadata, things like
  domains, ranges, inverses, and such for each relation you want to test.

* A path to the [graph]({{ site.baseurl }}/input/graphs.html) to be used in the
  experiment.

* A path to the [split directory]({{ site.baseurl }}/input/split_dir.html) to
  use for the experiment.  This tells the PRA code which node pair to use as
  training and testing data.

* A path to the [parameter file]({{ site.baseurl }}/input/param_file.html) for
  this experiment.

The experiment spec file should have four lines, one of these parameters per
line, with a tab separating the parameter name from the path.  An example
experiment spec file is as follows (note that tabs may not appear correctly
below, but must be used in the spec file):

```
kb files	/home/mg1/pra/kb_files/nell/
graph files	/home/mg1/pra/graphs/nell_svo/
split	/home/mg1/pra/splits/nell/
param file	/home/mg1/pra/param_files/nell_params.tsv
```

The name of the file (including any subdirectories under `experiment_specs/`)
defines the [result directory]({{ site.baseurl }}/input/results.html) where the
output will be put.
