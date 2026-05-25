# Snapshots

## Features

- Implements the Pekko Persistence @extref:[snapshot store plugin API](pekko:persistence-journals.html#snapshot-store-plugin-api).

## Schema

The keyspace and tables needs to be created before using the plugin. 
  
@@@ warning

Auto creation of the keyspace and tables
is included as a development convenience and should never be used in production. Cassandra does not handle
concurrent schema migrations well and if every Pekko node tries to create the schema at the same time you'll
get column id mismatch errors in Cassandra.

@@@

The default keyspace used by the plugin is `pekko_snapshot`, it should be created with the
NetworkTopology replication strategy with a replication factor of at least 3:

```
CREATE KEYSPACE IF NOT EXISTS pekko_snapshot WITH replication = {'class': 'NetworkTopologyStrategy', '<your_dc_name>' : 3 }; 
```

For local testing, and the default if you enable `pekko.persistence.cassandra.snapshot.keyspace-autocreate` you can use the following:

```
CREATE KEYSPACE IF NOT EXISTS pekko_snapshot
 WITH REPLICATION = { 'class' : 'SimpleStrategy','replication_factor':1 };
```

A single table is required. This needs to be created before starting your application.
For local testing you can enable `pekko.persistence.cassandra.snapshot.tables-autocreate`.
The default table definitions look like this:

```
CREATE TABLE IF NOT EXISTS pekko_snapshot.snapshots (
  persistence_id text,
  sequence_nr bigint,
  timestamp bigint,
  ser_id int,
  ser_manifest text,
  snapshot_data blob,
  snapshot blob,
  meta_ser_id int,
  meta_ser_manifest text,
  meta blob,
  PRIMARY KEY (persistence_id, sequence_nr))
  WITH CLUSTERING ORDER BY (sequence_nr DESC) AND gc_grace_seconds = 864000
  AND compaction = {
    'class' : 'SizeTieredCompactionStrategy',
    'enabled' : true,
    'tombstone_compaction_interval' : 86400,
    'tombstone_threshold' : 0.2,
    'unchecked_tombstone_compaction' : false,
    'bucket_high' : 1.5,
    'bucket_low' : 0.5,
    'max_threshold' : 32,
    'min_threshold' : 4,
    'min_sstable_size' : 50
    };
```

### ScyllaDB

ScyllaDB does not [support](https://github.com/apache/pekko-persistence-cassandra/issues/135)
`unchecked_tombstone_compaction`. You should adjust the `pekko.messages` table definition to remove this.

### Consistency

By default, the snapshot store uses `ONE` for all reads and writes, since snapshots
should only be used as an optimization to reduce number of replayed events.
If a recovery doesn't see the latest snapshot it will just start from an older snapshot
and replay events from there. Be careful to not delete events too eagerly after storing
snapshots since the deletes may be visible before the snapshot is visible. Keep a few
snapshots and corresponding events before deleting older events and snapshots.

The consistency level for snapshots can be changed with:

```
datastax-java-driver.profiles {
  pekko-persistence-cassandra-snapshot-profile {
    basic.request.consistency = QUORUM
  }
}
```

## Configuration

To activate the snapshot-store plugin, add the following line to your Pekko `application.conf`:

    pekko.persistence.snapshot-store.plugin = "pekko.persistence.cassandra.snapshot"

This will run the snapshot store with its default settings. The default settings can be changed with the configuration
properties defined in @ref:[reference.conf](configuration.md#default-configuration). Journal configuration is under 
`pekko.persistence.cassandra.snapshot`.

## Limitations

The snapshot is stored in a single row so the maximum size of a serialized snapshot is the Cassandra configured
[`max_mutation_size_in_kb`](https://cassandra.apache.org/doc/latest/faq/index.html#can-large-blob) which is 16MB by default.

## Delete all snapshots

The @apidoc[org.apache.pekko.persistence.cassandra.cleanup.Cleanup] tool can be used for deleting all events and/or snapshots
given list of `persistenceIds` without using persistent actors. It's important that the actors with corresponding
`persistenceId` are not running at the same time as using the tool. See @ref[Database Cleanup](./cleanup.md) for more details.
