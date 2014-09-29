---
layout: page
title: Getting Started
---
# Getting Started

This documentation might be sparse, but hopefully will be enough to get you
started using the code.  If you have questions that aren't answered, please
feel free to ask your questions via the [issue
tracker](https://github.com/matt-gardner/pra/issues), and I will update the
documentation to answer your question.

## Compiling and running the code

This PRA code was originally written in java, using GraphChi-java as the
engine for performing random walks on a graph.  I have since switched to using
scala as my main development language, so I expect that most new code in this
repository will be written in scala.

Scala uses `sbt` as its main build tool.  You can download `sbt`
[here](http://www.scala-sbt.org/download.html).  Once you have sbt installed
and in your shell's `PATH`, you can run the following commands to clone the
repository and run the tests, verifying that things are working correctly:

```
git clone https://github.com/matt-gardner/pra
cd pra
sbt test
```

If the end output is something like the following, you can be confident that
everything is set up properly:

```
/home/mg1/clone/pra$ sbt test
[info] Loading global plugins from /usr0/home/mg1/.sbt/0.13/plugins
[info] Loading project definition from /usr0/home/mg1/clone/pra/project
[info] Set current project to pra (in build file:/usr0/home/mg1/clone/pra/)
[... a lot of output from the tests ...]
[info] Passed: Total 65, Failed 0, Errors 0, Passed 65
[success] Total time: 2 s, completed Sep 29, 2014 5:11:42 PM
```

If that succeeds (and it does for me), you can actually run the code using
`sbt run`.  There are several different main methods in the code, however, so
this will give you a list of possible main methods (the order you see may be
different):

```
/home/mg1/clone/pra$ sbt "run /home/mg1/pra/"
[info] Loading global plugins from /usr0/home/mg1/.sbt/0.13/plugins
[info] Loading project definition from /usr0/home/mg1/clone/pra/project
[info] Set current project to pra (in build file:/usr0/home/mg1/clone/pra/)

Multiple main classes detected, select one to run:

 [1] edu.cmu.ml.rtw.pra.experiments.ExperimentGraphCreator
 [2] edu.cmu.ml.rtw.pra.experiments.ExperimentScorer
 [3] edu.cmu.ml.rtw.pra.graphs.FreebaseKbFilesCreator
 [4] edu.cmu.ml.rtw.pra.experiments.KbPraDriver
 [5] edu.cmu.ml.rtw.pra.on_demand.OnlinePraPredictor
 [6] edu.cmu.ml.rtw.pra.experiments.ExperimentRunner
 [7] edu.cmu.ml.rtw.pra.graphs.GraphCreator
 [8] edu.cmu.ml.rtw.pra.experiments.PathExplorer
 [9] edu.cmu.ml.rtw.pra.experiments.ExperimentExplorer

Enter number:
```

The main classes you should probably care about are the `Experiment*` classes.
These are [scala
drivers](https://github.com/matt-gardner/pra/tree/master/src/main/scala/edu/cmu/ml/rtw/pra/experiments)
that [examine a directory
structure]({{ site.baseurl }}/input.html) and run whichever
experiments are specified by the files it finds (more on that later).  The
base directory it examines is the first argument passed to the program - hence
the `/home/mg1/pra/` argument in the example command above.  To pass arguments
with `sbt`, you need to surround the whole command in quotes (`"run
/home/mg1/pra/"`), or just type `run /home/mg1/pra/` after getting an `sbt`
[interactive console](http://www.scala-sbt.org/0.13/tutorial/Running.html).

## Main Experiment Classes

There are four main experiment classes available, and each is described in its
own section below.  In what follows, I will repeatedly refer to `$pra_base`,
which is the directory you pass in as the first argument to each of these
classes (`/home/mg1/pra/` in the snippet above).  Each of these methods also
take an optional second parameter that will filter the experiments or graphs.
See below for more details.

### ExperimentGraphCreator

This code looks for a directory called `graph_specs/` under `$pra_base`.
Within that directory (under any number of subdirectories), it looks for files
ending in `.spec`.  Each such file specifies a graph to be created under the
`graphs/` directory in `$pra_base`.  Running this main method will create each
graph that does not already exist.  So, for example, if the file
`$pra_base/graph_specs/testing/test1.spec` exists, we will create a graph
under `$pra_base/graphs/testing/test/`.  There are pages documenting the
[graph spec format]({{ site.baseurl }}/input/graph_spec.html)
and the [graph directory contents]({{ site.baseurl }}/input/graphs.html).

### ExperimentRunner

After creating graphs, this code will run experiments.  It looks for a
directory called `experiment_specs/` under `$pra_base`, and recursively
searches that directory for any files ending in `.spec`.  Each such file
specifies an experiment.  When the experiment is run, the results will show up
in `$pra_base/results/`.  Running this method will run all experiments which
do not already have a corresponding directory under `results/` (so if, e.g.,
there was an error in your experiment and you need to re-run it, remove the
directory in `results/`.  If you want to run an experiment multiple times to
test for variability in the algorithm, duplicate the `.spec` file with
different names, like `test_run1.spec`, `test_run2.spec`, etc.).

The hierarchical structure makes it easy to organize focused experiments.  For
example, you might create a directory called `experiment_specs/tuning/`, with
`.spec` files `l1_.05,l2_.1.spec`, `l1_.1,l2_.1.spec`, or whatever you wish to
do.  Then when running the experiments, all of the results will appear under
`$pra_base/results/tuning/`.  You can filter which experiments to run with the
second parameter to this main method, so, e.g., `run /home/mg1/pra/ tuning`
would only run the experiments with `tuning` in their name.

For some more information, see the [experiment spec format]({{ site.baseurl }}/input/experiment_spec.html)
and the [results directory contents]({{ site.baseurl }}/input/results.html).

### ExperimentScorer

After running experiments, this code will look through `$pra_base/results/`,
compute metrics for all of the experiments there, and output a table with
results.  You can also filter the experiments scored with this, so `run
/home/mg1/pra/ tuning` would only show experiments with `tuning` in their name
in the table.  If there is a lot of output for an experiment, scoring the
results can take some time, so this code caches the results in
`$pra_base/results/saved_metrics.tsv`.  If you need to re-run an experiment and
you've already cached results for it, you'll need to delete them from this
file (or delete the file entirely and regenerate it).

I have plans to make the output displayed configurable with commandline
options, but it's not done yet.  If you want some other metric displayed in the
table, just edit `sortResultsBy_` and `displayMetrics_` in
[ExperimentScorer.scala](https://github.com/matt-gardner/pra/blob/master/src/main/scala/edu/cmu/ml/rtw/pra/experiments/ExperimentScorer.scala#L23)

This scorer is also extendable.  You can add a `MetricComputer` relatively
easily to compute your own metric, and call `scoreExperiments` with your
customized list of `MetricComputers`.  As an example of why this is useful, I
used this PRA code as the basis for a simple question answering system, and so
I wrote a `MetricComputer` that goes through the list of questions, evaluates
each SVO triple in the question given PRA model output, and gives an accuracy
score on the question set.  The result hooked right into this code as an
additional metric that could be displayed alongside the standard MAP and MRR
metrics computed by default.  If you want to use this functionality and have
trouble getting it to work, let me know.  I'll be putting the
question-answering code on github soon, so there will soon be a good example of
how to make this work.

### ExperimentExplorer

This code is still largely experimental.  I wanted to see what paths connected
particular (subject, object) pairs in a set of graphs, so I wrote some code
that will _just_ do the path exploration step and output a kind of feature
matrix.  If you want similar output, try running this and see what you get.
The code behaves similarly to ExperimentRunner, but the output is in
`$pra_base/exploration_results/`.  I'm not going to document this further
unless there's demand for it - let me know if you want to use this but have a
hard time.
