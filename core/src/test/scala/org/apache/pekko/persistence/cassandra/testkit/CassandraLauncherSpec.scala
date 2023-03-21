/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.cassandra.testkit

import java.io.File
import java.net.InetSocketAddress

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import com.datastax.oss.driver.api.core.CqlSession
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import org.scalatest.BeforeAndAfterAll

class CassandraLauncherSpec
    extends TestKit(ActorSystem("CassandraLauncherSpec"))
    with Matchers
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    shutdown(system, verifySystemShutdown = true)
    CassandraLauncher.stop()
    super.afterAll()
  }

  private def testCassandra(): Unit = {
    val session =
      CqlSession
        .builder()
        .withLocalDatacenter("datacenter1")
        .addContactPoint(new InetSocketAddress("localhost", CassandraLauncher.randomPort))
        .build()
    try session.execute("SELECT now() from system.local;").one()
    finally {
      session.close()
    }
  }

  "The CassandraLauncher" must {
    "support forking" in {
      val cassandraDirectory = new File("target/" + system.name)
      CassandraLauncher.start(
        cassandraDirectory,
        configResource = CassandraLauncher.DefaultTestConfigResource,
        clean = true,
        port = 0,
        CassandraLauncher.classpathForResources("logback-test.xml"))

      awaitAssert({
          testCassandra()
        }, 45.seconds)

      CassandraLauncher.stop()

      an[Exception] shouldBe thrownBy(testCassandra())
    }
  }

}
