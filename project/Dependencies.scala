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
  val scala212Version = "2.12.18"
  val scala213Version = "2.13.12"
  val scala3Version = "3.3.1"
  val scalaVersions = Seq(scala212Version, scala213Version, scala3Version)

  val pekkoVersion = PekkoCoreDependency.version
  val pekkoVersionInDocs = "1.0"
  val cassandraVersionInDocs = "4.0"

  // Should be sync with the version of the driver in Pekko Connectors Cassandra
  val driverVersion = "4.17.0"
  val driverVersionInDocs = "4.17"

  val pekkoConnectorsVersion = "1.0.1"
  val pekkoConnectorsVersionInDocs = "1.0"
  // for example
  val pekkoManagementVersion = "1.0.0"

  val nettyVersion = "4.1.106.Final"
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.13"

  val reconcilerDependencies = Seq(
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test)

  val pekkoTestDeps = Seq(
    "org.apache.pekko" %% "pekko-persistence",
    "org.apache.pekko" %% "pekko-persistence-typed",
    "org.apache.pekko" %% "pekko-persistence-query",
    "org.apache.pekko" %% "pekko-cluster-typed",
    "org.apache.pekko" %% "pekko-actor-testkit-typed",
    "org.apache.pekko" %% "pekko-persistence-tck",
    "org.apache.pekko" %% "pekko-stream-testkit",
    "org.apache.pekko" %% "pekko-multi-node-testkit",
    "org.apache.pekko" %% "pekko-cluster-sharding")

  val pekkoPersistenceCassandraDependencies = Seq(
    "org.apache.pekko" %% "pekko-connectors-cassandra" % pekkoConnectorsVersion,
    "org.apache.pekko" %% "pekko-persistence" % pekkoVersion,
    "org.apache.pekko" %% "pekko-persistence-query" % pekkoVersion,
    "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
    "org.apache.pekko" %% "pekko-cluster-tools" % pekkoVersion,
    "com.datastax.oss" % "java-driver-core" % driverVersion,
    "io.netty" % "netty-handler" % nettyVersion,
    logback % Test,
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.7.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    "org.pegdown" % "pegdown" % "1.6.0" % Test,
    "org.osgi" % "org.osgi.core" % "5.0.0" % Provided) ++ pekkoTestDeps.map(_ % pekkoVersion % Test)

  val exampleDependencies = Seq(
    logback,
    "org.apache.pekko" %% "pekko-persistence-typed" % pekkoVersion,
    "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,
    "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
    "org.apache.pekko" %% "pekko-cluster-sharding-typed" % pekkoVersion,
    "org.apache.pekko" %% "pekko-management" % pekkoManagementVersion,
    "org.apache.pekko" %% "pekko-management-cluster-bootstrap" % pekkoManagementVersion,
    "org.apache.pekko" %% "pekko-management-cluster-http" % pekkoManagementVersion,
    "org.apache.pekko" %% "pekko-discovery-kubernetes-api" % pekkoManagementVersion,
    "org.hdrhistogram" % "HdrHistogram" % "2.1.12")

  val dseTestDependencies = Seq(
    "com.datastax.dse" % "dse-java-driver-core" % "2.3.0" % Test,
    "org.apache.pekko" %% "pekko-persistence-tck" % pekkoVersion % Test,
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
    logback % Test)
}
