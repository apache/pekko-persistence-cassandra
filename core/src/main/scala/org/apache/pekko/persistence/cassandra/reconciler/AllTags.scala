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

package org.apache.pekko.persistence.cassandra.reconciler

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.scaladsl.Source
import pekko.NotUsed

import scala.collection.mutable

/**
 * Calculates all the tags by scanning the tag_write_progress table.
 *
 * This is not an efficient query as it needs to do a full table scan and while running will keep
 * all tags in memory
 *
 * This may not pick up tags that have just been created as the write to the tag progress
 * table is asynchronous.
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class AllTags(session: ReconciliationSession) {

  def execute(): Source[String, NotUsed] = {
    session
      .selectAllTagProgress()
      .map(_.getString("tag"))
      .statefulMap(() => mutable.Set.empty[String])(
        (seen, tag) =>
          if (seen.contains(tag)) {
            (seen, None)
          } else {
            (seen.+=(tag), Some(tag))
          },
        _ => None)
      .collect { case Some(tag) => tag }
  }

}
