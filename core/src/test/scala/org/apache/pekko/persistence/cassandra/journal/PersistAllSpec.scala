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

package org.apache.pekko.persistence.cassandra.journal

import java.util.UUID

import org.apache.pekko
import pekko.actor._
import pekko.persistence._
import pekko.persistence.cassandra.CassandraLifecycle
import pekko.persistence.cassandra.CassandraSpec
import pekko.testkit._
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object PersistAllSpec {
  private val config = ConfigFactory.parseString(s"""
      pekko.persistence.cassandra.journal.max-message-batch-size = 100
      pekko.persistence.cassandra.journal.keyspace=PersistAllSpec
      pekko.persistence.cassandra.snapshot.keyspace=PersistAllSpecSnapshot
      """).withFallback(CassandraLifecycle.config)

  case class DeleteTo(snr: Long)

  class ProcessorAtomic(val persistenceId: String, receiver: ActorRef) extends PersistentActor {
    def receiveRecover: Receive = handle

    def receiveCommand: Receive = {
      case DeleteTo(sequenceNr) =>
        deleteMessages(sequenceNr)
      case payload: List[_] =>
        persistAll(payload)(handle)
    }

    def handle: Receive = {
      case payload: String =>
        receiver ! payload
        receiver ! lastSequenceNr
        receiver ! recoveryRunning
    }
  }
}

import pekko.persistence.cassandra.journal.PersistAllSpec._

class PersistAllSpec extends CassandraSpec(config) with ImplicitSender with AnyWordSpecLike with Matchers {

  private def stopAndWaitUntilTerminated(ref: ActorRef) = {
    watch(ref)
    ref ! PoisonPill
    expectTerminated(ref)
  }

  "A Cassandra journal" must {

    // reproducer of issue #869
    "write and replay with persistAll greater max-message-batch-size" in {
      val persistenceId = UUID.randomUUID().toString
      val processorAtomic = system.actorOf(Props(new ProcessorAtomic(persistenceId, self)))

      val N = 200

      processorAtomic ! (1 to N).map(n => s"a-$n").toList
      (1L to N).foreach { i =>
        expectMsgAllOf[Any](s"a-$i", i, false)
      }

      stopAndWaitUntilTerminated(processorAtomic)

      val testProbe = TestProbe()
      val processor2 = system.actorOf(Props(new ProcessorAtomic(persistenceId, testProbe.ref)))
      (1L to N).foreach { i =>
        testProbe.expectMsgAllOf[Any](s"a-$i", i, true)
      }
      processor2
    }
  }
}
