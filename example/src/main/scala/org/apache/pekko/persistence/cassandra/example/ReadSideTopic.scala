/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.persistence.cassandra.example

import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef

object ReadSideTopic {

  final case class ReadSideMetrics(count: Long, maxValue: Long, p99: Long, p50: Long)

  def init(context: ActorContext[_]): ActorRef[Topic.Command[ReadSideMetrics]] = {
    context.spawn(Topic[ReadSideMetrics]("read-side-metrics"), "read-side-metrics")
  }

}
