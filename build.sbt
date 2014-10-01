organization := "edu.cmu.ml.rtw"

name := "pra"

version := "1.0"

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

javacOptions += "-Xlint:unchecked"

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
  "groupId" %  "graphchi-java" % "0.2",
  "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

instrumentSettings

jacoco.settings

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

licenses := Seq("GPL-3.0" -> url("http://www.opensource.org/licenses/GPL-3.0"))

homepage := Some(url("http://matt-gardner.github.io/pra"))

pomExtra := (
  <scm>
    <url>git@github.com:matt-gardner/pra.git</url>
    <connection>scm:git:git@github.com:matt-gardner/pra.git</connection>
  </scm>
  <developers>
    <developer>
      <id>matt-gardner</id>
      <name>Matt Gardner</name>
      <url>http://cs.cmu.edu/~mg1</url>
    </developer>
  </developers>)
