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

package org.apache.pekko.persistence.cassandra

import org.apache.pekko
import pekko.persistence.cassandra.query.TestActor
import pekko.persistence.journal.Tagged
import pekko.persistence.query.NoOffset
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink

import scala.collection.immutable
import scala.concurrent.Future

class EventsByTagStressSpec extends CassandraSpec(s"""
    pekko.persistence.cassandra {
      events-by-tag {
        max-message-batch-size = 25
      }
    }
  """) {

  implicit val ec = system.dispatcher

  val writers = 10
  val readers = 20
  val messages = 5000

  "EventsByTag" must {

    "work under load" in {
      val pas = (0 until writers).map { i =>
        system.actorOf(TestActor.props(s"pid$i"))
      }

      val eventsByTagQueries: immutable.Seq[(Int, TestSubscriber.Probe[(String, Int)])] = (0 until readers).map { i =>
        val probe = queryJournal
          .eventsByTag("all", NoOffset)
          .map(i => {
            (i.persistenceId, i.event.asInstanceOf[Int])
          })
          .runWith(TestSink.probe)
        (i, probe)
      }

      system.log.info("Started events by tag queries")

      val writes: Future[Unit] = Future {
        system.log.info("Sending messages")
        (0 until messages).foreach { i =>
          pas.foreach(ref => {
            ref ! Tagged(i, Set("all"))
            expectMsg(s"$i-done")
          })
        }
        system.log.info("Sent messages")
      }
      writes.onComplete(result => system.log.info("{}", result))

      system.log.info("Reading messages")
      var latestValues: Map[(Int, String), Int] = Map.empty.withDefault(_ => -1)
      (0 until messages).foreach { _ =>
        (0 until writers).foreach { _ =>
          eventsByTagQueries.foreach {
            case (probeNr, probe) =>
              // should be in order per persistence id per probe
              val (pid, msg) = probe.requestNext()
              latestValues((probeNr, pid)) shouldEqual (msg - 1)
              latestValues += (probeNr, pid) -> msg
          }
        }
      }
      system.log.info("Received all messages {}", latestValues)
    }

  }
}
