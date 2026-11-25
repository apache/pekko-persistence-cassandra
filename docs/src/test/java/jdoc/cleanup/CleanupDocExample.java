/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package jdoc.cleanup;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.cassandra.cleanup.CleanupSettings;
import org.apache.pekko.persistence.cassandra.cleanup.javadsl.Cleanup;
import org.apache.pekko.persistence.cassandra.query.javadsl.CassandraReadJournal;
import org.apache.pekko.persistence.query.PersistenceQuery;

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
        .mapAsync(persistenceIdParallelism, pid -> cleanup.cleanupBeforeSnapshot(pid, 2))
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
            pid -> cleanup.cleanupBeforeSnapshot(pid, 2, keepAfter.toInstant().toEpochMilli()))
        .run(system);

    // #cleanup

    // a list of persistence ids that is typed with a wildcard is accepted
    List<? extends String> persistenceIds = Arrays.asList("pid1", "pid2");
    CompletionStage<Done> deleted = cleanup.deleteAll(persistenceIds, false);
    CompletionStage<Optional<SnapshotMetadata>> oldestSnapshot =
        cleanup.deleteBeforeSnapshot("pid1", 2);
    Duration operationTimeout =
        new CleanupSettings(
                system.settings().config().getConfig("pekko.persistence.cassandra.cleanup"))
            .getOperationTimeout();
  }
}
