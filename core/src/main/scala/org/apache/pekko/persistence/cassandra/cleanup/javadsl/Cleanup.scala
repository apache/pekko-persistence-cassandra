/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.cassandra.cleanup.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.{ List => JList }

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.Done
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.persistence.SnapshotMetadata
import pekko.persistence.cassandra.cleanup.CleanupSettings
import pekko.persistence.cassandra.cleanup.{ Cleanup => ScalaCleanup }

/**
 * Java API: Tool for deleting all events and/or snapshots for a given list of `persistenceIds` without using
 * persistent actors. It's important that the actors with corresponding `persistenceId` are not running
 * at the same time as using the tool.
 *
 * If `neverUsePersistenceIdAgain` is `true` then the highest used sequence number is deleted and
 * the `persistenceId` should not be used again, since it would be confusing to reuse the same sequence
 * numbers for new events.
 *
 * When a list of `persistenceIds` are given they are deleted sequentially in the order
 * of the list. It's possible to parallelize the deletes by running several cleanup operations
 * at the same time operating on different sets of `persistenceIds`.
 *
 * The tagged event operations start an actor that lives until [[Cleanup#close]] is called, so an
 * instance should be closed once the cleanup operations it was created for have completed.
 *
 * Corresponding Scala API is in [[pekko.persistence.cassandra.cleanup.Cleanup]].
 */
@ApiMayChange
final class Cleanup(delegate: ScalaCleanup) {

  def this(systemProvider: ClassicActorSystemProvider, settings: CleanupSettings) =
    this(new ScalaCleanup(systemProvider, settings))

  def this(systemProvider: ClassicActorSystemProvider) =
    this(new ScalaCleanup(systemProvider))

  /**
   * Requires Cassandra 3+
   *
   * Keep all snapshots that occurred after `keepAfter`.
   * If fewer than `snapshotsToKeep` occurred after `keepAfter` at least that many
   * are kept. Setting this to 1 ensures that at least snapshot is kept even if it
   * is older than the `keepAfter`
   *
   * If only N number of snapshot should be kept prefer overload without timestamp
   * as it is more efficient.
   *
   * The returned snapshot metadata can be used to issue deletes for events older than the oldest
   * snapshot.
   *
   * @return the snapshot meta of the oldest remaining snapshot. Empty if there are no snapshots
   */
  def deleteBeforeSnapshot(
      persistenceId: String,
      snapshotsToKeep: Int,
      keepAfterUnixTimestamp: Long): CompletionStage[Optional[SnapshotMetadata]] =
    delegate
      .deleteBeforeSnapshot(persistenceId, snapshotsToKeep, keepAfterUnixTimestamp)
      .map(_.toJava)(ExecutionContext.parasitic)
      .asJava

  /**
   * Requires Cassandra 3+
   *
   * Keep N snapshots and delete all older snapshots along.
   *
   * This operation is much cheaper than including the timestamp because it can use the primary key and limit.
   *
   * @return the snapshot meta of the oldest remaining snapshot. Empty if there are no snapshots. This can be used to delete events from before the snapshot.
   */
  def deleteBeforeSnapshot(
      persistenceId: String,
      maxSnapshotsToKeep: Int): CompletionStage[Optional[SnapshotMetadata]] =
    delegate.deleteBeforeSnapshot(persistenceId, maxSnapshotsToKeep).map(_.toJava)(ExecutionContext.parasitic).asJava

  /**
   * Requires Cassandra 3+
   *
   * Delete all events before a sequenceNr for the given persistence id.
   *
   * WARNING: deleting events is generally discouraged in event sourced systems.
   *          once deleted the event by tag view can not be re-built
   *
   * @param persistenceId the persistence id to delete for
   * @param toSequenceNr sequence nr (inclusive) to delete up to
   */
  def deleteEventsTo(persistenceId: String, toSequenceNr: Long): CompletionStage[Done] =
    delegate.deleteEventsTo(persistenceId, toSequenceNr).asJava

  /**
   * Requires Cassandra 3+
   *
   * Deletes all but the last N snapshots and deletes all events before this snapshot
   * Does not delete from the tag_views table
   *
   * WARNING: deleting events is generally discouraged in event sourced systems.
   *          once deleted the event by tag view can not be re-built
   */
  def cleanupBeforeSnapshot(persistenceId: String, nrSnapshotsToKeep: Int): CompletionStage[Done] =
    delegate.cleanupBeforeSnapshot(persistenceId, nrSnapshotsToKeep).asJava

