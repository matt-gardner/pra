---
layout: page
title: Results
---
# Results directory contents

This is the directory containing the results.  The code will check to make sure
this directory does not exist before trying to run anything, and
`ExperimentRunner` uses the existence of the directory to know which
experiments still need to be run.

Inside the main results directory, there is a `settings.tsv` file that
basically lists all of the arguments and parameters that were used to run the
code, so hopefully if you lose track of which output was in which directory,
this file will help you figure it out.

Beyond that, there is one directory per relation tested.  Inside that relation
directory are files giving what paths were found, what paths were kept, what
the learned path weights were, what the resultant feature matrices were that
were used for training and testing, and what the final predictions were during
testing.

The most important file for you to know about is probably the scores.tsv file.
This file has all of the scores for each (source, target) pair found.  Each
source is listed in its own line chunk, with all found targets listed in the
order they were predicted, with a corresponding score.  There's a `*` in the
last column if the item was a known test example, and `*^` if the item was a
known training example.

To compute metrics over these results and compare experiments, use
`ExperimentScorer`.
