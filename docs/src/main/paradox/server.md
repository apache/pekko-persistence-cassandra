<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Cassandra server support

There are several alternative Cassandra server implementations. This project is tested with 
[Apache Cassandra](https://cassandra.apache.org) and
[DataStax Enterprise (DSE)](https://www.datastax.com/resources/datasheet/datastax-enterprise).

[DataStax Astra](https://www.datastax.com/products/datastax-astra) is built on Apache Cassandra and should therefore also work without
surprises even though there is no continuous integration tests for it in this project. See @ref:[DataStax Astra configuration](astra.md).

Several other alternatives are listed below. Be aware of that this project is not continuously tested against those. 

* @ref:[CosmosDB](cosmosdb.md)
* @ref:[DataStax Astra](astra.md)
* @ref:[ScyllaDB](scylladb.md)

## More details

@@toc { depth=1 }

@@@ index

* [Amazon Keyspaces](keyspaces.md)
* [CosmosDB](cosmosdb.md)
* [DataStax Astra](astra.md)
* [ScyllaDB](scylladb.md)

@@@
