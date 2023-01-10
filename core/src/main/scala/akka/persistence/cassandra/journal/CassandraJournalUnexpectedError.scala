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

package akka.persistence.cassandra.journal

import akka.actor.CoordinatedShutdown.Reason

/**
 * Cassandra Journal unexpected error with akka.persistence.cassandra.coordinated-shutdown-on-error set to true
 */
case object CassandraJournalUnexpectedError extends Reason
