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

package org.apache.pekko.persistence.cassandra.compaction

import java.util.concurrent.TimeUnit

import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.persistence.cassandra.RequiresCassandraThree
import pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec, PluginSettings }

import org.scalatest.wordspec.AnyWordSpecLike

import com.datastax.oss.driver.api.core.Version
import com.typesafe.config.ConfigFactory

object CassandraCompactionStrategySpec {
  lazy val config = ConfigFactory.parseString(s"""
       |pekko.persistence.cassandra.journal.keyspace=CassandraCompactionStrategySpec
       |pekko.persistence.cassandra.snapshot.keyspace=CassandraCompactionStrategySpecSnapshot
    """.stripMargin).withFallback(CassandraLifecycle.config)
}

class CassandraCompactionStrategySpec
    extends CassandraSpec(CassandraCompactionStrategySpec.config)
    with AnyWordSpecLike {

  val cassandraPluginSettings = PluginSettings(system)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cluster.execute(
      "CREATE KEYSPACE IF NOT EXISTS testKeyspace WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }")
  }

  private def supportsUnifiedCompactionStrategy: Boolean =
    cluster.getMetadata.getNodes.values.asScala.headOption.flatMap(n => Option(n.getCassandraVersion)).exists(
      _.compareTo(Version.parse("5.0.0")) >= 0)

  "A CassandraCompactionStrategy" must {

    "successfully create a TimeWindowCompactionStrategy" in {
      val twConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "TimeWindowCompactionStrategy"
          | compaction_window_size = 10
          | compaction_window_unit = "DAYS"
          |}
        """.stripMargin)

      val compactionStrategy = CassandraCompactionStrategy(twConfig.getConfig("journal.table-compaction-strategy"))
        .asInstanceOf[TimeWindowCompactionStrategy]

      compactionStrategy.compactionWindowSize shouldEqual 10
      compactionStrategy.compactionWindowUnit shouldEqual TimeUnit.DAYS
    }

    "successfully create CQL from TimeWindowCompactionStrategy" taggedAs RequiresCassandraThree in {
      val twConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "TimeWindowCompactionStrategy"
          | compaction_window_size = 1
          | compaction_window_unit = "MINUTES"
          |}
        """.stripMargin)

      val cqlExpression =
        s"CREATE TABLE IF NOT EXISTS testKeyspace.testTable1 (testId TEXT PRIMARY KEY) WITH compaction = ${CassandraCompactionStrategy(
            twConfig.getConfig("journal.table-compaction-strategy")).asCQL}"

      noException must be thrownBy {
        cluster.execute(cqlExpression)
      }
    }

    "fail if unrecognised property for TimeWindowCompactionStrategy" in {
      val twConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "TimeWindowCompactionStrategy"
          | compaction_window_size = 10
          | banana = "cherry"
          |}
        """.stripMargin)

      assertThrows[IllegalArgumentException] {
        CassandraCompactionStrategy(twConfig.getConfig("journal.table-compaction-strategy"))
          .asInstanceOf[TimeWindowCompactionStrategy]
      }
    }

    "successfully create a LeveledCompactionStrategy" in {
      val uniqueConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "LeveledCompactionStrategy"
          | enabled = true
          | tombstone_compaction_interval = 86400
          | tombstone_threshold = 0.1
          | unchecked_tombstone_compaction = false
          | sstable_size_in_mb = 100
          |}
        """.stripMargin)

      val compactionStrategy = CassandraCompactionStrategy(uniqueConfig.getConfig("journal.table-compaction-strategy"))
        .asInstanceOf[LeveledCompactionStrategy]

      compactionStrategy.enabled shouldEqual true
      compactionStrategy.tombstoneCompactionInterval shouldEqual 86400
      compactionStrategy.tombstoneThreshold shouldEqual 0.1
      compactionStrategy.uncheckedTombstoneCompaction shouldEqual false
      compactionStrategy.ssTableSizeInMB shouldEqual 100
    }

    "successfully create CQL from LeveledCompactionStrategy" in {
      val uniqueConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "LeveledCompactionStrategy"
          | enabled = true
          | tombstone_compaction_interval = 86400
          | tombstone_threshold = 0.1
          | unchecked_tombstone_compaction = false
          | sstable_size_in_mb = 100
          |}
        """.stripMargin)

      val cqlExpression =
        s"CREATE TABLE IF NOT EXISTS testKeyspace.testTable2 (testId TEXT PRIMARY KEY) WITH compaction = ${CassandraCompactionStrategy(
            uniqueConfig.getConfig("journal.table-compaction-strategy")).asCQL}"

      noException must be thrownBy {
        cluster.execute(cqlExpression)
      }
    }

    "successfully create a SizeTieredCompactionStrategy" in {
      val uniqueConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "SizeTieredCompactionStrategy"
          | enabled = true
          | tombstone_compaction_interval = 86400
          | tombstone_threshold = 0.1
          | unchecked_tombstone_compaction = false
          | bucket_high = 5.0
          | bucket_low = 0.75
          | max_threshold = 20
          | min_threshold = 10
          | min_sstable_size = 100
          |}
        """.stripMargin)

      val compactionStrategy = CassandraCompactionStrategy(uniqueConfig.getConfig("journal.table-compaction-strategy"))
        .asInstanceOf[SizeTieredCompactionStrategy]

      compactionStrategy.enabled shouldEqual true
      compactionStrategy.tombstoneCompactionInterval shouldEqual 86400
      compactionStrategy.tombstoneThreshold shouldEqual 0.1
      compactionStrategy.uncheckedTombstoneCompaction shouldEqual false
      compactionStrategy.bucketHigh shouldEqual 5.0
      compactionStrategy.bucketLow shouldEqual 0.75
      compactionStrategy.maxThreshold shouldEqual 20
      compactionStrategy.minThreshold shouldEqual 10
      compactionStrategy.minSSTableSize shouldEqual 100
    }

    "successfully create CQL from SizeTieredCompactionStrategy" in {
      val uniqueConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "SizeTieredCompactionStrategy"
          | enabled = true
          | tombstone_compaction_interval = 86400
          | tombstone_threshold = 0.1
          | unchecked_tombstone_compaction = false
          | bucket_high = 5.0
          | bucket_low = 0.75
          | max_threshold = 20
          | min_threshold = 10
          | min_sstable_size = 100
          |}
        """.stripMargin)

      val cqlExpression =
        s"CREATE TABLE IF NOT EXISTS testKeyspace.testTable3 (testId TEXT PRIMARY KEY) WITH compaction = ${CassandraCompactionStrategy(
            uniqueConfig.getConfig("journal.table-compaction-strategy")).asCQL}"

      noException must be thrownBy {
        cluster.execute(cqlExpression)
      }
    }

    "successfully create a UnifiedCompactionStrategy" in {
      val ucsConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "UnifiedCompactionStrategy"
          | tombstone_threshold = 0.1
          | scaling_parameters = "T4"
          | target_sstable_size = "1GiB"
          | min_sstable_size = "100MiB"
          | base_shard_count = 4
          | sstable_growth = 0.333
          | max_sstables_to_compact = 32
          | expired_sstable_check_frequency_seconds = 600
          | unsafe_aggressive_sstable_expiration = false
          | overlap_inclusion_method = "TRANSITIVE"
          |}
        """.stripMargin)

      val compactionStrategy = CassandraCompactionStrategy(ucsConfig.getConfig("journal.table-compaction-strategy"))
        .asInstanceOf[UnifiedCompactionStrategy]

      compactionStrategy.tombstoneThreshold shouldEqual 0.1
      compactionStrategy.scalingParameters shouldEqual Some("T4")
      compactionStrategy.targetSSTableSize shouldEqual Some("1GiB")
      compactionStrategy.minSSTableSize shouldEqual Some("100MiB")
      compactionStrategy.baseShardCount shouldEqual Some(4)
      compactionStrategy.sstableGrowth shouldEqual Some(0.333)
      compactionStrategy.maxSSTablesToCompact shouldEqual Some(32)
      compactionStrategy.expiredSSTableCheckFrequencySeconds shouldEqual Some(600L)
      compactionStrategy.unsafeAggressiveSSTableExpiration shouldEqual Some(false)
      compactionStrategy.overlapInclusionMethod shouldEqual Some("TRANSITIVE")
    }

    "only include the configured UnifiedCompactionStrategy options in the CQL" in {
      val ucsConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "UnifiedCompactionStrategy"
          | scaling_parameters = "L10, T4"
          |}
        """.stripMargin)

      CassandraCompactionStrategy(ucsConfig.getConfig("journal.table-compaction-strategy")).asCQL shouldEqual
      """{
          |'class' : 'UnifiedCompactionStrategy',
          |'enabled' : true,
          |'tombstone_compaction_interval' : 86400,
          |'tombstone_threshold' : 0.2,
          |'unchecked_tombstone_compaction' : false,
          |'scaling_parameters' : 'L10, T4'
          |}""".stripMargin
    }

    "fail if unrecognised property for UnifiedCompactionStrategy" in {
      val ucsConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "UnifiedCompactionStrategy"
          | sstable_size_in_mb = 100
          |}
        """.stripMargin)

      assertThrows[IllegalArgumentException] {
        CassandraCompactionStrategy(ucsConfig.getConfig("journal.table-compaction-strategy"))
      }
    }

    "fail if sstable_growth is out of range for UnifiedCompactionStrategy" in {
      val ucsConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "UnifiedCompactionStrategy"
          | sstable_growth = 1.5
          |}
        """.stripMargin)

      assertThrows[IllegalArgumentException] {
        CassandraCompactionStrategy(ucsConfig.getConfig("journal.table-compaction-strategy"))
      }
    }

    "successfully create CQL from UnifiedCompactionStrategy" in {
      assume(supportsUnifiedCompactionStrategy, "UnifiedCompactionStrategy requires Cassandra 5.0+")

      val ucsConfig = ConfigFactory.parseString("""journal.table-compaction-strategy {
          | class = "UnifiedCompactionStrategy"
          | enabled = true
          | tombstone_compaction_interval = 86400
          | tombstone_threshold = 0.1
          | unchecked_tombstone_compaction = false
          | scaling_parameters = "T4"
          | target_sstable_size = "1GiB"
          | min_sstable_size = "100MiB"
          | base_shard_count = 4
          | sstable_growth = 0.333
          | max_sstables_to_compact = 32
          | expired_sstable_check_frequency_seconds = 600
          | unsafe_aggressive_sstable_expiration = false
          | overlap_inclusion_method = "TRANSITIVE"
          |}
        """.stripMargin)

      val cqlExpression =
        s"CREATE TABLE IF NOT EXISTS testKeyspace.testTable4 (testId TEXT PRIMARY KEY) WITH compaction = ${CassandraCompactionStrategy(
            ucsConfig.getConfig("journal.table-compaction-strategy")).asCQL}"

      noException must be thrownBy {
        cluster.execute(cqlExpression)
      }

      val compaction = cluster.getMetadata
        .getKeyspace("testkeyspace").get
        .getTable("testtable4").get
        .getOptions.asScala.collectFirst { case (k, v) if k.asInternal == "compaction" => v }
        .get.asInstanceOf[java.util.Map[String, String]].asScala
      compaction("class") should endWith("UnifiedCompactionStrategy")
      compaction("scaling_parameters") shouldEqual "T4"
    }
  }
}
