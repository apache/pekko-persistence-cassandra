/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.persistence.cassandra.example

import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import org.apache.pekko.actor.typed.pubsub.Topic
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import ReadSideTopic.ReadSideMetrics
import org.apache.pekko.actor.typed.scaladsl.LoggerOps

object Reporter {
  def apply(topic: ActorRef[Topic.Command[ReadSideTopic.ReadSideMetrics]]): Behavior[ReadSideMetrics] =
    Behaviors.setup { ctx =>
      ctx.log.info("Subscribing to latency stats")
      topic ! Topic.Subscribe(ctx.self)
      Behaviors.receiveMessage[ReadSideMetrics] {
        case ReadSideMetrics(count, max, p99, p50) =>
          ctx.log.infoN("Read side Count: {} Max: {} p99: {} p50: {}", count, max, p99, p50)
          Behaviors.same
      }
    }
}
