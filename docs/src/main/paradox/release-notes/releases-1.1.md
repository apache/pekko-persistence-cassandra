# Release Notes (1.1.x)

## 1.1.0
Release notes for Apache Pekko Persistence Cassandra 1.1.0. See [GitHub Milestone](https://github.com/apache/pekko-persistence-cassandra/milestone/1?closed=1) for a fuller list of changes.

### Bug Fixes
* When PreparedStatement initialization fails, the code used not to recover ([#103](https://github.com/apache/pekko-persistence-cassandra/issues/103))

### Other Changes
* Improve the pattern matching for CassandraJournal options ([PR28](https://github.com/apache/pekko-persistence-cassandra/pull/28))

### Dependency Upgrades
* Apache Cassandra Driver 4.18 (replaces the Datastax Driver that was used before, this was donated to Apache Cassandra and they released 4.18 from this)
