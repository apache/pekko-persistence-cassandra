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

package org.apache.pekko.persistence.cassandra

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import pekko.actor.{ ActorIdentity, ActorSystem, Identify }
import pekko.testkit.{ ImplicitSender, TestKit }

object EventsByTagMigrationCloseSpec {

  // no Cassandra is needed, the tag writers actor is started eagerly and all queries are lazy
  val config = ConfigFactory.parseString("""
      datastax-java-driver.advanced.reconnect-on-init = false
    """)
}

class EventsByTagMigrationCloseSpec
    extends TestKit(ActorSystem("EventsByTagMigrationCloseSpec", EventsByTagMigrationCloseSpec.config))
    with AnyWordSpecLike
    with Matchers
    with ImplicitSender
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  "EventsByTagMigration" should {

    "stop its tag writers actor when closed, and tolerate a second close" in {
      val migration = EventsByTagMigration(system)

      // resolve while this is the only actor in the system matching the pattern: an actor that
      // has been stopped can still be in the guardian's children when its watchers have already
      // seen Terminated, and the Identify sent to it is answered from dead letters with an empty
      // ActorIdentity, which would race with the one from the live actor
      system.actorSelection("/user/eventsByTagMigration-tag-writers-*") ! Identify(())
      val tagWriters = watch(expectMsgType[ActorIdentity].ref.get)

      migration.close()
      expectTerminated(tagWriters)

      // a second close has no further effect
      migration.close()
    }
  }
}
