<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# DataStax Astra

To use Astra DB with Pekko Persistence Cassandra, download the database's Secure Connect Bundle (SCB) and
generate an [application token](https://docs.datastax.com/en/astra-db-serverless/administration/manage-application-tokens.html)
with permissions for the operations your application performs. The bundle supplies contact points and TLS settings,
so remove any explicit contact points from this driver configuration.

The example uses an existing keyspace named `my_keyspace` for both the journal and snapshot store. Use your actual
keyspace in all three settings. Create the keyspace through Astra, and use the
@ref:[journal](journal.md#schema) and @ref:[snapshot](snapshots.md#schema) schemas for the table definitions.
Astra DB Serverless ignores [unsupported CQL table properties](https://docs.datastax.com/en/astra-db-serverless/cql/develop-with-cql.html),
such as compaction settings, and returns a warning. You can leave those properties out of the table definitions.

Set the `ASTRA_DB_APPLICATION_TOKEN` environment variable for your application process to the token value
(the `token` field, not the token's client ID or secret). Keep the token and bundle out of source control.

Astra DB Serverless [does not allow writes at consistency `ONE`](https://docs.datastax.com/en/astra-db-serverless/architecture/database-internals/configure-consistency.html).
Set `LOCAL_QUORUM` on the snapshot profile, which otherwise uses `ONE`. The example also sets it for requests
without a named profile, such as some cleanup operations. The journal and query profile keeps its default `QUORUM`.

```hocon
pekko.persistence.cassandra {
  journal.keyspace = "my_keyspace"
  snapshot.keyspace = "my_keyspace"
}

datastax-java-driver {
  basic {
    session-keyspace = "my_keyspace"
    cloud.secure-connect-bundle = "/path/to/secure-connect-database.zip"
    request.consistency = LOCAL_QUORUM
  }
  profiles.pekko-persistence-cassandra-snapshot-profile.basic.request.consistency = LOCAL_QUORUM
  advanced.auth-provider {
    class = PlainTextAuthProvider
    username = "token"
    password = ${ASTRA_DB_APPLICATION_TOKEN}
  }
}
```
