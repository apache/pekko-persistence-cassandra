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

package org.apache.pekko.persistence.cassandra.journal

import scala.concurrent.duration.FiniteDuration
import org.apache.pekko
import pekko.actor.{ Actor, ActorRef }
import pekko.actor.Props
import pekko.annotation.InternalApi

/**
 * INTERNAL API: Proxies messages to another actor, only allowing identical messages through once every [interval].
 */
@InternalApi private[pekko] class PubSubThrottler(delegate: ActorRef, interval: FiniteDuration) extends Actor {
  import PubSubThrottler._
  import context.dispatcher

  /** The messages we've already seen during this interval */
  val seen = collection.mutable.Set.empty[Any]

  /** The messages we've seen more than once during this interval, and their sender(s). */
  val repeated = collection.mutable.Map.empty[Any, Set[ActorRef]].withDefaultValue(Set.empty)

  val timer = context.system.scheduler.scheduleWithFixedDelay(interval, interval, self, Tick)

  def receive = {
    case Tick =>
      for ((msg, clients) <- repeated;
        client <- clients) {
        delegate.tell(msg, client)
      }
      seen.clear()
      repeated.clear()

    case msg =>
      if (seen.contains(msg)) {
        repeated += (msg -> (repeated(msg) + sender()))
      } else {
        delegate.forward(msg)
        seen += msg
      }

  }

  override def postStop() = {
    timer.cancel()
    super.postStop()
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object PubSubThrottler {

  def props(delegate: ActorRef, interval: FiniteDuration): Props =
    Props(new PubSubThrottler(delegate, interval))

  private case object Tick

}
