# Event sourcing and CQRS

`EventSourcedBehavior`s with the `PersistenceQuery`'s `eventsByTag` query can be used to do Event sourcing with Command and
Query Responsibility Segregation (CQRS).

A full sample showing how do to this with Cassandra, including scaling the read side, is in the [Pekko samples repository](https://github.com/apache/incubator-pekko-samples/tree/main/pekko-sample-cqrs-scala).
