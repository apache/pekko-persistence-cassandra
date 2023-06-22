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

package org.apache.pekko.persistence.cassandra

import org.apache.pekko
import pekko.actor.{ ActorLogging, ActorRef, Props }
import pekko.persistence.cassandra.journal.TagWriterSpec.TestEx
import pekko.persistence.{ PersistentActor, RecoveryCompleted, SaveSnapshotSuccess }
import pekko.persistence.journal.Tagged

object TestTaggingActor {
  case object Ack
  case object Crash
  case object DoASnapshotPlease
  case object SnapShotAck
  case object Stop

  def props(pId: String, tags: Set[String] = Set(), probe: Option[ActorRef] = None): Props =
    Props(new TestTaggingActor(pId, tags, probe))
}

class TestTaggingActor(val persistenceId: String, tags: Set[String], probe: Option[ActorRef])
    extends PersistentActor
    with ActorLogging {
  import TestTaggingActor._

  def receiveRecover: Receive = {
    case RecoveryCompleted =>
      probe.foreach(_ ! RecoveryCompleted)
    case _ =>
  }

  def receiveCommand: Receive = normal

  def normal: Receive = {
    case event: String =>
      log.debug("Persisting {}", event)
      persist(Tagged(event, tags)) { e =>
        processEvent(e)
        sender() ! Ack
      }
    case Crash =>
      throw TestEx("oh dear")
    case DoASnapshotPlease =>
      saveSnapshot("i don't have any state :-/")
      context.become(waitingForSnapshot(sender()))
    case Stop =>
      context.stop(self)

  }

  def waitingForSnapshot(who: ActorRef): Receive = {
    case SaveSnapshotSuccess(_) =>
      who ! SnapShotAck
      context.become(normal)
  }

  def processEvent: Receive = {
    case _ =>
  }
}
