# Migration

* Before migrating to Apache Pekko Persistence Cassandra, ensure that you have [migrated](https://doc.akka.io/docs/akka-persistence-cassandra/1.0.6/migrations.html) to akka-persistence-cassandra 1.0.6
* The core [Pekko migration guide](https://pekko.apache.org/docs/pekko/current/project/migration-guides.html) is also worth reading.
* Note that this project uses the `pekko` [keyspace](https://cassandra.apache.org/_/glossary.html#keyspace) instead of `akka`.

## Keyspaces
As noted above the default keyspace has been changed. 
You can either:

* Adapt your configuration to use your current keyspace

```
  pekko.persistence.cassandra.journal.keyspace = "akka"
  pekko.persistence.cassandra.snapshot.keyspace = "akka_snapshot"
```

* Create new keyspaces for "pekko" and "pekko_snapshot" and restore your data into the new keyspace

## Configuration

All classes, plugin keys and profiles have also been adapted. 
Please refer to [reference.conf](https://github.com/apache/pekko-persistence-cassandra/blob/main/core/src/main/resources/reference.conf) on how to adapt your configuration.
