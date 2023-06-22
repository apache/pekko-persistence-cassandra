/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.cassandra

import java.io.File
import org.apache.pekko
import pekko.actor.{ ActorSystem, Props }
import pekko.persistence.cassandra.CassandraLifecycle.AwaitPersistenceInit
import pekko.testkit.{ ImplicitSender, SocketUtil, TestKit }
import com.typesafe.config.ConfigFactory
import pekko.persistence.cassandra.testkit.CassandraLauncher
import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object ReconnectSpec {
  val freePort = SocketUtil.temporaryLocalPort()
  val config = ConfigFactory.parseString(s"""
      datastax-java-driver {
        basic.load-balancing-policy.local-datacenter = "datacenter1"
        // Will fail without this setting 
        advanced.reconnect-on-init = true      
        basic.contact-points = ["127.0.0.1:$freePort"]
      }
      """).withFallback(CassandraLifecycle.config)
}

// not using Cassandra Spec
class ReconnectSpec
    extends TestKit(ActorSystem("ReconnectSpec", ReconnectSpec.config))
    with Suite
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  "Reconnecting" must {
    "start with system off" in {
      val pa = system.actorOf(Props(new AwaitPersistenceInit("pid", "", "")))
      pa ! "hello"
      expectNoMessage()

      CassandraLauncher.start(
        new File("target/ReconnectSpec"),
        configResource = CassandraLauncher.DefaultTestConfigResource,
        clean = true,
        port = ReconnectSpec.freePort,
        CassandraLauncher.classpathForResources("logback-test.xml"))

      try {
        CassandraLifecycle.awaitPersistenceInit(system)
      } finally {
        CassandraLauncher.stop()
      }

    }
  }

}
