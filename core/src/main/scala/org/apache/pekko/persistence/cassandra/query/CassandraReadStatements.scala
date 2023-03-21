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

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.persistence.cassandra.PluginSettings

/**
 * INTERNAL API
 */
@InternalApi private[pekko] trait CassandraReadStatements {

  def settings: PluginSettings
  private def journalSettings = settings.journalSettings
  private def eventsByTagSettings = settings.eventsByTagSettings

  private def tableName = s"${journalSettings.keyspace}.${journalSettings.table}"
  private def tagViewTableName = s"${journalSettings.keyspace}.${eventsByTagSettings.tagTable.name}"
  private def allPersistenceIdsTableName = s"${journalSettings.keyspace}.${journalSettings.allPersistenceIdsTable}"

  def selectAllPersistenceIds =
    s"""
      SELECT persistence_id FROM $allPersistenceIdsTableName
     """

  def selectDistinctPersistenceIds =
    s"""
      SELECT DISTINCT persistence_id, partition_nr FROM $tableName
     """

  def selectEventsFromTagViewWithUpperBound =
    s"""
      SELECT * FROM $tagViewTableName WHERE
        tag_name = ?  AND
        timebucket = ? AND
        timestamp > ? AND
        timestamp < ?
        ORDER BY timestamp ASC
     """.stripMargin

  def selectTagSequenceNrs =
    s"""
    SELECT persistence_id, tag_pid_sequence_nr, timestamp
    FROM $tagViewTableName WHERE
    tag_name = ? AND
    timebucket = ? AND
    timestamp > ? AND
    timestamp <= ?"""
}
