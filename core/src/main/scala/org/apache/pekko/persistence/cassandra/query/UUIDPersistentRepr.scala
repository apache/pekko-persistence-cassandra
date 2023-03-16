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

package org.apache.pekko.persistence.cassandra.query

import java.util.UUID

import org.apache.pekko.persistence.PersistentRepr
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.persistence.cassandra.journal.CassandraJournal.TagPidSequenceNr

/**
 * INTERNAL API: Wrap the [[PersistentRepr]] to add the UUID for
 * `eventsByTag` query, or similar queries.
 */
@InternalApi private[pekko] final case class UUIDPersistentRepr(
    offset: UUID,
    //  tags:             Set[String], this isn't in the tags table, may need to be added
    tagPidSequenceNr: TagPidSequenceNr,
    persistentRepr: PersistentRepr)
