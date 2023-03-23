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

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.persistence.PersistentRepr
import pekko.persistence.cassandra.TestTaggingActor.Ack
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec, TestTaggingActor }
import pekko.persistence.query.{ PersistenceQuery, ReadJournalProvider }
import pekko.stream.testkit.scaladsl.TestSink
import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.duration._

class JournalOverride(as: ExtendedActorSystem, config: Config, configPath: String)
    extends CassandraReadJournal(as, config, configPath) {
  override private[pekko] def mapEvent(pr: PersistentRepr) =
    PersistentRepr("cat", pr.sequenceNr, pr.persistenceId, pr.manifest, pr.deleted, pr.sender, pr.writerUuid)
}

class JournalOverrideProvider(as: ExtendedActorSystem, config: Config, configPath: String) extends ReadJournalProvider {
  override def scaladslReadJournal() = new JournalOverride(as, config, configPath)
  override def javadslReadJournal() = null
}

object CassandraQueryJournalOverrideSpec {

  val config = ConfigFactory.parseString("""
      pekko.persistence.cassandra.query {
        class = "org.apache.pekko.persistence.cassandra.query.JournalOverrideProvider"
      }
    """.stripMargin).withFallback(CassandraLifecycle.config)

}

class CassandraQueryJournalOverrideSpec extends CassandraSpec(CassandraQueryJournalOverrideSpec.config) {

  lazy val journal =
    PersistenceQuery(system).readJournalFor[JournalOverride](CassandraReadJournal.Identifier)

  "Cassandra query journal override" must {
    "map events" in {
      val pid = "p1"
      val p1 = system.actorOf(TestTaggingActor.props(pid))
      p1 ! "not a cat"
      expectMsg(Ack)

      val currentEvents = journal.currentEventsByPersistenceId(pid, 0, Long.MaxValue)
      val currentProbe = currentEvents.map(_.event.toString).runWith(TestSink.probe[String])
      currentProbe.request(2)
      currentProbe.expectNext("cat")
      currentProbe.expectComplete()

      val liveEvents = journal.eventsByPersistenceId(pid, 0, Long.MaxValue)
      val liveProbe = liveEvents.map(_.event.toString).runWith(TestSink.probe[String])
      liveProbe.request(2)
      liveProbe.expectNext("cat")
      liveProbe.expectNoMessage(100.millis)
      liveProbe.cancel()

      val internalEvents = journal.eventsByPersistenceIdWithControl(pid, 0, Long.MaxValue, None)
      val internalProbe = internalEvents.map(_.event.toString).runWith(TestSink.probe[String])
      internalProbe.request(2)
      internalProbe.expectNext("cat")
      liveProbe.expectNoMessage(100.millis)
      liveProbe.cancel()
    }
  }
}
