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

package org.apache.pekko.persistence.cassandra.reconciler

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.persistence.cassandra.PluginSettings
import pekko.Done
import pekko.persistence.cassandra.journal.TagWriter._
import scala.concurrent.duration._
import scala.concurrent.Future
import pekko.stream.scaladsl.Source
import pekko.actor.ExtendedActorSystem
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.event.Logging
import pekko.persistence.cassandra.journal.CassandraTagRecovery
import pekko.persistence.cassandra.Extractors
import pekko.util.Timeout
import pekko.stream.OverflowStrategy
import pekko.stream.scaladsl.Sink
import pekko.annotation.InternalApi
import pekko.serialization.SerializationExtension

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

  private implicit val sys: ActorSystem = system
  private val log = Logging(system, classOf[BuildTagViewForPersisetceId])
  private val serialization = SerializationExtension(system)

  private val queries: CassandraReadJournal =
    PersistenceQuery(system.asInstanceOf[ExtendedActorSystem])
      .readJournalFor[CassandraReadJournal]("pekko.persistence.cassandra.query")

  private implicit val flushTimeout: Timeout = Timeout(30.seconds)

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