  /**
   * Requires Cassandra 3+
   *
   * Deletes all events for the given persistence id from before the first after keepAfter.
   * If there are not enough snapshots to satisfy nrSnapshotsToKeep then snapshots before
   * keepAfter will also be kept.
   *
   * WARNING: deleting events is generally discouraged in event sourced systems.
   *          once deleted the event by tag view can not be re-built
   */
  def cleanupBeforeSnapshot(persistenceId: String, nrSnapshotsToKeep: Int, keepAfter: Long): CompletionStage[Done] =
    delegate.cleanupBeforeSnapshot(persistenceId, nrSnapshotsToKeep, keepAfter).asJava

  /**
   * Requires Cassandra 3+
   *
   * See single persistenceId overload for what is done for each persistence id
   */
  def cleanupBeforeSnapshot(persistenceIds: JList[_ <: String], nrSnapshotsToKeep: Int): CompletionStage[Done] =
    delegate.cleanupBeforeSnapshot(toScala(persistenceIds), nrSnapshotsToKeep).asJava

  /**
   * Requires Cassandra 3+
   *
   * See single persistenceId overload for what is done for each persistence id
   */
  def cleanupBeforeSnapshot(
      persistenceIds: JList[_ <: String],
      nrSnapshotsToKeep: Int,
      keepAfter: Long): CompletionStage[Done] =
    delegate.cleanupBeforeSnapshot(toScala(persistenceIds), nrSnapshotsToKeep, keepAfter).asJava

  /**
   * Delete everything related to the given list of `persistenceIds`. All events, tagged events, and
   * snapshots are deleted.
   */
  def deleteAll(persistenceIds: JList[_ <: String], neverUsePersistenceIdAgain: Boolean): CompletionStage[Done] =
    delegate.deleteAll(toScala(persistenceIds), neverUsePersistenceIdAgain).asJava

  /**
   * Delete everything related to one single `persistenceId`. All events,  tagged events, and
   * snapshots are deleted.
   */
  def deleteAll(persistenceId: String, neverUsePersistenceIdAgain: Boolean): CompletionStage[Done] =
    delegate.deleteAll(persistenceId, neverUsePersistenceIdAgain).asJava

  /**
   * Delete all events related to the given list of `persistenceIds`. Snapshots are not deleted.
   */
  def deleteAllEvents(persistenceIds: JList[_ <: String], neverUsePersistenceIdAgain: Boolean): CompletionStage[Done] =
    delegate.deleteAllEvents(toScala(persistenceIds), neverUsePersistenceIdAgain).asJava

  /**
   * Delete all events related to one single `persistenceId`. Snapshots are not deleted.
   */
  def deleteAllEvents(persistenceId: String, neverUsePersistenceIdAgain: Boolean): CompletionStage[Done] =
    delegate.deleteAllEvents(persistenceId, neverUsePersistenceIdAgain).asJava

  /**
   * Delete all events from `tag_views` table related to the given list of `persistenceIds`.
   * Events in `messages` (journal) table are not deleted and snapshots are not deleted.
   */
  def deleteAllTaggedEvents(persistenceIds: JList[_ <: String]): CompletionStage[Done] =
    delegate.deleteAllTaggedEvents(toScala(persistenceIds)).asJava

  /**
   * Delete all events from `tag_views` table related to to one single `persistenceId`.
   * Events in `messages` (journal) table are not deleted and snapshots are not deleted.
   */
  def deleteAllTaggedEvents(persistenceId: String): CompletionStage[Done] =
    delegate.deleteAllTaggedEvents(persistenceId).asJava

  /**
   * Delete all snapshots related to the given list of `persistenceIds`. Events are not deleted.
   */
  def deleteAllSnapshots(persistenceIds: JList[_ <: String]): CompletionStage[Done] =
    delegate.deleteAllSnapshots(toScala(persistenceIds)).asJava

  /**
   * Delete all snapshots related to one single `persistenceId`. Events are not deleted.
   */
  def deleteAllSnapshots(persistenceId: String): CompletionStage[Done] =
    delegate.deleteAllSnapshots(persistenceId).asJava

  /**
   * Releases the resources that the tagged event operations acquired. Without this the actor that
   * they start stays alive for the lifetime of the `ActorSystem`, so call this once the cleanup
   * operations have completed.
   *
   * Any operation still in progress is aborted, so only close after the stages returned by the
   * other methods have completed. This instance must not be used again after it has been closed.
   *
   * Calling this more than once has no further effect, and it does nothing if no tagged event
   * operation was used.
   */
  def close(): Unit =
    delegate.close()

  // copies, so that later changes to the caller's list can't affect an operation in progress
  private def toScala(persistenceIds: JList[_ <: String]): List[String] =
    persistenceIds.asScala.toList
}
