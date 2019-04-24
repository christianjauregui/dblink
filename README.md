# dblink
`dblink` is a Spark package for performing unsupervised entity resolution 
(ER) on structured data.
It's based on a Bayesian model called `blink` 
[(Steorts, 2015)](https://projecteuclid.org/euclid.ba/1441790411), 
with extensions proposed in
[(Marchant, Steorts, Kaplan, Rubinstein, Elazar, 2019)](https://TODO).
Unlike many ER algorithms, `dblink` approximates the full posterior 
distribution over clusterings of records (into entities).
This facilitates propagation of uncertainty to post-ER analysis, 
and provides a framework for answering probabilistic queries about entity 
membership.

`dblink` approximates the posterior using Markov chain Monte Carlo.
It write samples (of clustering configurations) to disk in Parquet format.
Diagnostic summary statistics are also written to disk in CSV format—these are 
useful for assessing convergence of the Markov chain.

## How to: Add dblink as a project dependency
_Note: This won't work yet. Waiting for project to be accepted._

Maven:
```xml
<dependency>
  <groupId>com.github.ngmarchant</groupId>
  <artifactId>dblink</artifactId>
  <version>0.1.0</version>
</dependency>
```

sbt:
```scala
libraryDependencies += "com.github.ngmarchant" % "dblink" % 0.1.0
```

## How to: Build a fat JAR
You can build a fat JAR using sbt by running the following command from
within the project directory:
```bash
$ sbt assembly
```

This should output a JAR file at `./target/scala-2.11/dblink-assembly-0.1.jar`
relative to the project directory.
Note that the JAR file does not bundle Spark or Hadoop, but it does include
all other dependencies.

## Example: RLdata500
A small data set called `RLdata500` is included in the examples directory as a
CSV file. This is a syntethic data set with 500 total records and 10 percent duplicates, where
ground truth is known so that standard entity resolution metrics can be assessed. [[TODO: Add in some asessments
to show how these work after running the package for support. Perhaps make these into an R package and build an R interface for this part for the output of dblink. Just a suggestion. There could be a vignette for this and any analysis.]]

It was extracted from the [RecordLinkage](https://cran.r-project.org/web/packages/RecordLinkage/index.html)
R package and contains 500 synthetically generated records, with some distorted
values.
A dblink config file `RLdata500.conf` is included in the examples directory for
this data set.
To run it, build the fat JAR according to the instructions above, then use
`spark-submit` as follows:
```bash
$SPARK_HOME/bin/spark-submit \
  --master local[1] \
  --conf "spark.driver.extraJavaOptions=-Dlog4j.configuration=log4j.properties" \
  --conf "spark.driver.extraClassPath=./target/scala-2.11/dblink-assembly-0.1.jar" \
  ./target/scala-2.11/dblink-assembly-0.1.jar \
  ./examples/RLdata500.conf
```

## Example: RLdata10000

TODO: Add an example of RLdata10000 once we have RLdata500 working. 

The output will be saved at `./examples/RLdata500_results/` (as specified in
the dblink config file).

## License
GPL-3

## Citing the package
Marchant, N. G., Steorts R. C., Kaplan, A., Rubinstein, B. I. P., Elazar, D. N. 
(2019). dblink: Distributed End-to-End Bayesian Entity Resolution. _TODO_, ?-?.
