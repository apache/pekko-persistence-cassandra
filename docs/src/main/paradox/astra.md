<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# DataStax Astra

You can spin up a free Apache Cassandra cluster in the cloud using [DataStax
Astra](https://www.datastax.com/products/datastax-astra). To use a Cassandra-as-a-Service cluster as Pekko persistence,
specify your secure connect bundle and credentials in your configuration instead of the contact points:

```
datastax-java-driver {
  basic {
    session-keyspace = my_keyspace
    cloud {
      secure-connect-bundle = /path/to/secure-connect-database_name.zip
    }
  }
  advanced {
    auth-provider {
      class = PlainTextAuthProvider
      username = <user name> 
      password = <password>
    }
  }
}
```
