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

package org.apache.pekko.persistence.cassandra.reconciler.javadsl

import java.util.concurrent.CompletionStage
import java.util.{ Set => JSet }

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.ApiMayChange
import pekko.persistence.cassandra.reconciler.ReconciliationSettings
import pekko.persistence.cassandra.reconciler.{ Reconciliation => ScalaReconciliation }
import pekko.stream.javadsl.Source

/**
 * Java API: For reconciling with tag_views table with the messages table. Can be used to fix data issues causes
 * by split brains or persistence ids running in multiple locations.
 *
 * Should not be run at the same time as an application.
 *
 * To support running in the same system as a journal the tag writers actor would need to be shared
 * and all the interleavings of the actor running at the same be considered.
 *
 * Each instance starts a tag writers actor that lives until [[Reconciliation#close]] is called, so
 * an instance should be closed once the reconciliation operations it was created for have completed.
 *
 * Corresponding Scala API is in [[pekko.persistence.cassandra.reconciler.Reconciliation]].
 */
@ApiMayChange
final class Reconciliation private (delegate: ScalaReconciliation) {

  def this(systemProvider: ClassicActorSystemProvider, settings: ReconciliationSettings) =
    this(new ScalaReconciliation(systemProvider, settings))

  def this(systemProvider: ClassicActorSystemProvider) =
    this(new ScalaReconciliation(systemProvider))

  /**
   * Scans the given tag and deletes all events for the provided persistence ids.
   * All events for a persistence id have to be deleted as not to leave gaps in the
   * tag pid sequence numbers.
   *
   * As this has to scan the tag views table for the given tag it is more efficient to
   * pass all the persistence ids of a tag in one call than to call this once per persistence id.
   */
  def deleteTagViewForPersistenceIds(persistenceIds: JSet[? <: String], tag: String): CompletionStage[Done] =
    delegate.deleteTagViewForPersistenceIds(persistenceIds.asScala.toSet[String], tag).asJava

  /**
   * Assumes that the tag views table contains no elements for the given persistence ids
   *  Either because tag_views and tag_progress have truncated for this given persistence id
   * or tag writing has never been enabled
   */
  def rebuildTagViewForPersistenceIds(persistenceId: String): CompletionStage[Done] =
    delegate.rebuildTagViewForPersistenceIds(persistenceId).asJava

  /**
   * Returns all the tags in the journal. This is not an efficient query for Cassandra so it is better
   * to calculate tags for calls to deleteTagViewForPersistenceId another way.
   *
   * Prefer to do batches of persistence ids at a time getting the tags just for that persistence id.
   */
  def allTags(): Source[String, NotUsed] =
    delegate.allTags().asJava

  /**
   * Select all the tags for the given persistence id. This may not return tags that
   * have just been used for the first time.
   */
  def tagsForPersistenceId(persistenceId: String): CompletionStage[JSet[String]] =
    delegate.tagsForPersistenceId(persistenceId).map(_.asJava)(ExecutionContext.parasitic).asJava

  /**
   * Truncate all tables and all metadata so that it can be rebuilt
   */
  def truncateTagView(): CompletionStage[Done] =
    delegate.truncateTagView().asJava

  def rebuildAllPersistenceIds(): CompletionStage[Done] =
    delegate.rebuildAllPersistenceIds().asJava

  /**
   * Stops the tag writers actor that this instance started. Without this the actor stays alive for
   * the lifetime of the `ActorSystem`, so call it once the reconciliation operations have completed.
   *
   * Any operation still in progress is aborted, so only close after the stages returned by the other
   * methods have completed. This instance must not be used again after it has been closed.
   *
   * Calling this more than once has no further effect.
   */
  def close(): Unit =
    delegate.close()
}
