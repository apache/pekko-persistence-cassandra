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

package org.apache.pekko.persistence.cassandra.journal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.NoSerializationVerificationNeeded
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.annotation.InternalStableApi
import org.apache.pekko.persistence.cassandra.PluginSettings.getReplicationStrategy
import org.apache.pekko.persistence.cassandra.compaction.CassandraCompactionStrategy
import org.apache.pekko.persistence.cassandra.getListFromConfig
import com.typesafe.config.Config

/** INTERNAL API */
@InternalStableApi
@InternalApi private[pekko] class JournalSettings(system: ActorSystem, config: Config)
    extends NoSerializationVerificationNeeded {

  private val journalConfig = config.getConfig("journal")

  val writeProfile: String = config.getString("write-profile")
  val readProfile: String = config.getString("read-profile")

  val keyspaceAutoCreate: Boolean = journalConfig.getBoolean("keyspace-autocreate")
  val tablesAutoCreate: Boolean = journalConfig.getBoolean("tables-autocreate")

  val keyspace: String = journalConfig.getString("keyspace")

  val table: String = journalConfig.getString("table")
  val metadataTable: String = journalConfig.getString("metadata-table")
  val allPersistenceIdsTable: String = journalConfig.getString("all-persistence-ids-table")

  val tableCompactionStrategy: CassandraCompactionStrategy =
    CassandraCompactionStrategy(journalConfig.getConfig("table-compaction-strategy"))

  val replicationStrategy: String = getReplicationStrategy(
    journalConfig.getString("replication-strategy"),
    journalConfig.getInt("replication-factor"),
    getListFromConfig(journalConfig, "data-center-replication-factors"))

  val gcGraceSeconds: Long = journalConfig.getLong("gc-grace-seconds")

  val targetPartitionSize: Long = journalConfig.getLong("target-partition-size")
  val maxMessageBatchSize: Int = journalConfig.getInt("max-message-batch-size")

  val maxConcurrentDeletes: Int = journalConfig.getInt("max-concurrent-deletes")

  val supportDeletes: Boolean = journalConfig.getBoolean("support-deletes")

  val supportAllPersistenceIds: Boolean = journalConfig.getBoolean("support-all-persistence-ids")

  val coordinatedShutdownOnError: Boolean = config.getBoolean("coordinated-shutdown-on-error")

}
