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

import org.apache.pekko
import pekko.persistence.cassandra.TestTaggingActor.Ack
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.persistence.query.{ NoOffset, PersistenceQuery }
import pekko.stream.scaladsl._
import pekko.stream.testkit.scaladsl.TestSink
import com.typesafe.config.ConfigFactory
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.duration._

object CassandraEventsByTagLoadSpec {

  val config = ConfigFactory.parseString(s"""
       pekko.persistence.cassandra {
         log-queries = off
         events-by-tag {
            max-message-batch-size = 25
            bucket-size = "Minute"
         }
         snapshot.keyspace=CassandraEventsByTagLoadSpecSnapshot
       }
       pekko.actor.serialize-messages=off
    """).withFallback(CassandraLifecycle.config)
}

class CassandraEventsByTagLoadSpec extends CassandraSpec(CassandraEventsByTagLoadSpec.config) {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(60, Seconds), interval = Span(5, Seconds))

  val nrPersistenceIds = 50L
  val eventTags = Set("orange", "green", "red")
  val messagesPerPersistenceId = 500L
  val veryLongWait = 60.seconds

  "Events by tag" must {
    "Events should come in sequence number order for each persistence id" in {
      val refs = (1L to nrPersistenceIds).map(i => {
        system.actorOf(TestTaggingActor.props(s"p-$i", eventTags))
      })

      (1L to messagesPerPersistenceId).foreach { i =>
        refs.foreach { ref =>
          ref ! s"e-$i"
          expectMsg(Ack)
        }
      }

      val readJournal =
        PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

      eventTags.foreach { tag =>
        try {
          validateTagStream(readJournal)(tag)
        } catch {
          case e: Throwable =>
            system.log.error("IMPORTANT:: Failed, retrying to see if it was eventual consistency")
            system.log.error("IMPORTANT:: " + e.getMessage)
            validateTagStream(readJournal)(tag)
            system.log.info("IMPORTANT:: Passed second time")
            throw new RuntimeException("Only passed the second time")
        }

      }
    }
  }

  private def validateTagStream(readJournal: CassandraReadJournal)(tag: String): Unit = {
    system.log.info(s"Validating tag $tag")
    val probe = readJournal.eventsByTag("orange", NoOffset).toMat(TestSink.probe)(Keep.right).run()
    var sequenceNrsPerPid = Map[String, Long]()
    var allReceived: Map[String, List[Long]] = Map.empty.withDefaultValue(List.empty)
    probe.request(messagesPerPersistenceId * nrPersistenceIds)

    (1L to (messagesPerPersistenceId * nrPersistenceIds)).foreach { i: Long =>
      val event =
        try {
          probe.expectNext(veryLongWait)
        } catch {
          case e: AssertionError =>
            system.log.error(e, s"Failed to get event: $i")
            allReceived.filter(_._2.size != messagesPerPersistenceId).foreach(p => system.log.info("{}", p))
            throw e
        }

      allReceived += (event.persistenceId -> (event.sequenceNr :: allReceived(event.persistenceId)))
      var fail = false
      sequenceNrsPerPid.get(event.persistenceId) match {
        case Some(currentSeqNr) =>
          if (event.sequenceNr != currentSeqNr + 1) {
            fail = true
            system.log.error(
              s"Out of order sequence nrs for pid ${event.persistenceId}. This was event nr [$i]. Expected ${currentSeqNr + 1}, got: ${event.sequenceNr}")
          }
          sequenceNrsPerPid += (event.persistenceId -> event.sequenceNr)
        case None =>
          event.sequenceNr should equal(1)
          sequenceNrsPerPid += (event.persistenceId -> event.sequenceNr)
      }
    }
  }

}
