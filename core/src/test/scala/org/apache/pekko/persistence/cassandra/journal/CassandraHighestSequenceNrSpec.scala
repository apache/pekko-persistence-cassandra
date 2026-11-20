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
import pekko.persistence.{
  DeleteMessagesSuccess,
  PersistentActor,
  RecoveryCompleted,
  SaveSnapshotSuccess,
  SnapshotOffer
}
import pekko.persistence.cassandra.CassandraSpec
import pekko.testkit.TestProbe

import scala.collection.immutable

object CassandraHighestSequenceNrSpec {
  case class PersistMe(msg: Long)
  case class PersistAllMe(msgs: immutable.Seq[Long])
  case object SnapshotMe
  case class DeleteTo(sequenceNr: Long)
  case object GetRecoveredEvents
  case class Ack(sequenceNr: Long)
  case class RecoveredEvents(events: Seq[Any])

  class PA(val persistenceId: String, probe: ActorRef) extends PersistentActor {

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
        probe ! s
      case DeleteTo(sequenceNr) =>
        deleteMessages(sequenceNr)
      case d: DeleteMessagesSuccess =>
        probe ! d
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

    // Without a snapshot the first partition is a hit, so the skipped partition comes after a hit and the
    // scan must still look one partition beyond it. Only the highest sequence nr is asserted: replay
    // with gap-free-sequence-numbers stops at an empty partition reached mid-stream, a separate issue.
    "look past an empty partition left by an AtomicWrite after events were found" in {
      val probe = TestProbe()
      val pid = nextId()
      val props = Props(new PA(pid, probe.ref))
      val p1 = system.actorOf(props)

      (1L to 3L).foreach { i =>
        p1 ! PersistMe(i)
        expectMsg(Ack(i))
      }

      // all four rows go to partition 2, partition 1 is left empty
      p1 ! PersistAllMe(4L to 7L)
      expectMsg(Ack(7))
      p1 ! PoisonPill

      // must continue after 7 rather than reuse a sequence nr already stored in partition 2
      val p1TakeTwo = system.actorOf(props)
      p1TakeTwo ! PersistMe(8)
      expectMsg(Ack(8))
    }

    // Deleting up to the highest sequence nr leaves the partition the floor lives in holding nothing but
    // tombstones. The bounded query must treat it as a miss and still find nothing beyond it.
    "continue after deleted_to when the floor partition holds only tombstones" in {
      val probe = TestProbe()
      val pid = nextId()
      val props = Props(new PA(pid, probe.ref))
      val p1 = system.actorOf(props)

      // events 1-3 fill partition 0, 4-6 fill partition 1
      (1L to 6L).foreach { i =>
        p1 ! PersistMe(i)
        expectMsg(Ack(i))
      }

      p1 ! DeleteTo(6)
      probe.expectMsg(DeleteMessagesSuccess(6))
      p1 ! PoisonPill

      val p1TakeTwo = system.actorOf(props)
      p1TakeTwo ! GetRecoveredEvents
      expectMsg(RecoveredEvents(List(RecoveryCompleted)))

      p1TakeTwo ! PersistMe(7)
      expectMsg(Ack(7))
    }

    // As above, but followed by an AtomicWrite that skips a partition, so the first row above the floor
    // comes after two misses: the tombstoned floor partition and the skipped one.
    "look past a tombstoned floor partition and a skipped partition" in {
      val probe = TestProbe()
      val pid = nextId()
      val props = Props(new PA(pid, probe.ref))
      val p1 = system.actorOf(props)

      (1L to 6L).foreach { i =>
        p1 ! PersistMe(i)
        expectMsg(Ack(i))
      }

      p1 ! DeleteTo(6)
      probe.expectMsg(DeleteMessagesSuccess(6))

      // events 7-10 span partitions 2 and 3, so all four rows go to partition 3 and partition 2 is empty
      p1 ! PersistAllMe(7L to 10L)
      expectMsg(Ack(10))
      p1 ! PoisonPill

      val p1TakeTwo = system.actorOf(props)
      p1TakeTwo ! GetRecoveredEvents
      expectMsg(
        RecoveredEvents(List(PersistMe(7), PersistMe(8), PersistMe(9), PersistMe(10), RecoveryCompleted)))

      p1TakeTwo ! PersistMe(11)
      expectMsg(Ack(11))
    }
  }
}
