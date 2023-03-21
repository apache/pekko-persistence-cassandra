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

package org.apache.pekko.persistence.cassandra.snapshot

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.persistence.cassandra.PluginSettings.getReplicationStrategy
import org.apache.pekko.persistence.cassandra.compaction.CassandraCompactionStrategy
import org.apache.pekko.persistence.cassandra.getListFromConfig
import com.typesafe.config.Config

/** INTERNAL API */
@InternalApi private[pekko] class SnapshotSettings(system: ActorSystem, config: Config) {
  private val snapshotConfig = config.getConfig("snapshot")

  val writeProfile: String = snapshotConfig.getString("write-profile")
  val readProfile: String = snapshotConfig.getString("read-profile")

  val keyspaceAutoCreate: Boolean = snapshotConfig.getBoolean("keyspace-autocreate")
  val tablesAutoCreate: Boolean = snapshotConfig.getBoolean("tables-autocreate")

  val keyspace: String = snapshotConfig.getString("keyspace")

  val table: String = snapshotConfig.getString("table")

  val tableCompactionStrategy: CassandraCompactionStrategy =
    CassandraCompactionStrategy(snapshotConfig.getConfig("table-compaction-strategy"))

  val replicationStrategy: String = getReplicationStrategy(
    snapshotConfig.getString("replication-strategy"),
    snapshotConfig.getInt("replication-factor"),
    getListFromConfig(snapshotConfig, "data-center-replication-factors"))

  val gcGraceSeconds: Long = snapshotConfig.getLong("gc-grace-seconds")

  val maxLoadAttempts: Int = snapshotConfig.getInt("max-load-attempts")

}
