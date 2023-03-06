/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import sbt._
import Keys._

object Dependencies {
  // keep in sync with .github/workflows/unit-tests.yml
  val scala212Version = "2.12.17"
  val scala213Version = "2.13.10"
  val scala3Version = "3.1.2" // not yet enabled - missing pekko-http/pekko-management Scala 3 artifacts
  val scalaVersions = Seq(scala212Version, scala213Version)

  val akkaVersion = System.getProperty("override.akka.version", "2.6.20")
  val akkaVersionInDocs = akkaVersion.take(3)
  val cassandraVersionInDocs = "4.0"
  // Should be sync with the version of the driver in Alpakka Cassandra
  val driverVersionInDocs = "4.6"

  val alpakkaVersion = "3.0.4"
  val alpakkaVersionInDocs = alpakkaVersion.take(3)
  // for example
  val akkaManagementVersion = "1.1.4"

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.10"

  val reconcilerDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test)

  val akkaTestDeps = Seq(
    "com.typesafe.akka" %% "akka-persistence",
    "com.typesafe.akka" %% "akka-persistence-typed",
    "com.typesafe.akka" %% "akka-persistence-query",
    "com.typesafe.akka" %% "akka-cluster-typed",
    "com.typesafe.akka" %% "akka-actor-testkit-typed",
    "com.typesafe.akka" %% "akka-persistence-tck",
    "com.typesafe.akka" %% "akka-stream-testkit",
    "com.typesafe.akka" %% "akka-multi-node-testkit",
    "com.typesafe.akka" %% "akka-cluster-sharding")

  val akkaPersistenceCassandraDependencies = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % alpakkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.7.0",
    logback % Test,
    "org.scalatest" %% "scalatest" % "3.2.14" % Test,
    "org.pegdown" % "pegdown" % "1.6.0" % Test,
    "org.osgi" % "org.osgi.core" % "5.0.0" % Provided) ++ akkaTestDeps.map(_ % akkaVersion % Test)

  val exampleDependencies = Seq(
    logback,
    "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
    "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion,
    "org.hdrhistogram" % "HdrHistogram" % "2.1.12")

  val dseTestDependencies = Seq(
    "com.datastax.dse" % "dse-java-driver-core" % "2.3.0" % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    logback % Test)
}
