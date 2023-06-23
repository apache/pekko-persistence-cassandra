/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.cassandra.compaction

import com.typesafe.config.Config

/*
 * http://docs.datastax.com/en/cql/3.1/cql/cql_reference/compactSubprop.html
 */
trait CassandraCompactionStrategy {
  def asCQL: String
}

object CassandraCompactionStrategy {
  def apply(config: Config): CassandraCompactionStrategy =
    BaseCompactionStrategy.fromConfig(config)
}
