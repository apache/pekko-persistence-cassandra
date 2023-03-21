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

package org.apache.pekko.persistence.cassandra.journal

import java.util.UUID

import scala.concurrent.Await
import org.apache.pekko.Done
import org.apache.pekko.event.Logging
import org.apache.pekko.persistence.PersistentRepr
import org.apache.pekko.persistence.cassandra.journal.CassandraJournal.Serialized
import org.apache.pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec, TestTaggingActor, _ }
import org.apache.pekko.serialization.SerializationExtension
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.stream.connectors.cassandra.CqlSessionProvider
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession

object CassandraEventUpdateSpec {
  val config = ConfigFactory.parseString("""
    """).withFallback(CassandraLifecycle.config)
}

class CassandraEventUpdateSpec extends CassandraSpec(CassandraEventUpdateSpec.config) { s =>

  private[pekko] val log = Logging(system, getClass)
  private val serialization = SerializationExtension(system)

  val updater = new CassandraEventUpdate {

    override private[pekko] val log = s.log
    override private[pekko] def settings: PluginSettings =
      PluginSettings(system)
    override private[pekko] implicit val ec: ExecutionContext = system.dispatcher
    // use separate session, not shared via CassandraSessionRegistry because init is different
    private val sessionProvider =
      CqlSessionProvider(
        system.asInstanceOf[ExtendedActorSystem],
        system.settings.config.getConfig(PluginSettings.DefaultConfigPath))
    override private[pekko] val session: CassandraSession =
      new CassandraSession(
        system,
        sessionProvider,
        ec,
        log,
        systemName,
        init = _ => Future.successful(Done),
        onClose = () => ())
  }

  "CassandraEventUpdate" must {
    "update the event in messages" in {
      val pid = nextPid
      val a = system.actorOf(TestTaggingActor.props(pid))
      a ! "e-1"
      expectMsgType[TestTaggingActor.Ack.type]
      val eventsBefore = events(pid)
      eventsBefore.map(_.pr.payload) shouldEqual Seq("e-1")
      val originalEvent = eventsBefore.head
      val modifiedEvent = serialize(originalEvent.pr.withPayload("secrets"), originalEvent.offset, Set("ignored"))

      updater.updateEvent(modifiedEvent).futureValue shouldEqual Done

      eventPayloadsWithTags(pid) shouldEqual Seq(("secrets", Set()))
    }

    "update the event in tag_views" in {
      val pid = nextPid
      val b = system.actorOf(TestTaggingActor.props(pid, Set("red", "blue")))
      b ! "e-1"
      expectMsgType[TestTaggingActor.Ack.type]
      val eventsBefore = events(pid).head
      val modifiedEvent = serialize(eventsBefore.pr.withPayload("hidden"), eventsBefore.offset, Set("ignored"))

      expectEventsForTag(tag = "red", "e-1")
      expectEventsForTag(tag = "blue", "e-1")

      updater.updateEvent(modifiedEvent).futureValue shouldEqual Done

      expectEventsForTag(tag = "red", "hidden")
      expectEventsForTag(tag = "blue", "hidden")
    }

    def serialize(pr: PersistentRepr, offset: UUID, tags: Set[String]): Serialized = {
      import system.dispatcher
      Await.result(serializeEvent(pr, tags, offset, Hour, serialization, system), remainingOrDefault)
    }
  }
}
