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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.Props
import org.apache.pekko.persistence.cassandra.journal.MultiPluginSpec._
import org.apache.pekko.persistence.cassandra.testkit.CassandraLauncher
import org.apache.pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec, PluginSettings }
import org.apache.pekko.persistence.{ PersistentActor, SaveSnapshotSuccess }

object MultiPluginSpec {
  val now = System.currentTimeMillis()
  val journalKeyspace = s"multiplugin_spec_journal_$now"
  val snapshotKeyspace = s"multiplugin_spec_snapshot_$now"
  val cassandraPort = CassandraLauncher.randomPort
  val config = ConfigFactory.parseString(s"""
       |pekko.test.single-expect-default = 20s
       |pekko.test.filter-leeway = 20s
       |
       |pekko.persistence.snapshot-store.plugin = ""
       |
       |pekko.persistence.cassandra.journal.keyspace = $journalKeyspace
       |pekko.persistence.cassandra.journal.circuit-breaker.call-timeout = 30s
       |pekko.persistence.cassandra.snapshot.keyspace=$snapshotKeyspace
       |
       |cassandra-plugin-a=$${pekko.persistence.cassandra}
       |cassandra-plugin-a.journal.table=processor_a_messages
       |
       |cassandra-plugin-b=$${pekko.persistence.cassandra}
       |cassandra-plugin-b.journal.table=processor_b_messages
       |
       |cassandra-plugin-c=$${pekko.persistence.cassandra}
       |cassandra-plugin-c.journal.table=processor_c_messages
       |cassandra-plugin-c.snapshot.table=snapshot_c_messages
       |
       |cassandra-plugin-d=$${pekko.persistence.cassandra}
       |cassandra-plugin-d.journal.table=processor_d_messages
       |cassandra-plugin-d.snapshot.table=snapshot_d_messages
       |
    """.stripMargin).withFallback(CassandraLifecycle.config)

  trait Processor extends PersistentActor {

    override def receiveRecover: Receive = {
      case _ =>
    }

    override def receiveCommand: Receive = {
      case _: SaveSnapshotSuccess =>
      case "snapshot"             => saveSnapshot("snapshot")
      case payload =>
        persist(payload) { payload =>
          sender() ! s"$payload-$lastSequenceNr"
        }
    }

    def noop: Receive = {
      case _: String =>
    }
  }

  class OverrideJournalPluginProcessor(override val journalPluginId: String) extends Processor {
    override val persistenceId: String = "always-the-same"
    override val snapshotPluginId: String = "pekko.persistence.cassandra.snapshot"
  }

  class OverrideSnapshotPluginProcessor(override val journalPluginId: String, override val snapshotPluginId: String)
      extends Processor {
    override val persistenceId: String = "always-the-same"
  }

}

class MultiPluginSpec
    extends CassandraSpec(
      MultiPluginSpec.config,
      journalName = MultiPluginSpec.journalKeyspace,
      snapshotName = MultiPluginSpec.snapshotKeyspace) {

  lazy val cassandraPluginSettings = PluginSettings(system)

  // default journal plugin is not used for this test
//  override def awaitPersistenceInit(): Unit = ()

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    CassandraLifecycle.awaitPersistenceInit(system, "cassandra-plugin-a.journal")
    CassandraLifecycle.awaitPersistenceInit(system, "cassandra-plugin-b.journal")
    CassandraLifecycle.awaitPersistenceInit(system, "cassandra-plugin-c.journal", "cassandra-plugin-c.snapshot")
    CassandraLifecycle.awaitPersistenceInit(system, "cassandra-plugin-d.journal", "cassandra-plugin-d.snapshot")
  }

  "A Cassandra journal" must {
    "be usable multiple times with different configurations for two actors having the same persistence id" in {
      val processorA = system.actorOf(Props(classOf[OverrideJournalPluginProcessor], "cassandra-plugin-a.journal"))
      processorA ! s"msg-a"
      expectMsg(s"msg-a-1")

      val processorB = system.actorOf(Props(classOf[OverrideJournalPluginProcessor], "cassandra-plugin-b.journal"))
      processorB ! s"msg-b"
      expectMsg(s"msg-b-1")

      processorB ! s"msg-b"
      expectMsg(s"msg-b-2")

      // c is actually a and therefore the next message must be seqNr 2 and not 3
      val processorC = system.actorOf(Props(classOf[OverrideJournalPluginProcessor], "cassandra-plugin-a.journal"))
      processorC ! s"msg-a"
      expectMsg(s"msg-a-2")
    }
  }

  "A Cassandra snapshot store" must {
    "be usable multiple times with different configurations for two actors having the same persistence id" in {
      val processorC =
        system.actorOf(
          Props(classOf[OverrideSnapshotPluginProcessor], "cassandra-plugin-c.journal", "cassandra-plugin-c.snapshot"))
      processorC ! s"msg-c"
      expectMsg(s"msg-c-1")

      val processorD =
        system.actorOf(
          Props(classOf[OverrideSnapshotPluginProcessor], "cassandra-plugin-d.journal", "cassandra-plugin-d.snapshot"))
      processorD ! s"msg-d"
      expectMsg(s"msg-d-1")

      processorD ! s"msg-d"
      expectMsg(s"msg-d-2")

      processorC ! "snapshot"
      processorD ! "snapshot"

      // e is actually c and therefore the next message must be seqNr 2 after recovery by using the snapshot
      val processorE =
        system.actorOf(
          Props(classOf[OverrideSnapshotPluginProcessor], "cassandra-plugin-c.journal", "cassandra-plugin-c.snapshot"))
      processorE ! s"msg-c"
      expectMsg(s"msg-c-2")

      // e is actually c and therefore the next message must be seqNr 2 after recovery by using the snapshot
      val processorF =
        system.actorOf(
          Props(classOf[OverrideSnapshotPluginProcessor], "cassandra-plugin-d.journal", "cassandra-plugin-d.snapshot"))
      processorF ! s"msg-d"
      expectMsg(s"msg-d-3")

    }
  }
}
