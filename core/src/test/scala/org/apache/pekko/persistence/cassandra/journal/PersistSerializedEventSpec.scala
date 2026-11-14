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

import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.actor.Props
import pekko.persistence.RecoveryCompleted
import pekko.persistence.SerializedEvent
import pekko.persistence.cassandra.CassandraLifecycle
import pekko.persistence.cassandra.CassandraSpec
import pekko.persistence.cassandra.Persister
import pekko.persistence.journal.Tagged
import pekko.persistence.query.NoOffset
import pekko.serialization.SerializationExtension
import pekko.serialization.Serializers
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.TestProbe

class PersistSerializedEventSpec extends CassandraSpec(CassandraLifecycle.config) {

  private case class Row(seqNr: Long, serId: Int, serManifest: String)

  private def serializedEvent(event: AnyRef): SerializedEvent = {
    val serializer = SerializationExtension(system).findSerializerFor(event)
    new SerializedEvent(serializer.toBinary(event), serializer.identifier, Serializers.manifestFor(serializer, event))
  }

  private def selectRows(pid: String): List[Row] =
    cluster
      .execute(
        s"select sequence_nr, ser_id, ser_manifest from $journalName.messages where persistence_id = ? and partition_nr = 0",
        pid)
      .all()
      .asScala
      .toList
      .map(row => Row(row.getLong("sequence_nr"), row.getInt("ser_id"), row.getString("ser_manifest")))
      .sortBy(_.seqNr)

  "A Cassandra journal" must {

    "store SerializedEvent with the given serialization information" in {
      val pid = nextId()

      // String serialization has no manifest
      val serializedEvent1 = serializedEvent("e1")
      serializedEvent1.serializerManifest shouldBe ""

      // Option serialization has manifest
      val serializedEvent2 = serializedEvent(Some("e2"))
      serializedEvent2.serializerManifest should not be ""

      val probe = TestProbe()
      val incarnation1 = system.actorOf(Props(new Persister(pid, probe.ref)))
      probe.expectMsgType[RecoveryCompleted]
      incarnation1 ! serializedEvent1
      probe.expectMsg(serializedEvent1)
      incarnation1 ! serializedEvent2
      probe.expectMsg(serializedEvent2)
      probe.watch(incarnation1)
      system.stop(incarnation1)
      probe.expectTerminated(incarnation1)

      selectRows(pid) shouldBe List(
        Row(1L, serializedEvent1.serializerId, serializedEvent1.serializerManifest),
        Row(2L, serializedEvent2.serializerId, serializedEvent2.serializerManifest))

      system.actorOf(Props(new Persister(pid, probe.ref)))
      probe.expectMsg("e1")
      probe.expectMsg(Some("e2"))
      probe.expectMsgType[RecoveryCompleted]
    }

    "store tagged SerializedEvent so that eventsByTag returns the deserialized event" in {
      val pid = nextId()
      val tag = s"tag-$pid"
      val serializedEvent1 = serializedEvent("e1")

      val probe = TestProbe()
      val persister = system.actorOf(Props(new Persister(pid, probe.ref)))
      probe.expectMsgType[RecoveryCompleted]
      val tagged = Tagged(serializedEvent1, Set(tag))
      persister ! tagged
      probe.expectMsg(tagged)

      selectRows(pid) shouldBe List(Row(1L, serializedEvent1.serializerId, serializedEvent1.serializerManifest))

      val events = queryJournal.eventsByTag(tag, NoOffset).map(_.event).runWith(TestSink[Any]())
      events.request(10)
      events.expectNext("e1")
      events.cancel()
    }
  }
}
