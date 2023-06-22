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

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor._
import pekko.persistence.PersistentActor
import pekko.persistence.cassandra.CassandraLifecycle
import pekko.persistence.cassandra.CassandraSpec
import pekko.persistence.journal.Tagged
import com.typesafe.config.ConfigFactory

object ManyActorsLoadSpec {
  val config = ConfigFactory.parseString(s"""
      pekko.persistence.cassandra.journal.keyspace=ManyActorsLoadSpec
      pekko.persistence.cassandra.events-by-tag.enabled = on
      pekko.persistence.cassandra.journal.support-all-persistence-ids = off
      # increase this to 3s when benchmarking
      pekko.persistence.cassandra.events-by-tag.scanning-flush-interval = 1s
      #pekko.persistence.cassandra.log-queries = on
      pekko.persistence.cassandra.snapshot.keyspace=ManyActorsLoadSpecSnapshot
    """).withFallback(CassandraLifecycle.config)

  final case class Init(numberOfEvents: Int)
  case object InitDone
  private final case class Next(remaining: Int)
  final case class Delete(seqNr: Long)
  case object GetMetrics
  final case class Metrics(
      snapshotDuration: FiniteDuration,
      replayDuration1: FiniteDuration,
      replayDuration2: FiniteDuration,
      replayedEvents: Int,
      totalDuration: FiniteDuration)

  def props(persistenceId: String, tagging: Long => Set[String]): Props =
    Props(new ProcessorA(persistenceId, tagging))

  class ProcessorA(val persistenceId: String, tagging: Long => Set[String]) extends PersistentActor {

    def receiveRecover: Receive = {
      case _: String =>
    }

    def receiveCommand: Receive = {
      case s: String =>
        val tags = tagging(lastSequenceNr)
        val event =
          if (tags.isEmpty) s"event-$lastSequenceNr"
          else Tagged(s"event-$lastSequenceNr", tags)
        persist(event) { _ =>
          sender() ! s
        }
    }
  }

}

/**
 * Reproducer for issue #408
 */
class ManyActorsLoadSpec extends CassandraSpec(ManyActorsLoadSpec.config) {

  import ManyActorsLoadSpec._

  "Persisting from many actors" must {

    "not have performance drop when flushing scanning" in {
      val numberOfActors = 1000 // increase this to 10000 when benchmarking

      val rounds = 1 // increase this to 10 when benchmarking
      val deadline =
        Deadline.now + rounds * system.settings.config
          .getDuration("pekko.persistence.cassandra.events-by-tag.scanning-flush-interval", TimeUnit.MILLISECONDS)
          .millis + 2.seconds

      val tagging: Long => Set[String] = { _ =>
        Set.empty
      }
      //      val tagging: Long => Set[String] = { seqNr =>
      //        if (seqNr % 10 == 0) Set("blue")
      //        else if (seqNr % 17 == 0) Set("blue", "green")
      //        else Set.empty
      //      }

      val actors = (0 until numberOfActors).map { i =>
        system.actorOf(props(persistenceId = s"pid-$i", tagging))
      }.toVector

      actors.foreach(_ ! "init")
      receiveN(numberOfActors, 20.seconds)

      while (deadline.hasTimeLeft()) {
        val startTime = System.nanoTime()

        (0 until numberOfActors).foreach { i =>
          actors(i) ! "x"
        }
        receiveN(numberOfActors, 10.seconds)
        val duration = (System.nanoTime() - startTime).nanos
        println(s"Persisting $numberOfActors events from $numberOfActors actors took: ${duration.toMillis} ms")
      }

    }
  }

}
