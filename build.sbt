name := "pra"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.11.2", "2.10.3")

resolvers += Resolver.url("https://raw.github.com/matt-gardner/graphchi-java/mvn-repo/")

libraryDependencies ++= Seq(
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "com.google.guava" % "guava" % "17.0",
  "log4j" % "log4j" % "1.2.16",
  "cc.mallet" % "mallet" % "2.0.7",
  "commons-io" % "commons-io" % "2.4",
  "net.sf.trove4j" % "trove4j" % "2.0.2",
  "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "groupId" %  "graphchi-java" % "0.2"
)

instrumentSettings

jacoco.settings
