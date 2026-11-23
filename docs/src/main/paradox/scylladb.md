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

The @ref:[Snapshots](snapshots.md) page describes an issue to watch out for when you install the table definitions.
