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

package org.apache.pekko.persistence.cassandra.query.scaladsl

import org.apache.pekko
import pekko.persistence.cassandra.query.TestActor
import pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec }
import pekko.persistence.journal.{ Tagged, WriteEventAdapter }
import pekko.persistence.query.NoOffset
import pekko.stream.connectors.cassandra.CassandraMetricsRegistry
import pekko.stream.testkit.scaladsl.TestSink
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object CassandraReadJournalSpec {
  val config = ConfigFactory.parseString(s"""
    pekko.actor.serialize-messages=off
    pekko.persistence.cassandra.query.max-buffer-size = 10
    pekko.persistence.cassandra.query.refresh-interval = 0.5s
    pekko.persistence.cassandra.journal.event-adapters {
      test-tagger = org.apache.pekko.persistence.cassandra.query.scaladsl.TestTagger
    }
    pekko.persistence.cassandra.journal.event-adapter-bindings = {
      "java.lang.String" = test-tagger
    }
    pekko.persistence.cassandra.log-queries = off
    """).withFallback(CassandraLifecycle.config)
}

class TestTagger extends WriteEventAdapter {
  override def manifest(event: Any): String = ""
  override def toJournal(event: Any): Any = event match {
    case s: String if s.startsWith("a") =>
      Tagged(event, Set("a"))
    case _ =>
      event
  }
}

class CassandraReadJournalSpec extends CassandraSpec(CassandraReadJournalSpec.config) {

  "Cassandra Read Journal Scala API" must {
    "start eventsByPersistenceId query" in {
      val a = system.actorOf(TestActor.props("a"))
      a ! "a-1"
      expectMsg("a-1-done")

      val src = queries.eventsByPersistenceId("a", 0L, Long.MaxValue)
      src.map(_.persistenceId).runWith(TestSink.probe[Any]).request(10).expectNext("a").cancel()
    }

    "start current eventsByPersistenceId query" in {
      val a = system.actorOf(TestActor.props("b"))
      a ! "b-1"
      expectMsg("b-1-done")

      val src = queries.currentEventsByPersistenceId("b", 0L, Long.MaxValue)
      src.map(_.persistenceId).runWith(TestSink.probe[Any]).request(10).expectNext("b").expectComplete()
    }

    // these tests rely on events written in previous tests
    "start eventsByTag query" in {
      val src = queries.eventsByTag("a", NoOffset)
      src
        .map(_.persistenceId)
        .runWith(TestSink.probe[Any])
        .request(10)
        .expectNext("a")
        .expectNoMessage(100.millis)
        .cancel()
    }

    "start current eventsByTag query" in {
      val src = queries.currentEventsByTag("a", NoOffset)
      src.map(_.persistenceId).runWith(TestSink.probe[Any]).request(10).expectNext("a").expectComplete()
    }

    "insert Cassandra metrics to Cassandra Metrics Registry" in {
      val registry = CassandraMetricsRegistry(system).getRegistry
      val snapshots =
        registry.getNames.toArray().filter(value => value.toString.startsWith("pekko.persistence.cassandra"))
      snapshots.length should be > 0
    }
  }
}
