---
layout: page
title: Experiment Spec
---
# Experiment Spec File Format

An experiment specification file goes in the directory `experiment_specs/`, and must end with
`.json`.  As you could probably guess from the required extension, this file should be formatted as
a json object (with an extension or two; see below).  This file specifies all of the parameters
that define a PRA experiment, grouped into four chunks:

* The [graph]({{ site.baseurl }}/input/graphs.html) to be used in the experiment.  There are a lot
  of possible parameters to put here.  See the [graph page]({{ site.baseurl }}/input/graphs.html)
for more information.  The name of this set of parameters in the json object must be `graph`.

* [Relation metadata]({{ site.baseurl }}/input/relation_metadata.html).  If you know things about
  the relations in your graph, such as what their domains and ranges are, or whether they have
known inverses, that information is specified in this directory.  If you don't know any of this
information, you can safely leave this out. The name of this parameter in the json object must be
`relation metadata`, and its value should just be a string.  The string can be a name, which will
make the code search for a directory with that name under `relation_metadata/`, or it can be a
fully qualified path.

* A description of the training / testing [split]({{ site.baseurl }}/input/split_dir.html) to use
  for the experiment.  This tells the PRA code which relations to learn models for, and which node
pairs to use as training and testing data for each relation.  The name of this parameter in the
json object must be `split`.  And its value must be a string, as with the `relation metadata`
parameter.  The only difference is the code will look under `splits/` if just a name is given.

* [PRA-specific parameters]({{ site.baseurl }}/input/pra_parameters.html).  The previous three sets
  of parameters specify the data to use for PRA - what the graph is like, what we know about the
relations in the graph, and what to use for training and testing.  These parameters specify how PRA
should work; things like how many random walks to do, whether to use the vector space walks
described in my EMNLP 2014 paper, or whether to compute a standard PRA matrix or just see what
paths you can find in the data.  The name of this in the json object must be `pra parameters`.

The name of the file (including any subdirectories under `experiment_specs/`)
defines the [result directory]({{ site.baseurl }}/output/results.html) where the
output will be put.

## Examples

### Simple, with generated data

Here's an example of a fully specified and functional experiment spec:

```
{
  "graph": {
    "name": "test_graph",
    "relation sets": [
      {
        "type": "generated",
        "generation params": {
          "name": "synthetic/very_easy",
          "num_entities": 10000,
          "num_base_relations": 20,
          "num_base_relation_training_duplicates": 5,
          "num_base_relation_testing_duplicates": 0,
          "num_base_relation_overlapping_instances": 500,
          "num_base_relation_noise_instances": 100,
          "num_pra_relations": 2,
          "num_pra_relation_training_instances": 200,
          "num_pra_relation_testing_instances": 50,
          "num_rules": 5,
          "min_rule_length": 1,
          "max_rule_length": 4,
          "rule_prob_mean": 0.6,
          "rule_prob_stddev": 0.2,
          "num_noise_relations": 2,
          "num_noise_relation_instances": 100
        }
      }
    ]
  },
  "split": "synthetic/very_easy",
  "pra parameters": {
    "pra mode": "standard",
    "l1 weight": 0.005,
    "l2 weight": 1,
    "walks per source": 100,
    "walks per path": 50,
    "path finding iterations": 3,
    "number of paths to keep": 1000,
    "matrix accept policy": "all-targets",
    "path accept policy": "paired-only"
  }
}
```

Don't worry too much about all of the individual parameters - you can look at the links above to
get a better description on each of them.  For now, just pay attention to the format.  You can copy
and paste this spec file, and it should just work; the specified graph comes from the data
generator in the code base, so there are no other input files you need to have for this to work.
`ExperimentRunner` will look for the graph, see that it does not exist in the `graphs/` directory,
and try to create it.  Creating the graph will look for the relation set under `relation_sets/`,
see that it's not there, and generate the data.

After this has run once and the graph has been created, I could alternatively specify the graph
just with a name, instead of with a nested json object.  So in subsequent experiments, this
specification would work:

```
{
  "graph": "test_graph",
  "split": "synthetic_very_easy",
  "pra parameters": {
    "pra mode": "standard",
    "l1 weight": 0.005,
    "l2 weight": 1,
    "walks per source": 100,
    "walks per path": 50,
    "path finding iterations": 3,
    "number of paths to keep": 1000,
    "matrix accept policy": "all-targets",
    "path accept policy": "paired-only"
  }
}
```

If the graph is just a name, `ExperimentRunner` will look under the `graphs/` directory for
something with that name.  You could also specify a path, if you have the graph stored elsewhere.
Be careful with this, though - `ExperimentRunner` does not guarantee an order that the experiments
will run in, and so if you're only creating the graph once, and referring to it like this in the
other experiments, you might try to use it before it's created.  To solve this issue, keep reading.

### More complex, with load statements

If you consider the specification above, you might notice that it contains a complete set of
parameters both for a data set and for running PRA.  If you want to use the same data in another
experiment, or if you use the same PRA parameters across multiple experiments, you'll have to
repeat all of these parameters every time they're used.  Unless you use load statements.

The code that reads these specification files will accept a `load` keyword to read another file
containing parameters.  For example, you could have a file, `pra_params.json`, containing the PRA
parameters that you tend to reuse:

```
{
  "pra mode": "standard",
  "l1 weight": 0.005,
  "l2 weight": 1,
  "walks per source": 100,
  "walks per path": 50,
  "path finding iterations": 3,
  "number of paths to keep": 1000,
  "matrix accept policy": "all-targets",
  "path accept policy": "paired-only"
}
```

Then in your experiment specification, you can put in a load statement to read those parameters:

```
{
  ...
  "pra parameters": "load pra_params"
}
```

Note the format - it is `load`, space, name, where the extension is dropped.  If this is what the
load statement looks like, the code will look under `param_files/` for a file with that name
(e.g., it will check for `param_files/pra_params.json` in this example).  You can alternatively
give a fully specified path for the parameters you are loading.

This kind of a load works just fine for a lot of cases, but does not work very well if you want to
use these parameters as defaults, but override them in some experiments.  If the `pra parameters`
key in the json is used for a load statement, it can't also contain other overrides.  So, you can
also have a load statement at the beginning of a file, and those parameters can be overriden.  For
example, say we change `pra_params.json` to instead look like this:

```
{
  "pra parameters": {
    "pra mode": "standard",
    "l1 weight": 0.005,
    "l2 weight": 1,
    "walks per source": 100,
    "walks per path": 50,
    "path finding iterations": 3,
    "number of paths to keep": 1000,
    "matrix accept policy": "all-targets",
    "path accept policy": "paired-only"
  }
}
```

The only difference here is that the parameters are nested under `pra parameters`, so they show up
where they're supposed to in an experiment specification.  Now, in my experiment spec, I can do
the following:


```
load pra_params
{
  ...
  "pra parameters": {
    "path follower": "matrix multiplication"
  }
}
```

And I can have my default parameters set, and only specify here the things that I want done
differently from my defaults.  This allows for relatively easy specification of parallel
experiments, where just one thing changes across the set.

Also note that the files that are loaded with a `load` statement can themselves have `load`
statements, so you can go as deep with this as you care to.

And that, along with reading the links above on what each of these parameters actually _means_,
should be enough to get you started using this.  There are some examples of experiment
specifications and parameter files that I actually use in the `examples/` directory of the PRA
[codebase](http://github.com/matt-gardner/pra).
