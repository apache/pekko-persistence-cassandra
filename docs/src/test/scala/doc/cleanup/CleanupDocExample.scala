/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package doc.cleanup

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.persistence.cassandra.cleanup.Cleanup
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.persistence.query.PersistenceQuery

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object CleanupDocExample {

  implicit val system: ActorSystem = ???

  // #cleanup
  val queries = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  val cleanup = new Cleanup(system)

  //  how many persistence ids to operate on in parallel
  val persistenceIdParallelism = 10

  // forall persistence ids, keep two snapshots and delete all events before the oldest kept snapshot
  queries.currentPersistenceIds().mapAsync(persistenceIdParallelism)(pid => cleanup.cleanupBeforeSnapshot(pid, 2)).run()

  // forall persistence ids, keep everything after the provided unix timestamp, if there aren't enough snapshots after this time
  // go back before the timestamp to find snapshot to delete before
  // this operation is more expensive that the one above
  val keepAfter = ZonedDateTime.now().minus(1, ChronoUnit.MONTHS);
  queries
    .currentPersistenceIds()
    .mapAsync(persistenceIdParallelism)(pid => cleanup.cleanupBeforeSnapshot(pid, 2, keepAfter.toInstant.toEpochMilli))
    .run()

  // #cleanup

}
