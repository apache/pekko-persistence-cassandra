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

package org.apache.pekko.persistence.cassandra.cleanup.javadsl

import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.{ ArrayList => JArrayList, List => JList }

import org.apache.pekko
import pekko.Done
import pekko.persistence.SnapshotMetadata
import pekko.persistence.cassandra.cleanup.{ CleanupSpec => ScalaCleanupSpec }
import pekko.persistence.cassandra.query.DirectWriting
import pekko.persistence.cassandra.{ CassandraSpec, RequiresCassandraThree }
import pekko.persistence.query.NoOffset
import pekko.stream.scaladsl.Sink

class CleanupSpec extends CassandraSpec(ScalaCleanupSpec.config) with DirectWriting {
  import ScalaCleanupSpec._

  private def persistEvents(pid: String, nrEvents: Int): Unit = {
    val p = system.actorOf(TestActor.props(pid))
    (1 to nrEvents).foreach { _ =>
      p ! PersistTaggedEvent("javadsl-tag")
      expectMsgType[Ack]
    }
    watch(p)
    system.stop(p)
    expectTerminated(p)
  }

  "Cassandra cleanup Java API" must {
    "delete everything for a java.util.List of persistenceIds" in {
      val pids = new JArrayList[String]()
      (1 to 3).foreach(_ => pids.add(nextPid))
      pids.forEach(pid => persistEvents(pid, 2))
      queries
        .currentEventsByTag(tag = "javadsl-tag", offset = NoOffset)
        .runWith(Sink.seq)
        .futureValue
        .size should ===(6)

      val cleanup = new Cleanup(system)
      try {
        // a list that is typed with a wildcard is accepted
        val wildcardPids: JList[? <: String] = pids
        cleanup.deleteAll(wildcardPids, true).toCompletableFuture.get(10, TimeUnit.SECONDS) should ===(Done)
      } finally cleanup.close()

      val foundPids = queries.currentPersistenceIds().runWith(Sink.seq).futureValue
      pids.forEach(pid => foundPids should not contain pid)
      queries
        .currentEventsByTag(tag = "javadsl-tag", offset = NoOffset)
        .runWith(Sink.seq)
        .futureValue
        .size should ===(0)

      val p2 = system.actorOf(TestActor.props(pids.get(0)))
      p2 ! GetRecoveredState
      expectMsg(RecoveredState("", Nil, 0L))
    }

    "return the oldest remaining snapshot as an Optional" taggedAs RequiresCassandraThree in {
      val cleanup = new Cleanup(system)
      val pid = nextPid
      writeTestSnapshot(SnapshotMetadata(pid, 1, 1000), "snapshot-1").futureValue
      writeTestSnapshot(SnapshotMetadata(pid, 2, 2000), "snapshot-2").futureValue
      writeTestSnapshot(SnapshotMetadata(pid, 3, 3000), "snapshot-3").futureValue

      val oldestSnapshot: Optional[SnapshotMetadata] =
        cleanup.deleteBeforeSnapshot(pid, 2).toCompletableFuture.get(10, TimeUnit.SECONDS)

      oldestSnapshot should ===(Optional.of(SnapshotMetadata(pid, 2, 2000)))
    }

    "return an empty Optional when there are no snapshots" taggedAs RequiresCassandraThree in {
      val cleanup = new Cleanup(system)

      val oldestSnapshot: Optional[SnapshotMetadata] =
        cleanup.deleteBeforeSnapshot(nextPid, 2, 0L).toCompletableFuture.get(10, TimeUnit.SECONDS)

      oldestSnapshot should ===(Optional.empty[SnapshotMetadata]())
    }
  }
}
