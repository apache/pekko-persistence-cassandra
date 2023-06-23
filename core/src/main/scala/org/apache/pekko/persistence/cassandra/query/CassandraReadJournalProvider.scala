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

package org.apache.pekko.persistence.cassandra.query

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class CassandraReadJournalProvider(system: ExtendedActorSystem, config: Config, configPath: String)
    extends ReadJournalProvider {

  override val scaladslReadJournal: scaladsl.CassandraReadJournal =
    new scaladsl.CassandraReadJournal(system, config, configPath)

  override val javadslReadJournal: javadsl.CassandraReadJournal =
    new javadsl.CassandraReadJournal(scaladslReadJournal)

}
