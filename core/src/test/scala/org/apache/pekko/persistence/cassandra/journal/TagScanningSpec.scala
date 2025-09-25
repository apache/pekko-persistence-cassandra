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

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec, TestTaggingActor }

object TagScanningSpec {
  private val config = ConfigFactory.parseString(s"""
      pekko.persistence.cassandra.events-by-tag.enabled = on
      pekko.persistence.cassandra.events-by-tag.scanning-flush-interval = 2s
      pekko.persistence.cassandra.journal.replay-filter.mode = off
      pekko.persistence.cassandra.log-queries = off
    """).withFallback(CassandraLifecycle.config)
}

class TagScanningSpec extends CassandraSpec(TagScanningSpec.config) {

  "Tag writing" must {
    "complete writes to tag scanning for many persistent actors" in {
      val nrActors = 25
      (0 until nrActors).foreach { i =>
        val ref = system.actorOf(TestTaggingActor.props(s"$i"))
        ref ! "msg"
      }

      awaitAssert {
        import scala.jdk.CollectionConverters._
        val expected = (0 until nrActors).map(n => (s"$n".toInt, 1L)).toList
        val scanning = cluster
          .execute(s"select * from $journalName.tag_scanning")
          .all()
          .asScala
          .toList
          .map(row => (row.getString("persistence_id"), row.getLong("sequence_nr")))
          .filterNot(_._1.startsWith("persistenceInit"))
          .map { case (pid, seqNr) => (pid.toInt, seqNr) } // sorting by pid makes the failure message easy to interpret
          .sortBy(_._1)
        scanning shouldEqual expected
      }
    }
  }
}
