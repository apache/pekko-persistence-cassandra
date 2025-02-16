/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from pekko.
 */

import sbt._

object Dependencies {
  // keep in sync with .github/workflows/unit-tests.yml
  val scala212Version = "2.12.20"
  val scala213Version = "2.13.16"
  val scala3Version = "3.3.5"
  val scalaVersions = Seq(scala212Version, scala213Version, scala3Version)

  val pekkoVersion = PekkoCoreDependency.version
  val pekkoVersionInDocs = PekkoCoreDependency.default.link
  val cassandraVersionInDocs = "4.0"

  // Should be sync with the version of the driver in Pekko Connectors Cassandra
  val driverVersion = "4.19.0"
  val driverVersionInDocs = "4.17"

  val pekkoConnectorsVersion = PekkoConnectorsDependency.version
  val pekkoConnectorsVersionInDocs = PekkoConnectorsDependency.default.link

  val logbackVersion = "1.3.15"

  val nettyVersion = "4.1.118.Final"
  val logback = "ch.qos.logback" % "logback-classic" % logbackVersion

  val pekkoPersistenceCassandraDependencies = Seq(
    "org.apache.cassandra" % "java-driver-core" % driverVersion,
    "io.netty" % "netty-handler" % nettyVersion,
    logback % Test,
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    "org.pegdown" % "pegdown" % "1.6.0" % Test,
    "org.osgi" % "org.osgi.core" % "6.0.0" % Provided)

  val exampleDependencies = Seq(
    logback,
    "org.hdrhistogram" % "HdrHistogram" % "2.1.12")

  val dseTestDependencies = Seq(
    "com.datastax.dse" % "dse-java-driver-core" % "2.3.0" % Test,
    logback % Test)

  val docsDependencies = Seq(
    "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2" % Test)
}
