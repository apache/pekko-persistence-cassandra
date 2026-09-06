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

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.must.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import scala.util.Random

import pekko.persistence.cassandra.journal.JournalSettings
import pekko.persistence.cassandra.snapshot.SnapshotSettings
import pekko.persistence.cassandra.EventsByTagSettings

class CassandraPluginSettingsSpec
    extends TestKit(ActorSystem("CassandraPluginConfigSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  lazy val defaultConfig = ConfigFactory.load().getConfig("pekko.persistence.cassandra")

  lazy val keyspaceNames = {
    // Generate a key that is the max acceptable length ensuring the first char is alpha
    def maxKey = Random.alphanumeric.dropWhile(_.toString.matches("[^a-zA-Z]")).take(48).mkString

    Table(
      ("Keyspace", "isValid"),
      // unquoted: must start with a letter, then alphanumeric/underscore, max 48 chars
      ("test", true),
      ("_test_123", false),
      ("", false),
      ("test-space", false),
      ("'test'", false),
      ("a", true),
      ("a_", true),
      ("1", false),
      ("a1", true),
      ("_", false),
      ("asdf!", false),
      (maxKey, true),
      (maxKey + "_", false),
      // quoted: any content except a double quote, 1 to 48 chars
      ("\"_asdf\"", true),
      ("\"_\"", true),
      ("\"a\"", true),
      ("\"a_sdf\"", true),
      ("\"\"", false),
      ("\"valid_with_quotes\"", true),
      ("\"test-space\"", true),
      ("\"My Table\"", true),
      ("\"missing_trailing_quote", false),
      ("missing_leading_quote\"", false),
      ('"'.toString + maxKey + '"'.toString, true))
  }

  override protected def afterAll(): Unit = {
    shutdown(system, verifySystemShutdown = true)
    super.afterAll()
  }

  "A CassandraJournalSettings" must {

    "set the metadata table" in {
      val config = new JournalSettings(system, defaultConfig)
      config.metadataTable must be("metadata")
    }

    "parse config with SimpleStrategy as default for replication-strategy" in {
      val config = new JournalSettings(system, defaultConfig)
      config.replicationStrategy must be("'SimpleStrategy','replication_factor':1")
    }

    "parse config with a list of datacenters configured for NetworkTopologyStrategy" in {
      lazy val configWithNetworkStrategy =
        ConfigFactory.parseString("""
          |journal.replication-strategy = "NetworkTopologyStrategy"
          |journal.data-center-replication-factors = ["dc1:3", "dc2:2"]
        """.stripMargin).withFallback(defaultConfig)
      val config = new JournalSettings(system, configWithNetworkStrategy)
      config.replicationStrategy must be("'NetworkTopologyStrategy','dc1':3,'dc2':2")
    }

    "parse config with a list of datacenters configured for NetworkTopologyStrategy using dot syntax" in {
      lazy val configWithNetworkStrategy =
        ConfigFactory.parseString("""
          |journal.replication-strategy = "NetworkTopologyStrategy"
          |journal.data-center-replication-factors.0 = "dc1:3"
          |journal.data-center-replication-factors.1 = "dc2:2"
        """.stripMargin).withFallback(defaultConfig)
      val config = new JournalSettings(system, configWithNetworkStrategy)
      config.replicationStrategy must be("'NetworkTopologyStrategy','dc1':3,'dc2':2")
    }

    "parse config with comma-separated data-center-replication-factors" in {
      lazy val configWithNetworkStrategy =
        ConfigFactory.parseString("""
          |journal.replication-strategy = "NetworkTopologyStrategy"
          |journal.data-center-replication-factors = "dc1:3,dc2:2"
        """.stripMargin).withFallback(defaultConfig)
      val config = new JournalSettings(system, configWithNetworkStrategy)
      config.replicationStrategy must be("'NetworkTopologyStrategy','dc1':3,'dc2':2")
    }

    "throw an exception for an unknown replication strategy" in {
      intercept[IllegalArgumentException] {
        PluginSettings.getReplicationStrategy("UnknownStrategy", 0, List.empty)
      }
    }

    "throw an exception when data-center-replication-factors is invalid or empty for NetworkTopologyStrategy" in {
      intercept[IllegalArgumentException] {
        PluginSettings.getReplicationStrategy("NetworkTopologyStrategy", 0, List.empty)
      }
      intercept[IllegalArgumentException] {
        PluginSettings.getReplicationStrategy("NetworkTopologyStrategy", 0, null)
      }
      intercept[IllegalArgumentException] {
        PluginSettings.getReplicationStrategy("NetworkTopologyStrategy", 0, Seq("dc1"))
      }
    }

    "validate keyspace parameter" in {
      forAll(keyspaceNames) { (keyspace, isValid) =>
        if (isValid) PluginSettings.validateKeyspaceName(keyspace) must be(keyspace)
        else
          intercept[IllegalArgumentException] {
            PluginSettings.validateKeyspaceName(keyspace)
          }
      }
    }

    "validate table name parameter" in {
      forAll(keyspaceNames) { (tableName, isValid) =>
        if (isValid) PluginSettings.validateTableName(tableName) must be(tableName)
        else
          intercept[IllegalArgumentException] {
            PluginSettings.validateTableName(tableName)
          }.getMessage must include("Invalid table name")
      }
    }

    "reject invalid keyspace name in JournalSettings" in {
      val badConfig =
        ConfigFactory.parseString("""journal.keyspace = "invalid;name"""").withFallback(defaultConfig)
      intercept[IllegalArgumentException] {
        new JournalSettings(system, badConfig)
      }.getMessage must include("Invalid keyspace name")
    }

    "reject invalid table name in JournalSettings" in {
      val badConfig =
        ConfigFactory.parseString("""journal.table = "invalid;table"""").withFallback(defaultConfig)
      intercept[IllegalArgumentException] {
        new JournalSettings(system, badConfig)
      }.getMessage must include("Invalid table name")
    }

    "reject invalid metadata table name in JournalSettings" in {
      val badConfig =
        ConfigFactory.parseString("""journal.metadata-table = "bad-name"""").withFallback(defaultConfig)
      intercept[IllegalArgumentException] {
        new JournalSettings(system, badConfig)
      }.getMessage must include("Invalid table name")
    }

    "reject invalid all-persistence-ids table name in JournalSettings" in {
      val badConfig =
        ConfigFactory.parseString("""journal.all-persistence-ids-table = "bad-name"""").withFallback(defaultConfig)
      intercept[IllegalArgumentException] {
        new JournalSettings(system, badConfig)
      }.getMessage must include("Invalid table name")
    }

    "reject invalid keyspace name in SnapshotSettings" in {
      val badConfig =
        ConfigFactory.parseString("""snapshot.keyspace = "invalid;name"""").withFallback(defaultConfig)
      intercept[IllegalArgumentException] {
        new SnapshotSettings(system, badConfig)
      }.getMessage must include("Invalid keyspace name")
    }

    "reject invalid table name in SnapshotSettings" in {
      val badConfig =
        ConfigFactory.parseString("""snapshot.table = "invalid;table"""").withFallback(defaultConfig)
      intercept[IllegalArgumentException] {
        new SnapshotSettings(system, badConfig)
      }.getMessage must include("Invalid table name")
    }

    "reject invalid tag table name in EventsByTagSettings" in {
      val badConfig =
        ConfigFactory.parseString("""events-by-tag.table = "bad-name"""").withFallback(defaultConfig)
      intercept[IllegalArgumentException] {
        new EventsByTagSettings(system, badConfig)
      }.getMessage must include("Invalid table name")
    }

    // Quoted identifiers are case sensitive in CQL and may contain characters that are not
    // allowed unquoted, so they must survive validation now that it is applied at startup.
    "accept quoted keyspace and table names in JournalSettings" in {
      val quotedConfig = ConfigFactory.parseString("""
          |journal.keyspace = "\"My Keyspace\""
          |journal.table = "\"my-messages\""
        """.stripMargin).withFallback(defaultConfig)
      val config = new JournalSettings(system, quotedConfig)
      config.keyspace must be("\"My Keyspace\"")
      config.table must be("\"my-messages\"")
    }

    "accept a quoted table name in SnapshotSettings" in {
      val quotedConfig = ConfigFactory.parseString("""
          |snapshot.table = "\"my-snapshots\""
        """.stripMargin).withFallback(defaultConfig)
      val config = new SnapshotSettings(system, quotedConfig)
      config.table must be("\"my-snapshots\"")
    }

    "accept a quoted tag table name in EventsByTagSettings" in {
      val quotedConfig = ConfigFactory.parseString("""
          |events-by-tag.table = "\"my-tag-views\""
        """.stripMargin).withFallback(defaultConfig)
      val config = new EventsByTagSettings(system, quotedConfig)
      config.tagTable.name must be("\"my-tag-views\"")
    }

    "parse keyspace-autocreate parameter" in {
      val configWithFalseKeyspaceAutocreate =
        ConfigFactory.parseString("journal.keyspace-autocreate = false").withFallback(defaultConfig)

      val config = new JournalSettings(system, configWithFalseKeyspaceAutocreate)
      config.keyspaceAutoCreate must be(false)
    }

    "parse tables-autocreate parameter" in {
      val configWithFalseTablesAutocreate =
        ConfigFactory.parseString("journal.tables-autocreate = false").withFallback(defaultConfig)

      val config = new JournalSettings(system, configWithFalseTablesAutocreate)
      config.tablesAutoCreate must be(false)
    }
  }

  "An EventsByTagSettings" must {

    def settingsWithMaxBufferSize(value: String): EventsByTagSettings =
      new EventsByTagSettings(
        system,
        ConfigFactory.parseString(s"events-by-tag.max-buffer-size = $value").withFallback(defaultConfig))

    "default max-buffer-size to no limit" in {
      new EventsByTagSettings(system, defaultConfig).maxBufferSize must be(0)
    }

    "parse max-buffer-size as a number" in {
      settingsWithMaxBufferSize("100000").maxBufferSize must be(100000)
    }

    "parse max-buffer-size no limit aliases" in {
      forAll(Table("value", "unlimited", "UNLIMITED", "off", "false", "0")) { value =>
        settingsWithMaxBufferSize(value).maxBufferSize must be(0)
      }
    }
  }

}
