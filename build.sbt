/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import com.typesafe.sbt.packager.docker._

ThisBuild / apacheSonatypeProjectProfile := "pekko"
ThisBuild / versionScheme := Some(VersionScheme.SemVerSpec)
sourceDistName := "apache-pekko-persistence-cassandra"
sourceDistIncubating := true

commands := commands.value.filterNot { command =>
  command.nameOption.exists { name =>
    name.contains("sonatypeRelease") || name.contains("sonatypeBundleRelease")
  }
}

addCommandAlias("applyCodeStyle", ";scalafmtAll; scalafmtSbt; javafmtAll; docs/javafmtAll; +headerCreateAll")
addCommandAlias("checkCodeStyle",
  ";+headerCheckAll; scalafmtCheckAll; scalafmtSbtCheck; javafmtCheckAll; docs/javafmtCheckAll")

lazy val root = project
  .in(file("."))
  .enablePlugins(Common, ScalaUnidocPlugin)
  .disablePlugins(SitePlugin)
  .aggregate(core, cassandraLauncher)
  .settings(name := "pekko-persistence-cassandra-root", publish / skip := true)

lazy val dumpSchema = taskKey[Unit]("Dumps cassandra schema for docs")
dumpSchema := (core / Test / runMain).toTask(" org.apache.pekko.persistence.cassandra.PrintCreateStatements").value

lazy val core = project
  .in(file("core"))
  .enablePlugins(Common, AutomateHeaderPlugin, MultiJvmPlugin)
  .dependsOn(cassandraLauncher % Test)
  .settings(
    name := "pekko-persistence-cassandra",
    libraryDependencies ++= Dependencies.pekkoPersistenceCassandraDependencies,
    Compile / packageBin / packageOptions += Package.ManifestAttributes(
      "Automatic-Module-Name" -> "pekko.persistence.cassandra"))
  .configs(MultiJvm)

lazy val cassandraLauncher = project
  .in(file("cassandra-launcher"))
  .enablePlugins(Common)
  .settings(
    name := "pekko-persistence-cassandra-launcher",
    Compile / managedResourceDirectories += (cassandraBundle / target).value / "bundle",
    Compile / managedResources += (cassandraBundle / assembly).value)

// This project doesn't get published directly, rather the assembled artifact is included as part of cassandraLaunchers
// resources
lazy val cassandraBundle = project
  .in(file("cassandra-bundle"))
  .enablePlugins(Common, AutomateHeaderPlugin)
  .settings(
    name := "pekko-persistence-cassandra-bundle",
    crossPaths := false,
    autoScalaLibrary := false,
    libraryDependencies += ("org.apache.cassandra" % "cassandra-all" % "3.11.3")
      .exclude("commons-logging", "commons-logging"),
    dependencyOverrides += "com.github.jbellis" % "jamm" % "0.3.3", // See jamm comment in https://issues.apache.org/jira/browse/CASSANDRA-9608
    assembly / target := target.value / "bundle" / "pekko" / "persistence" / "cassandra" / "launcher",
    assembly / assemblyJarName := "cassandra-bundle.jar")

// Used for testing events by tag in various environments
lazy val endToEndExample = project
  .in(file("example"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Dependencies.exampleDependencies, publish / skip := true,
    // make version compatible with docker for publishing example project,
    // see https://github.com/sbt/sbt-dynver#portable-version-strings
    inConfig(Docker)(DynVerPlugin.buildSettings ++ Seq(dynverSeparator := "-")))
  .settings(
    dockerBaseImage := "openjdk:8-jre-alpine",
    dockerCommands :=
      dockerCommands.value.flatMap {
        case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
        case v                                => Seq(v)
      },
    dockerExposedPorts := Seq(8080, 8558, 17355),
    dockerUsername := Some("kubakka"),
    dockerUpdateLatest := true,
    // update if deploying to some where that can't see docker hu
    // dockerRepository := Some("some-registry"),
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "/sbin/apk", "add", "--no-cache", "bash", "bind-tools", "busybox-extras", "curl", "iptables"),
      Cmd(
        "RUN",
        "/sbin/apk",
        "add",
        "--no-cache",
        "jattach",
        "--repository",
        "http://dl-cdn.alpinelinux.org/alpine/edge/community/"),
      Cmd("RUN", "chgrp -R 0 . && chmod -R g=u .")),
    // Docker image is only for running in k8s
    Universal / javaOptions ++= Seq("-J-Dconfig.resource=kubernetes.conf"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)

lazy val dseTest = project
  .in(file("dse-test"))
  .dependsOn(core % "test->test")
  .settings(libraryDependencies ++= Dependencies.dseTestDependencies)

lazy val docs = project
  .enablePlugins(ParadoxPlugin, PekkoParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin)
  .dependsOn(core)
  .settings(
    name := "Apache Pekko Persistence Cassandra",
    (Compile / paradox) := (Compile / paradox).dependsOn(root / dumpSchema).value,
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/pekko-persistence-cassandra/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/pekko-persistence-cassandra/${projectInfoVersion.value}",
    Compile / paradoxProperties ++= Map(
      "project.url" -> "https://pekko.apache.org/docs/pekko-persistence-cassandra/current/",
      "canonical.base_url" -> "https://pekko.apache.org/docs/pekko-persistence-cassandra/current",
      "pekko.version" -> Dependencies.pekkoVersion,
      // Pekko
      "extref.pekko.base_url" -> s"https://pekko.apache.org/docs/pekko/${Dependencies.pekkoVersionInDocs}/%s",
      "scaladoc.pekko.base_url" -> s"https://pekko.apache.org/api/pekko/${Dependencies.pekkoVersionInDocs}/",
      "javadoc.pekko.base_url" -> s"https://pekko.apache.org/japi/pekko/${Dependencies.pekkoVersionInDocs}/",
      // Connectors
      "extref.pekko-connectors.base_url" -> s"https://pekko.apache.org/docs/pekko-connectors/${Dependencies.pekkoConnectorsVersionInDocs}/%s",
      "scaladoc.org.apache.pekko.stream.connectors.base_url" -> s"https://pekko.apache.org/api/pekko-connectors/${Dependencies.pekkoConnectorsVersionInDocs}/",
      "javadoc.org.apache.pekko.stream.connectors.base_url" -> "",
      // Cassandra
      "extref.cassandra.base_url" -> s"https://cassandra.apache.org/doc/${Dependencies.cassandraVersionInDocs}/%s",
      // Datastax Java driver
      "extref.java-driver.base_url" -> s"https://docs.datastax.com/en/developer/java-driver/${Dependencies.driverVersionInDocs}/%s",
      "javadoc.com.datastax.oss.base_url" -> s"https://docs.datastax.com/en/drivers/java/${Dependencies.driverVersionInDocs}/",
      // Java
      "javadoc.base_url" -> "https://docs.oracle.com/javase/8/docs/api/",
      // Scala
      "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/${scalaBinaryVersion.value}.x/",
      "scaladoc.org.apache.pekko.persistence.cassandra.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
      "javadoc.org.apache.pekko.persistence.cassandra.base_url" -> ""), // no Javadoc is published
    paradoxGroups := Map("Language" -> Seq("Java", "Scala")),
    ApidocPlugin.autoImport.apidocRootPackage := "org.apache.pekko",
    apidocRootPackage := "org.apache.pekko")
