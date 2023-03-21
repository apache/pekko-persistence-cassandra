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

import org.apache.pekko.NotUsed
import org.apache.pekko.persistence.cassandra.TestTaggingActor.{ Ack, Crash }
import org.apache.pekko.persistence.query.{ EventEnvelope, NoOffset }
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.testkit.scaladsl.TestSink

import scala.concurrent.duration._

class EventsByTagCrashSpec extends CassandraSpec(EventsByTagRestartSpec.config) {

  val waitTime = 100.milliseconds

  "EventsByTag" must {

    "should handle crashes of the persistent actor" in {
      // crash the actor many times, persist 5 events each time
      val crashEvery = 5
      val crashNr = 20
      val msgs = crashEvery * crashNr
      val p2 = system.actorOf(TestTaggingActor.props("p2", Set("blue")))
      (1 to msgs).foreach { cn =>
        if (cn % crashEvery == 0) {
          p2 ! Crash
        }
        val msg = s"msg $cn"
        p2 ! msg
        expectMsg(Ack)
      }
      val blueTags: Source[EventEnvelope, NotUsed] = queryJournal.eventsByTag(tag = "blue", offset = NoOffset)
      val tagProbe = blueTags.runWith(TestSink.probe[EventEnvelope](system))
      (1L to msgs).foreach { m =>
        val expected = s"msg $m"
        tagProbe.request(1)
        tagProbe.expectNext().event shouldEqual expected
      }
      tagProbe.expectNoMessage(250.millis)
      tagProbe.cancel()

    }
  }
}
