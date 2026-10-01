/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.cassandra.journal

import org.apache.pekko
import pekko.actor.{ ActorRef, PoisonPill, Props }
import pekko.persistence.{ PersistentActor, RecoveryCompleted, SaveSnapshotSuccess, SnapshotOffer }
import pekko.persistence.cassandra.CassandraSpec
import pekko.testkit.TestProbe

import scala.collection.immutable

object CassandraHighestSequenceNrSpec {
  case class PersistMe(msg: Long)
  case class PersistAllMe(msgs: immutable.Seq[Long])
  case object SnapshotMe
  case object GetRecoveredEvents
  case class Ack(sequenceNr: Long)
  case class RecoveredEvents(events: Seq[Any])

  class PA(val persistenceId: String, snapshotProbe: ActorRef) extends PersistentActor {

    override def journalPluginId: String = "cassandra-plugin-small-partition-size.journal"

    private var recoveredEvents: List[Any] = List.empty

    override def receiveRecover: Receive = {
      case _: SnapshotOffer => // the snapshot carries no state, it only moves the recovery start point
      case event            => recoveredEvents = event :: recoveredEvents
    }

    override def receiveCommand: Receive = {
      case p: PersistMe =>
        val replyTo = sender()
        persist(p) { _ =>
          replyTo ! Ack(lastSequenceNr)
        }
      case PersistAllMe(msgs) =>
        val replyTo = sender()
        var remaining = msgs.size
        persistAll(msgs.map(PersistMe(_))) { _ =>
          remaining -= 1
          if (remaining == 0) replyTo ! Ack(lastSequenceNr)
        }
      case SnapshotMe =>
        saveSnapshot("snap")
      case s: SaveSnapshotSuccess =>
        snapshotProbe ! s
      case GetRecoveredEvents =>
        sender() ! RecoveredEvents(recoveredEvents.reverse)
    }
  }
}

class CassandraHighestSequenceNrSpec extends CassandraSpec(s"""
    cassandra-plugin-small-partition-size = $${pekko.persistence.cassandra}
    cassandra-plugin-small-partition-size {
      journal.target-partition-size = 3
      journal.keyspace = "HighestSequenceNrSpec"
    }
  """) {

  import CassandraHighestSequenceNrSpec._

  override def keyspaces(): Set[String] = super.keyspaces().union(Set("HighestSequenceNrSpec"))

  "Highest sequence nr recovery" must {

    // An AtomicWrite is stored entirely in the partition of its *last* sequence nr, so a write that
    // crosses a boundary leaves the partition it started in empty. With a snapshot the search for the
    // highest sequence nr starts from the partition holding the snapshot, which is followed by that
    // empty partition. Stopping at either of them would hand back a sequence nr that is already in use.
    "look past an empty partition left by an AtomicWrite that crossed a boundary" in {
      val snapshots = TestProbe()
      val pid = nextId()
      val props = Props(new PA(pid, snapshots.ref))
      val p1 = system.actorOf(props)

      // target-partition-size is 3, so events 1-3 fill partition 0
      (1L to 3L).foreach { i =>
        p1 ! PersistMe(i)
        expectMsg(Ack(i))
      }

      // the recovery start point is now in partition 0
      p1 ! SnapshotMe
      snapshots.expectMsgType[SaveSnapshotSuccess]

      // events 4-7 are a single AtomicWrite spanning partitions 1 and 2, so all four rows are written
      // to partition 2 and partition 1 is left with no rows at all
      p1 ! PersistAllMe(4L to 7L)
      expectMsg(Ack(7))

      p1 ! PoisonPill

      val p1TakeTwo = system.actorOf(props)
      p1TakeTwo ! GetRecoveredEvents
      expectMsg(
        RecoveredEvents(List(PersistMe(4), PersistMe(5), PersistMe(6), PersistMe(7), RecoveryCompleted)))

      // must continue after 7 rather than reuse a sequence nr already stored in partition 2
      p1TakeTwo ! PersistMe(8)
      expectMsg(Ack(8))
    }

    "find the highest sequence nr across many partitions without a snapshot" in {
      val snapshots = TestProbe()
      val pid = nextId()
      val props = Props(new PA(pid, snapshots.ref))
      val p1 = system.actorOf(props)

      (1L to 20L).foreach { i =>
        p1 ! PersistMe(i)
        expectMsg(Ack(i))
      }

      p1 ! PoisonPill

      val p1TakeTwo = system.actorOf(props)
      p1TakeTwo ! PersistMe(21)
      expectMsg(Ack(21))
    }
  }
}
