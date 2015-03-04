---
layout: page
title: Getting Started
---
# Getting Started

This documentation might be sparse, but hopefully will be enough to get you started using the code.
If you have questions that aren't answered, please feel free to ask your questions via the [issue
tracker](https://github.com/matt-gardner/pra/issues), and I will update the documentation to answer
your question.

This documentation is for *version 2.0* of the PRA code.  Earlier versions had a much more
complicated input structure, and I broke backwards compatibility with that structure with the
release of verion 2.0.  The good news is that you shouldn't have anything to lose by updating to
version 2.0.  If you need help converting data or experiment specifications from a previous
version to the new one, just let me know.

## Compiling and running the code

This PRA code was originally written in java, using GraphChi-java as the engine for performing
random walks on a graph.  I have since switched to using scala as my main development language, so
I expect that most new code in this repository will be written in scala, and parts of the codebase
are slowly migrating from java to scala.

Scala uses `sbt` as its main build tool.  You can download `sbt`
[here](http://www.scala-sbt.org/download.html).  Once you have sbt installed and in your shell's
`PATH`, you can run the following commands to clone the repository and run the tests, verifying
that things are working correctly:

```
git clone https://github.com/matt-gardner/pra
cd pra
sbt test
```

If the end output is something like the following, you can be confident that everything is set up
properly:

```
/home/mg1/clone/pra$ sbt test
[info] Loading global plugins from /usr0/home/mg1/.sbt/0.13/plugins
[info] Loading project definition from /usr0/home/mg1/clone/pra/project
[info] Set current project to pra (in build file:/usr0/home/mg1/clone/pra/)
[... a lot of output from the tests ...]
[info] Passed: Total 65, Failed 0, Errors 0, Passed 65
[success] Total time: 2 s, completed Sep 29, 2014 5:11:42 PM
```

If you're getting an error, it's possible there's a bug in the most recent code; you could try
checking out a tagged version (such as v1.1, with `git checkout v1.1`), as tagged versions should
be relatively stable.

If the test succeeds, you can actually run the code using `sbt run`.  There are two different main
methods in the code, however, so this will give you a list of possible main methods (the order you
see may be different, and any given commit might have more or less than what is listed here):

```
/home/mg1/clone/pra$ sbt "run /home/mg1/pra/"
[info] Loading global plugins from /usr0/home/mg1/.sbt/0.13/plugins
[info] Loading project definition from /usr0/home/mg1/clone/pra/project
[info] Set current project to pra (in build file:/usr0/home/mg1/clone/pra/)

Multiple main classes detected, select one to run:

 [1] edu.cmu.ml.rtw.pra.experiments.ExperimentScorer
 [2] edu.cmu.ml.rtw.pra.experiments.ExperimentRunner

Enter number:
```

These `Experiment*` classes are [scala
drivers](https://github.com/matt-gardner/pra/tree/master/src/main/scala/edu/cmu/ml/rtw/pra/experiments)
that [examine a directory structure]({{ site.baseurl }}/input.html) and run whichever experiments
are specified by the files it finds (more on that later).  The base directory it examines is the
first argument passed to the program - hence the `/home/mg1/pra/` argument in the example command
above.  To pass arguments with `sbt`, you need to surround the whole command in quotes (`"run
/home/mg1/pra/"`), or just type `run /home/mg1/pra/` after getting an `sbt` [interactive
console](http://www.scala-sbt.org/0.13/tutorial/Running.html).

## Main Experiment Classes

There are two main experiment classes available, and each is described in its own section below.
In what follows, I will repeatedly refer to `$pra_base`, which is the directory you pass in as the
first argument to each of these classes (`/home/mg1/pra/` in the snippet above).  Each of these
methods also take an optional second parameter that will filter the experiments.  See below for
more details.

### ExperimentRunner

This code will run experiments.  It looks for a directory called `experiment_specs/` under
`$pra_base`, and recursively searches that directory for any files ending in `.json`.  Each such
file specifies an experiment.  When the experiment is run, the results will show up in
`$pra_base/results/`.  Running this method will run all experiments which do not already have a
corresponding directory under `results/` (so if, e.g., there was an error in your experiment and
you need to re-run it, remove the directory in `results/`.  If you want to run an experiment
multiple times to test for variability in the algorithm, duplicate the `.json` file with different
names, like `test_run1.json`, `test_run2.json`, etc.).

The hierarchical structure makes it easy to organize focused experiments.  For example, you might
create a directory called `experiment_specs/tuning/`, with `.json` files `l1_.05,l2_.1.json`,
`l1_.1,l2_.1.json`, or whatever you wish to do.  Then when running the experiments, all of the
results will appear under `$pra_base/results/tuning/`.  You can filter which experiments to run
with the second parameter to this main method, so, e.g., `run /home/mg1/pra/ tuning` would only run
the experiments with `tuning` in their name.

`ExperimentRunner` will attempt to create any inputs that it needs from the specification you give
it.  For example, PRA needs a graph as input, and a graph is made up of some number of relation
sets.  You specify in the `.json` file how you want the graph made, and `ExperimentRunner` will
check to see if it already exists, and create it if not.  And so, as you can imagine, the `.json`
file has a lot of potential options.  For more information on the available options, see the
[experiment spec format]({{ site.baseurl }}/input/experiment_spec.html).  For more information on
the _output_ of `ExperimentRunner`, see the
[results directory contents]({{ site.baseurl }}/output/results.html).

`ExperimentRunner` is designed to allow for several instances running in parallel.  So if you have
some long set of experiment specifications, and you have a big enough machine to handle 4
experiments running at the same time, you can start 4 instances of this process, and things should
just work.  If you run into a problem when trying to do this, it's a bug and you should let me
know.

### ExperimentScorer

After running experiments, this code will look through `$pra_base/results/`, compute metrics for
all of the experiments there, and output a table with results, along with significance tests.  You
can also filter the experiments scored with this, so `run /home/mg1/pra/ tuning` would only show
experiments with `tuning` in their name in the table.  If there is a lot of output for an
experiment, scoring the results can take some time, so this code caches the results in
`$pra_base/results/saved_metrics.tsv`.  This looks at timestamps to see if the results have been
updated, so you shouldn't ever have to mess with the `saved_metrics.tsv` file, but if you're
getting odd errors with `ExperimentScorer`, you might try deleting that file.

I have plans to make the output displayed configurable with commandline options, but it's not done
yet.  If you want some other metric displayed in the table, just edit `sortResultsBy_` and
`displayMetrics_` in
[ExperimentScorer.scala](https://github.com/matt-gardner/pra/blob/master/src/main/scala/edu/cmu/ml/rtw/pra/experiments/ExperimentScorer.scala#L23)

This scorer is also extendable.  You can add a `MetricComputer` relatively easily to compute your
own metric, and call `scoreExperiments` with your customized list of `MetricComputers`.  As an
example of why this is useful, I used this PRA code as the basis for a simple [question answering
system](https://github.com/matt-gardner/qapra), and so I wrote a `MetricComputer` that goes through
the list of questions, evaluates each SVO triple in the question given PRA model output, and gives
an accuracy score on the question set.  The result hooked right into this code as an additional
metric that could be displayed alongside the standard MAP and MRR metrics computed by default.  If
you want to use this functionality and have trouble getting it to work, let me know.  The example
mentioned above can be found
[here](https://github.com/matt-gardner/qapra/blob/master/src/main/scala/org/allenai/qapra/QuestionScorer.scala#L37).
