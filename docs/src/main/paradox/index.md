<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Pekko Persistence Cassandra

The Pekko Persistence Cassandra plugin allows for using [Apache Cassandra](https://cassandra.apache.org) as a backend for @extref:[Pekko Persistence](pekko:persistence.html) and @extref:[Pekko Persistence Query](pekko:persistence-query.html). It uses @extref:[Pekko Connectors Cassandra](pekko-connectors:cassandra.html) for Cassandra access which is based on the @extref:[Datastax Java Driver](java-driver:).

@@toc { depth=2 }

@@@ index

* [overview](overview.md)
* [Journal Plugin](journal.md)
* [Query Plugin](read-journal.md)
* [Events by tag](events-by-tag.md)
* [Snapshot Plugin](snapshots.md)
* [Serialization](serialization.md)
* [Testing](testing.md)
* [CQRS](cqrs.md)
* [Health check](healthcheck.md)
* [Configuration](configuration.md)
* [Cassandra server support](server.md)
* [Release Notes](release-notes/index.md)
* [Migrations](migrations.md)
* [Cleanup](cleanup.md)

@@@
