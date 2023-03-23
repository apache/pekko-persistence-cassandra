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

import scala.collection.immutable
import org.apache.pekko
import pekko.actor.Props
import pekko.persistence.PersistentActor
import pekko.actor.ActorRef
import pekko.persistence.DeleteMessagesSuccess
import pekko.persistence.journal.Tagged

object TestActor {
  def props(persistenceId: String, journalId: String = "pekko.persistence.cassandra.journal"): Props =
    Props(new TestActor(persistenceId, journalId))

  final case class PersistAll(events: immutable.Seq[String])
  final case class DeleteTo(seqNr: Long)
}

class TestActor(override val persistenceId: String, override val journalPluginId: String) extends PersistentActor {

  var lastDelete: ActorRef = _

  val receiveRecover: Receive = {
    case evt: String =>
  }

  val receiveCommand: Receive = {
    case cmd: String =>
      persist(cmd) { evt =>
        sender() ! evt + "-done"
      }
    case cmd: Tagged =>
      persist(cmd) { evt =>
        val msg = s"${evt.payload}-done"
        sender() ! msg
      }

    case TestActor.PersistAll(events) =>
      val size = events.size
      val handler = {
        var count = 0
        evt: String => {
          count += 1
          if (count == size)
            sender() ! "PersistAll-done"
        }
      }
      persistAll(events)(handler)

    case TestActor.DeleteTo(seqNr) =>
      lastDelete = sender()
      deleteMessages(seqNr)

    case d: DeleteMessagesSuccess =>
      lastDelete ! d
  }
}
