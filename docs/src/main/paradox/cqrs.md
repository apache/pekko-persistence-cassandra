<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Event sourcing and CQRS

`EventSourcedBehavior`s with the `PersistenceQuery`'s `eventsByTag` query can be used to do Event sourcing with Command and
Query Responsibility Segregation (CQRS).

A full sample showing how do to this with Cassandra, including scaling the read side, is in the [Pekko samples repository](https://github.com/apache/pekko-samples/tree/main/pekko-sample-cqrs-scala).
