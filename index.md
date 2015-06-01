---
title: Home
layout: page
---
# PRA (Path Ranking Algorithm)

An implementation of the Path Ranking Algorithm (PRA) using GraphChi, a library for efficient
processing of large graphs on a single (large) machine.  This algorithm learns models that analyze
a graph and predict missing edges of particular types.

See the navigation on the left for code documentation.  Please feel free to file bugs, feature
requests, or send pull requests.  And, if you find some of this documentation confusing or
lacking, please feel free to contact me.  I try to be responsive to reasonable requests for help,
and I would like for this documentation to be useful; if it's not, tell me.

## Using the code

If all you want to do is run this algorithm as-is on your dataset, see the [getting started
guide]({{ site.baseurl }}/getting_started.html).  If you want to use this code as a library in your
own system, you can easily do so by using [maven](http://maven.apache.org/) or
[sbt](http://www.scala-sbt.org/) as your build system.  The code is released on the maven central
repository, so all you have to do is include it as a managed dependency.  If you are using sbt,
include the following line in your `build.sbt` file:

```
libraryDependencies += "edu.cmu.ml.rtw" %% "pra" % "3.0"
```

If you are using maven, you can include the PRA code by putting the following under
`<dependencies>` in your `pom.xml` file:

```
<dependency>
  <groupId>edu.cmu.ml.rtw</groupdId>
  <artifactId>pra_2.11</artifactId>
  <version>3.0</version>
</dependency>
```

Available PRA versions:

- 3.0

- 2.0

- 1.1

- 1.0

See the [changelog](https://github.com/matt-gardner/pra) in the code's README for more info on
what's new in each version.

Please note that in between versions the parameters available (and how they are specified) may
change.  If you are seeing configuration errors, or errors when processing parameters, you could
either look in the code to see what the available options are (I've tried to make those relatively
easy to find...), or you should be sure you have a tagged version of the code checked out.  The
documentation on this site reflects version 3.0.

## License

This code makes use of a number of other libraries that are distributed under various open source
licenses (notably the Apache License and the Common Public License).  You can see those
dependencies listed in the pom.xml file.  The code under the src/ directory is distributed under
the terms of the GNU General Public License, version 3 (or, at your choosing, any later version of
that license).  You can find the text of that license
[here](http://www.gnu.org/licenses/gpl-3.0.txt).
