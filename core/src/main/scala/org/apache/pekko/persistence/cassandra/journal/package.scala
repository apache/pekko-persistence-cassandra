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

package org.apache.pekko.persistence.cassandra

import org.apache.pekko.annotation.InternalApi

package object journal {

  /** INTERNAL API */
  @InternalApi private[pekko] def partitionNr(sequenceNr: Long, partitionSize: Long): Long =
    (sequenceNr - 1L) / partitionSize
}
