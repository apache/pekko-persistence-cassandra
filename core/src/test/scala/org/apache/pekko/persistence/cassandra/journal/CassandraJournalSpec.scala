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

import org.apache.pekko.actor.Actor
import org.apache.pekko.persistence.CapabilityFlag
import org.apache.pekko.persistence.{ AtomicWrite, PersistentRepr }
import org.apache.pekko.persistence.JournalProtocol.{
  ReplayMessages,
  WriteMessageFailure,
  WriteMessages,
  WriteMessagesFailed
}

import scala.concurrent.duration._
import org.apache.pekko.persistence.journal._
import org.apache.pekko.persistence.cassandra.CassandraLifecycle
import org.apache.pekko.stream.connectors.cassandra.CassandraMetricsRegistry
import org.apache.pekko.testkit.TestProbe
import com.typesafe.config.ConfigFactory

object CassandraJournalConfiguration {
  val config = ConfigFactory.parseString(s"""
       pekko.persistence.cassandra.journal.keyspace=CassandraJournalSpec
       pekko.persistence.cassandra.snapshot.keyspace=CassandraJournalSpecSnapshot
       datastax-java-driver {
         basic.session-name = CassandraJournalSpec
         advanced.metrics {
           session.enabled = [ "bytes-sent", "cql-requests"]
         }
       }  
    """).withFallback(CassandraLifecycle.config)

  lazy val perfConfig =
    ConfigFactory.parseString("""
    akka.actor.serialize-messages=off
    pekko.persistence.cassandra.journal.keyspace=CassandraJournalPerfSpec
    pekko.persistence.cassandra.snapshot.keyspace=CassandraJournalPerfSpecSnapshot
    """).withFallback(config)

}

// Can't use CassandraSpec so needs to do its own clean up
class CassandraJournalSpec extends JournalSpec(CassandraJournalConfiguration.config) with CassandraLifecycle {
  override def systemName: String = "CassandraJournalSpec"

  override def supportsRejectingNonSerializableObjects = false

  "A Cassandra Journal" must {
    "insert Cassandra metrics to Cassandra Metrics Registry" in {
      val registry = CassandraMetricsRegistry(system).getRegistry
      val metricsNames = registry.getNames.toArray.toSet
      // metrics category is the configPath of the plugin + the session-name
      metricsNames should contain("pekko.persistence.cassandra.CassandraJournalSpec.bytes-sent")
      metricsNames should contain("pekko.persistence.cassandra.CassandraJournalSpec.cql-requests")
    }
    "be able to replay messages after serialization failure" in {
      // there is no chance that a journal could create a data representation for type of event
      val notSerializableEvent = new Object {
        override def toString = "not serializable"
      }
      val msg = PersistentRepr(
        payload = notSerializableEvent,
        sequenceNr = 6,
        persistenceId = pid,
        sender = Actor.noSender,
        writerUuid = writerUuid)

      val probe = TestProbe()

      journal ! WriteMessages(List(AtomicWrite(msg)), probe.ref, actorInstanceId)
      val err = probe.expectMsgPF() {
        case fail: WriteMessagesFailed => fail.cause
      }
      probe.expectMsg(WriteMessageFailure(msg, err, actorInstanceId))

      journal ! ReplayMessages(5, 5, 1, pid, probe.ref)
      probe.expectMsg(replayedMessage(5))
    }
  }
}

class CassandraJournalMetaSpec extends JournalSpec(CassandraJournalConfiguration.config) with CassandraLifecycle {
  override def systemName: String = "CassandraJournalSpec"

  override def supportsRejectingNonSerializableObjects = false
  protected override def supportsMetadata: CapabilityFlag = true
}

class CassandraJournalPerfSpec
    extends JournalPerfSpec(CassandraJournalConfiguration.perfConfig)
    with CassandraLifecycle {
  override def systemName: String = "CassandraJournalPerfSpec"

  override def awaitDurationMillis: Long = 20.seconds.toMillis

  override def supportsRejectingNonSerializableObjects = false

}
