/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys._
import sbtheader._
import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots

object Common extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = JvmPlugin && HeaderPlugin && ApacheSonatypePlugin && DynVerPlugin

  override def globalSettings =
    Seq(
      startYear := Some(2022),
      homepage := Some(url("https://pekko.apache.org/")),
      // apiURL defined in projectSettings because version.value is not correct here
      scmInfo := Some(
        ScmInfo(
          url("https://github.com/apache/pekko-persistence-cassandra"),
          "git@github.com:apache/pekko-persistence-cassandra.git")),
      developers += Developer(
        "contributors",
        "Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/pekko-persistence-cassandra/graphs/contributors")),
      description := "A Cassandra plugin for Apache Pekko Persistence.")

  override lazy val projectSettings = Seq(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    crossVersion := CrossVersion.binary,
    crossScalaVersions := Dependencies.scalaVersions,
    scalaVersion := Dependencies.scala213Version,
    scalacOptions ++= Seq("-encoding", "UTF-8", "-feature", "-unchecked", "-Xlint", "-Ywarn-dead-code", "-deprecation"),
    Compile / console / scalacOptions --= Seq("-deprecation", "-Xfatal-warnings", "-Xlint", "-Ywarn-unused:imports"),
    Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
      "-doc-title",
      "Apache Pekko Persistence Cassandra",
      "-doc-version",
      version.value,
      "-sourcepath",
      (ThisBuild / baseDirectory).value.toString,
      "-doc-source-url", {
        val branch = if (isSnapshot.value) "main" else s"v${version.value}"
        s"https://github.com/apache/pekko-persistence-cassandra/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
      },
      "-doc-canonical-base-url",
      "https://pekko.apache.org/api/pekko-persistence-cassandra/current/") ++ {
      // for some reason Scaladoc creates this
      if (scalaBinaryVersion.value == "3")
        List("-skip-packages:org.apache.pekko.pattern")
      else
        List("-skip-packages", "org.apache.pekko.pattern")
    },
    Compile / doc / scalacOptions --= Seq("-Xfatal-warnings"),
    autoAPIMappings := true,
    Test / logBuffered := System.getProperty("pekko.logBufferedTests", "false").toBoolean,
    // show full stack traces and test case durations
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    // -a Show stack traces and exception class name for AssertionErrors.
    // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
    // -q Suppress stdout for successful tests.
    Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v", "-q"),
    Test / parallelExecution := false)

  override lazy val buildSettings = Seq(
    dynverSonatypeSnapshots := true)
}
