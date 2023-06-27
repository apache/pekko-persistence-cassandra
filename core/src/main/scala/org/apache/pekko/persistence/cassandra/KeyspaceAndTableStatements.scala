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

package org.apache.pekko.persistence.cassandra

import scala.collection.immutable

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider

/**
 * Definitions of keyspace and table creation statements.
 */
class KeyspaceAndTableStatements(
    systemProvider: ClassicActorSystemProvider,
    configPath: String,
    settings: PluginSettings)
    extends CassandraStatements(settings) {

  /**
   * The Cassandra Statement that can be used to create the configured keyspace.
   *
   * This can be queried in for example a startup script without accessing the actual
   * Cassandra plugin actor.
   */
  def createJournalKeyspaceStatement: String =
    journalStatements.createKeyspace

  /**
   * Scala API: The Cassandra statements that can be used to create the configured tables.
   *
   * This can be queried in for example a startup script without accessing the actual
   * Cassandra plugin actor.
   */
  def createJournalTablesStatements: immutable.Seq[String] =
    journalStatements.createTable ::
    journalStatements.createTagsTable ::
    journalStatements.createTagsProgressTable ::
    journalStatements.createTagScanningTable ::
    journalStatements.createMetadataTable ::
    journalStatements.createAllPersistenceIdsTable ::
    Nil

  /**
   * Java API: The Cassandra statements that can be used to create the configured tables.
   *
   * This can be queried in for example a startup script without accessing the actual
   * Cassandra plugin actor.
   */
  def getCreateJournalTablesStatements: java.util.List[String] = {
    import pekko.util.ccompat.JavaConverters._
    createJournalTablesStatements.asJava
  }

  /**
   * The Cassandra Statement that can be used to create the configured keyspace.
   *
   * This can be queried in for example a startup script without accessing the actual
   * Cassandra plugin actor.
   */
  def createSnapshotKeyspaceStatement: String =
    snapshotStatements.createKeyspace

  /**
   * Scala API: The Cassandra statements that can be used to create the configured tables.
   *
   * This can be queried in for example a startup script without accessing the actual
   * Cassandra plugin actor.
   */
  def createSnapshotTablesStatements: immutable.Seq[String] =
    snapshotStatements.createTable :: Nil

  /**
   * Java API: The Cassandra statements that can be used to create the configured tables.
   *
   * This can be queried in for example a startup script without accessing the actual
   * Cassandra plugin actor.
   */
  def getCreateSnapshotTablesStatements: java.util.List[String] = {
    import pekko.util.ccompat.JavaConverters._
    createSnapshotTablesStatements.asJava
  }

}
