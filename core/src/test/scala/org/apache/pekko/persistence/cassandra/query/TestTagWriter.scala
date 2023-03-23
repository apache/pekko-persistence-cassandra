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

package org.apache.pekko.persistence.cassandra.query

import java.nio.ByteBuffer
import java.time.{ LocalDateTime, ZoneOffset }
import java.util.UUID

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.persistence.PersistentRepr
import pekko.persistence.cassandra.BucketSize
import pekko.persistence.cassandra.EventsByTagSettings
import pekko.persistence.cassandra.PluginSettings
import pekko.persistence.cassandra.formatOffset
import pekko.persistence.cassandra.journal._
import pekko.serialization.Serialization
import pekko.serialization.Serializers
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.uuid.Uuids

private[pekko] trait TestTagWriter {
  def system: ActorSystem
  def cluster: CqlSession
  val serialization: Serialization
  val settings: PluginSettings
  final def journalSettings: JournalSettings = settings.journalSettings
  final def eventsByTagSettings: EventsByTagSettings = settings.eventsByTagSettings

  lazy val (preparedWriteTagMessage, preparedWriteTagMessageWithMeta) = {
    val writeStatements: CassandraJournalStatements = new CassandraJournalStatements(settings)
    (cluster.prepare(writeStatements.writeTags(false)), cluster.prepare(writeStatements.writeTags(true)))
  }

  def clearAllEvents(): Unit = {
    cluster.execute(s"truncate ${journalSettings.keyspace}.${eventsByTagSettings.tagTable.name}")
  }

  def writeTaggedEvent(
      time: LocalDateTime,
      pr: PersistentRepr,
      tags: Set[String],
      tagPidSequenceNr: Long,
      bucketSize: BucketSize): Unit = {
    val timestamp = time.toInstant(ZoneOffset.UTC).toEpochMilli
    write(pr, tags, tagPidSequenceNr, uuid(timestamp), bucketSize)
  }

  def writeTaggedEvent(
      persistent: PersistentRepr,
      tags: Set[String],
      tagPidSequenceNr: Long,
      bucketSize: BucketSize): Unit = {
    val nowUuid = Uuids.timeBased()
    write(persistent, tags, tagPidSequenceNr, nowUuid, bucketSize)
  }

  def writeTaggedEvent(
      persistent: PersistentRepr,
      tags: Set[String],
      tagPidSequenceNr: Long,
      uuid: UUID,
      bucketSize: BucketSize): Unit =
    write(persistent, tags, tagPidSequenceNr, uuid, bucketSize)

  private def write(
      pr: PersistentRepr,
      tags: Set[String],
      tagPidSequenceNr: Long,
      uuid: UUID,
      bucketSize: BucketSize): Unit = {
    val event = pr.payload.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(event)
    val serialized = ByteBuffer.wrap(serialization.serialize(event).get)
    val serManifest = Serializers.manifestFor(serializer, pr)
    val timeBucket = TimeBucket(Uuids.unixTimestamp(uuid), bucketSize)

    tags.foreach(tag => {
      val bs = preparedWriteTagMessage
        .bind()
        .setString("tag_name", tag)
        .setLong("timebucket", timeBucket.key)
        .setUuid("timestamp", uuid)
        .setLong("tag_pid_sequence_nr", tagPidSequenceNr)
        .setByteBuffer("event", serialized)
        .setString("event_manifest", pr.manifest)
        .setString("persistence_id", pr.persistenceId)
        .setInt("ser_id", serializer.identifier)
        .setString("ser_manifest", serManifest)
        .setString("writer_uuid", "ManualWrite")
        .setLong("sequence_nr", pr.sequenceNr)
      cluster.execute(bs)
    })

    system.log.debug(
      "Written event: {} Uuid: {} Timebucket: {} TagPidSeqNr: {}",
      pr.payload,
      formatOffset(uuid),
      timeBucket,
      tagPidSequenceNr)
  }
}
