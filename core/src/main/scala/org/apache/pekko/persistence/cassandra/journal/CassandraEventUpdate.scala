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

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.persistence.cassandra.PluginSettings
import pekko.persistence.cassandra.journal.CassandraJournal.{ Serialized, TagPidSequenceNr }
import pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import pekko.util.ccompat.JavaConverters._
import com.datastax.oss.driver.api.core.cql.{ PreparedStatement, Row, Statement }

import scala.concurrent.{ ExecutionContext, Future }
import java.lang.{ Long => JLong }

/** INTERNAL API */
@InternalApi private[pekko] trait CassandraEventUpdate {

  private[pekko] val session: CassandraSession
  private[pekko] def settings: PluginSettings
  private[pekko] implicit val ec: ExecutionContext
  private[pekko] val log: LoggingAdapter

  private def journalSettings = settings.journalSettings
  private lazy val journalStatements = new CassandraJournalStatements(settings)
  lazy val psUpdateMessage: Future[PreparedStatement] = session.prepare(journalStatements.updateMessagePayloadAndTags)
  lazy val psSelectTagPidSequenceNr: Future[PreparedStatement] =
    session.prepare(journalStatements.selectTagPidSequenceNr)
  lazy val psUpdateTagView: Future[PreparedStatement] = session.prepare(journalStatements.updateMessagePayloadInTagView)
  lazy val psSelectMessages: Future[PreparedStatement] = session.prepare(journalStatements.selectMessages)

  /**
   * Update the given event in the messages table and the tag_views table.
   *
   * Does not support changing tags in anyway. The tags field is ignored.
   */
  def updateEvent(event: Serialized): Future[Done] =
    for {
      (partitionNr, existingTags) <- findEvent(event)
      psUM <- psUpdateMessage
      e = event.copy(tags = existingTags) // do not allow updating of tags
      _ <- session.executeWrite(prepareUpdate(psUM, e, partitionNr))
      _ <- Future.traverse(existingTags) { tag =>
        updateEventInTagViews(event, tag)
      }
    } yield Done

  private def findEvent(s: Serialized): Future[(Long, Set[String])] = {
    val firstPartition = partitionNr(s.sequenceNr, journalSettings.targetPartitionSize)
    for {
      ps <- psSelectMessages
      row <- findEvent(ps, s.persistenceId, s.sequenceNr, firstPartition)
    } yield (row.getLong("partition_nr"), row.getSet[String]("tags", classOf[String]).asScala.toSet)
  }

  /**
   * Events are nearly always in a deterministic partition. However they can be in the
   * N + 1 partition if a large atomic write was done.
   */
  private def findEvent(ps: PreparedStatement, pid: String, sequenceNr: Long, partitionNr: Long): Future[Row] =
    session
      .selectOne(ps.bind(pid, partitionNr: JLong, sequenceNr: JLong, sequenceNr: JLong))
      .flatMap {
        case Some(row) => Future.successful(Some(row))
        case None =>
          session.selectOne(pid, partitionNr + 1: JLong, sequenceNr: JLong, sequenceNr: JLong)
      }
      .map {
        case Some(row) => row
        case None =>
          throw new RuntimeException(
            s"Unable to find event: Pid: [$pid] SequenceNr: [$sequenceNr] partitionNr: [$partitionNr]")
      }

  private def updateEventInTagViews(event: Serialized, tag: String): Future[Done] =
    psSelectTagPidSequenceNr
      .flatMap { ps =>
        val bound = ps
          .bind()
          .setString("tag_name", tag)
          .setLong("timebucket", event.timeBucket.key)
          .setUuid("timestamp", event.timeUuid)
          .setString("persistence_id", event.persistenceId)
        session.selectOne(bound)
      }
      .map {
        case Some(r) => r.getLong("tag_pid_sequence_nr")
        case None =>
          throw new RuntimeException(
            s"no tag pid sequence nr. Pid ${event.persistenceId}. Tag: $tag. SequenceNr: ${event.sequenceNr}")
      }
      .flatMap { tagPidSequenceNr =>
        updateEventInTagViews(event, tag, tagPidSequenceNr)
      }

  private def updateEventInTagViews(event: Serialized, tag: String, tagPidSequenceNr: TagPidSequenceNr): Future[Done] =
    psUpdateTagView.flatMap { ps =>
      // primary key
      val bound = ps
        .bind()
        .setString("tag_name", tag)
        .setLong("timebucket", event.timeBucket.key)
        .setUuid("timestamp", event.timeUuid)
        .setString("persistence_id", event.persistenceId)
        .setLong("tag_pid_sequence_nr", tagPidSequenceNr)
        .setByteBuffer("event", event.serialized)
        .setString("ser_manifest", event.serManifest)
        .setInt("ser_id", event.serId)
        .setString("event_manifest", event.eventAdapterManifest)

      session.executeWrite(bound)
    }

  private def prepareUpdate(ps: PreparedStatement, s: Serialized, partitionNr: Long): Statement[_] = {
    // primary key
    ps.bind()
      .setString("persistence_id", s.persistenceId)
      .setLong("partition_nr", partitionNr)
      .setLong("sequence_nr", s.sequenceNr)
      .setUuid("timestamp", s.timeUuid)
      .setInt("ser_id", s.serId)
      .setString("ser_manifest", s.serManifest)
      .setString("event_manifest", s.eventAdapterManifest)
      .setByteBuffer("event", s.serialized)
      .setSet("tags", s.tags.asJava, classOf[String])
  }
}
