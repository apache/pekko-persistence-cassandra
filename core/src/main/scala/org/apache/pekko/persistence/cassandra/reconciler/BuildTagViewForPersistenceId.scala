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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.persistence.cassandra.PluginSettings
import org.apache.pekko.Done
import org.apache.pekko.persistence.cassandra.journal.TagWriter._
import scala.concurrent.duration._
import scala.concurrent.Future
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import org.apache.pekko.event.Logging
import org.apache.pekko.persistence.cassandra.journal.CassandraTagRecovery
import org.apache.pekko.persistence.cassandra.Extractors
import org.apache.pekko.util.Timeout
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.serialization.SerializationExtension

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class BuildTagViewForPersisetceId(
    persistenceId: String,
    system: ActorSystem,
    recovery: CassandraTagRecovery,
    settings: PluginSettings) {

  import system.dispatcher

  private implicit val sys = system
  private val log = Logging(system, classOf[BuildTagViewForPersisetceId])
  private val serialization = SerializationExtension(system)

  private val queries: CassandraReadJournal =
    PersistenceQuery(system.asInstanceOf[ExtendedActorSystem])
      .readJournalFor[CassandraReadJournal]("pekko.persistence.cassandra.query")

  private implicit val flushTimeout = Timeout(30.seconds)

  def reconcile(flushEvery: Int = 1000): Future[Done] = {

    val recoveryPrep = for {
      tp <- recovery.lookupTagProgress(persistenceId)
      _ <- recovery.setTagProgress(persistenceId, tp)
    } yield tp

    Source
      .futureSource(recoveryPrep.map((tp: Map[String, TagProgress]) => {
        log.debug("[{}] Rebuilding tag view table from: [{}]", persistenceId, tp)
        queries
          .eventsByPersistenceId(
            persistenceId,
            0,
            Long.MaxValue,
            Long.MaxValue,
            None,
            settings.journalSettings.readProfile,
            "BuildTagViewForPersistenceId",
            extractor = Extractors.rawEvent(settings.eventsByTagSettings.bucketSize, serialization, system))
          .map(recovery.sendMissingTagWriteRaw(tp, actorRunning = false))
          .buffer(flushEvery, OverflowStrategy.backpressure)
          .mapAsync(1)(_ => recovery.flush(flushTimeout))
      }))
      .runWith(Sink.ignore)

  }

}
