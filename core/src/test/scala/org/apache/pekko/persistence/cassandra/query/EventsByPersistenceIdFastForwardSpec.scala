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

import java.util.UUID

import org.apache.pekko.persistence.PersistentRepr
import org.apache.pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec }
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import com.typesafe.config.ConfigFactory
import org.scalatest.time.{ Milliseconds, Seconds, Span }

object EventsByPersistenceIdFastForwardSpec {

  // separate from EventsByPersistenceIdWithControlSpec since it needs the refreshing enabled
  val config = ConfigFactory.parseString(s"""
    pekko.persistence.cassandra.journal.keyspace=EventsByPersistenceIdFastForwardSpec
    pekko.persistence.cassandra.query.refresh-interval = 250ms
    pekko.persistence.cassandra.query.max-result-size-query = 2
    pekko.persistence.cassandra.journal.target-partition-size = 15
    """).withFallback(CassandraLifecycle.config)
}

class EventsByPersistenceIdFastForwardSpec
    extends CassandraSpec(EventsByPersistenceIdFastForwardSpec.config)
    with DirectWriting {

  override implicit val patience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Milliseconds))

  "be able to fast forward when currently looking for missing sequence number" in {
    val w1 = UUID.randomUUID().toString
    val evt1 = PersistentRepr("e-1", 1L, "f", "", writerUuid = w1)
    writeTestEvent(evt1)

    val src = queries.eventsByPersistenceIdWithControl("f", 0L, Long.MaxValue)
    val (futureControl, probe) = src.map(_.event).toMat(TestSink.probe[Any])(Keep.both).run()
    val control = futureControl.futureValue
    probe.request(5)

    val evt3 = PersistentRepr("e-3", 3L, "f", "", writerUuid = w1)
    writeTestEvent(evt3)

    probe.expectNext("e-1")

    system.log.debug("Sleeping for query to go into look-for-missing-seqnr-mode")
    Thread.sleep(2000)

    // then we fast forward past the gap
    control.fastForward(3L)
    probe.expectNext("e-3")

    val evt2 = PersistentRepr("e-2", 2L, "f", "", writerUuid = w1)
    val evt4 = PersistentRepr("e-4", 4L, "f", "", writerUuid = w1)
    writeTestEvent(evt2)
    writeTestEvent(evt4)
    probe.expectNext("e-4")

    probe.cancel()
  }
}
