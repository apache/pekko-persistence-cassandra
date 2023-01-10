/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.cassandra.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class CassandraReadJournalProvider(system: ExtendedActorSystem, config: Config, configPath: String)
    extends ReadJournalProvider {

  override val scaladslReadJournal: scaladsl.CassandraReadJournal =
    new scaladsl.CassandraReadJournal(system, config, configPath)

  override val javadslReadJournal: javadsl.CassandraReadJournal =
    new javadsl.CassandraReadJournal(scaladslReadJournal)

}
