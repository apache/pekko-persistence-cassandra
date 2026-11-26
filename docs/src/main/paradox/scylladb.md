<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# ScyllaDB

@@@ warning

**This project is not continuously tested against ScyllaDB.**

@@@

[ScyllaDB](https://www.scylladb.com) is a wire compatible replacement for Cassandra. Initial testing shows that
there are issues when using ScyllaDB with persistent query which is also used for recovery.

Users with ScyllaDB experience and test environments are very welcome contributors!
You can run the tests and having a ScyllaDB instance accessible on localhost port 9042, or adjust the
`datastax-java-driver.basic.contact-points` and other configuration to connect to your ScyllaDB.

## Tablets and keyspace creation

ScyllaDB enables tablets for new keyspaces by default. Up to ScyllaDB 2026.1 that default was only applied to
keyspaces created with `NetworkTopologyStrategy`, and a `SimpleStrategy` keyspace was silently created with vnodes.
Since ScyllaDB 2026.2 the default is applied to every replication strategy, including `SimpleStrategy`, and because
`SimpleStrategy` cannot be used with tablets the `CREATE KEYSPACE` statement is rejected with the error
`SimpleStrategy doesn't support tablet replication`.

`SimpleStrategy` is the default `replication-strategy` of this plugin, so with `keyspace-autocreate` enabled the
journal and snapshot keyspaces fail to be created and the plugin cannot start.

Use one of these approaches:

* Set `replication-strategy = "NetworkTopologyStrategy"` together with `data-center-replication-factors` for both
  `pekko.persistence.cassandra.journal` and `pekko.persistence.cassandra.snapshot`.
* Create the keyspaces yourself, see @ref:[Journal](journal.md#schema) and @ref:[Snapshots](snapshots.md#schema),
  using `NetworkTopologyStrategy` or adding `AND tablets = {'enabled': false}` to the `CREATE KEYSPACE` statement.
  Set `keyspace-autocreate = false` for both `pekko.persistence.cassandra.journal` and
  `pekko.persistence.cassandra.snapshot`, otherwise the plugin still issues `CREATE KEYSPACE IF NOT EXISTS` with
  its configured `replication-strategy`. The per-keyspace `tablets = {'enabled': false}` override is only accepted
  when the server permits it: ScyllaDB rejects it when `tablets_mode_for_new_keyspaces` is set to `enforced`.
* Disable tablets for new keyspaces on the ScyllaDB side with `tablets_mode_for_new_keyspaces: disabled` in
  `scylla.yaml` (or the `--tablets-mode-for-new-keyspaces=disabled` command line option). This is what the
  `scylladb-service` in the project's `docker-compose.yml` does.

## Snapshots

The @ref:[Snapshots](snapshots.md) page describes an issue to watch out for when you install the table definitions.
