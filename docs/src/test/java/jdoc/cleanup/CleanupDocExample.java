/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package jdoc.cleanup;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.cassandra.cleanup.Cleanup;
import org.apache.pekko.persistence.cassandra.query.javadsl.CassandraReadJournal;
import org.apache.pekko.persistence.query.PersistenceQuery;
import scala.compat.java8.FutureConverters;

public class CleanupDocExample {

  public static void example() {

    ActorSystem system = null;

    // #cleanup
    CassandraReadJournal queries =
        PersistenceQuery.get(system)
            .getReadJournalFor(CassandraReadJournal.class, CassandraReadJournal.Identifier());
    Cleanup cleanup = new Cleanup(system);

    int persistenceIdParallelism = 10;

    // forall persistence ids, keep two snapshots and delete all events before the oldest kept
    // snapshot
    queries
        .currentPersistenceIds()
        .mapAsync(
            persistenceIdParallelism,
            pid -> FutureConverters.toJava(cleanup.cleanupBeforeSnapshot(pid, 2)))
        .run(system);

    // forall persistence ids, keep everything after the provided unix timestamp, if there aren't
    // enough snapshots after this time
    // go back before the timestamp to find snapshot to delete before
    // this operation is more expensive that the one above
    ZonedDateTime keepAfter = ZonedDateTime.now().minus(1, ChronoUnit.MONTHS);
    queries
        .currentPersistenceIds()
        .mapAsync(
            persistenceIdParallelism,
            pid ->
                FutureConverters.toJava(
                    cleanup.cleanupBeforeSnapshot(pid, 2, keepAfter.toInstant().toEpochMilli())))
        .run(system);

    // #cleanup

  }
}
