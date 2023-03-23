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

package org.apache.pekko.persistence.cassandra.reconciler

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.persistence.cassandra.PluginSettings
import pekko.Done
import pekko.event.Logging
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.annotation.InternalApi
import pekko.persistence.query.NoOffset
import pekko.persistence.cassandra.journal.TimeBucket
import pekko.stream.scaladsl.Sink
import scala.concurrent.Future

/**
 * Deletes tagged events for a persistence id.
 *
 * Walks the events by tags table for the given tag, filters by persistence id, and
 * issues deletes one at a time.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class DeleteTagViewForPersistenceId(
    persistenceIds: Set[String],
    tag: String,
    system: ActorSystem,
    session: ReconciliationSession,
    settings: PluginSettings,
    queries: CassandraReadJournal) {
  private val log = Logging(system, s"DeleteTagView($tag)")
  private implicit val sys = system
  import system.dispatcher

  def execute(): Future[Done] = {
    queries
      .currentEventsByTagInternal(tag, NoOffset)
      .filter(persistenceIds contains _.persistentRepr.persistenceId)
      // Make the parallelism configurable?
      .mapAsync(1) { uuidPr =>
        val bucket = TimeBucket(uuidPr.offset, settings.eventsByTagSettings.bucketSize)
        val timestamp = uuidPr.offset
        val persistenceId = uuidPr.persistentRepr.persistenceId
        val tagPidSequenceNr = uuidPr.tagPidSequenceNr
        log.debug("Issuing delete {} {} {} {}", persistenceId, bucket, timestamp, tagPidSequenceNr)
        session.deleteFromTagView(tag, bucket, timestamp, persistenceId, tagPidSequenceNr)
      }
      .runWith(Sink.ignore)
      .flatMap(_ =>
        Future.traverse(persistenceIds) { pid =>
          val progress = session.deleteTagProgress(tag, pid)
          val scanning = session.deleteTagScannning(pid)
          for {
            _ <- progress
            _ <- scanning
          } yield Done
        })
      .map(_ => Done)
  }

}
