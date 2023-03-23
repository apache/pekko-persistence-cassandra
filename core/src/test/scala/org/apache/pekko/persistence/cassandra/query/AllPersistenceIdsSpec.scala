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

package org.apache.pekko.persistence.cassandra.query

import java.util.UUID

import scala.concurrent.duration._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorRef
import pekko.persistence.cassandra.CassandraLifecycle
import pekko.persistence.cassandra.CassandraSpec
import pekko.persistence.cassandra.journal.JournalSettings
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.scaladsl.TestSink
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach

object AllPersistenceIdsSpec {
  val config = ConfigFactory.parseString(s"""
    pekko.persistence.cassandra {
      journal.target-partition-size = 15
      query {
        max-buffer-size = 10
        refresh-interval = 0.5s
        max-result-size-query = 10
      }
    }  
    """).withFallback(CassandraLifecycle.config)
}

class AllPersistenceIdsSpec extends CassandraSpec(AllPersistenceIdsSpec.config) with BeforeAndAfterEach {

  val cfg = system.settings.config.getConfig("pekko.persistence.cassandra")
  val journalSettings = new JournalSettings(system, cfg)

  override protected def beforeEach() = {
    super.beforeEach()
    deleteAllEvents()
  }

  def all(): Source[String, NotUsed] = queries.persistenceIds().filterNot(_.startsWith("persistenceInit"))

  def current(): Source[String, NotUsed] = queries.currentPersistenceIds().filterNot(_.startsWith("persistenceInit"))

  private def deleteAllEvents(): Unit = {
    cluster.execute(s"TRUNCATE ${journalSettings.keyspace}.${journalSettings.table}")
    cluster.execute(s"TRUNCATE ${journalSettings.keyspace}.${journalSettings.allPersistenceIdsTable}")
  }

  private def setup(persistenceId: String, n: Int): ActorRef = {
    val ref = system.actorOf(TestActor.props(persistenceId))
    for (i <- 1 to n) {
      ref ! s"$persistenceId-$i"
      expectMsg(s"$persistenceId-$i-done")
    }

    ref
  }

  "Cassandra query CurrentPersistenceIds" must {
    "find existing events" in {
      setup("a", 1)
      setup("b", 1)
      setup("c", 1)

      val src = current()
      src.runWith(TestSink.probe[Any]).request(4).expectNextUnordered("a", "b", "c").expectComplete()
    }

    "deliver persistenceId only once if there are multiple events spanning partitions" in {
      setup("d", 100)

      val src = current()
      src.runWith(TestSink.probe[Any]).request(10).expectNext("d").expectComplete()
    }

    "find existing persistence ids in batches if there is more of them than max-result-size-query" in {
      for (_ <- 1 to 1000) {
        setup(UUID.randomUUID().toString, 1)
      }

      val src = current()
      val probe = src.runWith(TestSink.probe[Any])
      probe.request(1000)

      for (_ <- 1 to 1000) {
        probe.expectNext()
      }

      probe.expectComplete()
    }
  }

  "Cassandra query AllPersistenceIds" must {
    "find new events" in {
      setup("e", 1)
      setup("f", 1)

      val src = all()
      val probe = src.runWith(TestSink.probe[Any]).request(5).expectNextUnordered("e", "f")

      setup("g", 1)

      probe.expectNext("g")
    }

    "find new events after demand request" in {
      setup("h", 1)
      setup("i", 1)
      val src = all()
      val probe = src.runWith(TestSink.probe[Any])

      probe.request(1)
      probe.expectNext()
      probe.expectNoMessage(100.millis)

      setup("j", 1)

      probe.request(5)
      probe.expectNext()
      probe.expectNext()
    }

    "only deliver what requested if there is more in the buffer" in {
      setup("k", 1)
      setup("l", 1)
      setup("m", 1)
      setup("n", 1)
      setup("o", 1)

      val src = all()
      val probe = src.runWith(TestSink.probe[Any])
      probe.request(2)
      probe.expectNext()
      probe.expectNext()
      probe.expectNoMessage(1000.millis)

      probe.request(2)
      probe.expectNext()
      probe.expectNext()
      probe.expectNoMessage(1000.millis)
    }

    "deliver persistenceId only once if there are multiple events spanning partitions" in {
      setup("p", 1000)

      val src = all()
      val probe = src.runWith(TestSink.probe[Any])

      probe.request(10).expectNext("p").expectNoMessage(1000.millis)

      setup("q", 1000)

      probe.request(10).expectNext("q").expectNoMessage(1000.millis)
    }
  }

  "Cassandra query CurrentPersistenceIdsFromMessages" must {
    "find existing events" in {
      setup("a2", 1)
      setup("b2", 1)
      setup("c2", 1)

      val src = queries.currentPersistenceIdsFromMessages().filterNot(_.startsWith("persistenceInit"))
      src.runWith(TestSink.probe[Any]).request(4).expectNextUnordered("a2", "b2", "c2").expectComplete()
    }
  }
}
