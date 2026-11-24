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

package org.apache.pekko.persistence.cassandra.reconciler.javadsl

import java.util.concurrent.TimeUnit
import java.util.{ HashSet => JHashSet, Set => JSet }

import org.apache.pekko
import pekko.Done
import pekko.persistence.cassandra.CassandraSpec
import pekko.stream.javadsl.Sink

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually

/**
 * These tests depend on the output of each other, can't be run separately
 */
class ReconciliationSpec extends CassandraSpec with Eventually with BeforeAndAfterAll {

  private lazy val reconciliation = new Reconciliation(system)

  override protected def afterAll(): Unit = {
    reconciliation.close()
    super.afterAll()
  }

  private val tag1 = "tag1"
  private val tag2 = "tag2"
  private val pid1 = "p1"
  private val pid2 = "p2"

  "Reconciliation Java API" should {
    "return the tags of a persistence id as a java.util.Set" in {
      writeEventsFor(Set(tag1, tag2), pid1, 3)
      writeEventsFor(tag1, pid2, 3)

      val expected = new JHashSet[String]()
      expected.add(tag1)
      expected.add(tag2)
      eventually {
        val tags: JSet[String] =
          reconciliation.tagsForPersistenceId(pid1).toCompletableFuture.get(patience.timeout.toMillis,
            TimeUnit.MILLISECONDS)
        tags should ===(expected)
      }
    }

    "return all tags as a javadsl Source" in {
      eventually {
        val allTags = reconciliation
          .allTags()
          .runWith(Sink.seq[String], system)
          .toCompletableFuture
          .get(patience.timeout.toMillis, TimeUnit.MILLISECONDS)
        new JHashSet[String](allTags) should ===(new JHashSet[String](java.util.Arrays.asList(tag1, tag2)))
      }
    }

    "delete the tag view for a java.util.Set of persistence ids" in {
      eventsByTag(tag1)
        .request(10)
        .expectNextN(List("p1 event-1", "p1 event-2", "p1 event-3", "p2 event-1", "p2 event-2", "p2 event-3"))
        .expectNoMessage()
        .cancel()

      // a set that is typed with a wildcard is accepted
      val pids: JSet[_ <: String] = java.util.Collections.singleton(pid2)
      reconciliation
        .deleteTagViewForPersistenceIds(pids, tag1)
        .toCompletableFuture
        .get(patience.timeout.toMillis, TimeUnit.MILLISECONDS) should ===(Done)

      eventsByTag(tag1)
        .request(5)
        .expectNextN(List("p1 event-1", "p1 event-2", "p1 event-3"))
        .expectNoMessage()
        .cancel()
    }
  }
}
