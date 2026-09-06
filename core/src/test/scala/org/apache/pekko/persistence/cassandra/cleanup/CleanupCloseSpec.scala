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

package org.apache.pekko.persistence.cassandra.cleanup

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import pekko.actor.{ ActorIdentity, ActorRef, ActorSystem, Identify }
import pekko.testkit.{ ImplicitSender, TestKit }

object CleanupCloseSpec {

  // no Cassandra is needed, only the lifecycle of the reconciliation actor is asserted on
  val config = ConfigFactory.parseString("""
      pekko.persistence.cassandra.cleanup.dry-run = true
      datastax-java-driver.advanced.reconnect-on-init = false
    """)
}

class CleanupCloseSpec
    extends TestKit(ActorSystem("CleanupCloseSpec", CleanupCloseSpec.config))
    with AnyWordSpecLike
    with Matchers
    with ImplicitSender
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  private def reconciliationTagWriters(): Seq[ActorRef] = {
    system.actorSelection("/system/reconciliation-tag-writers-*") ! Identify(())
    receiveWhile(500.millis) {
      case ActorIdentity(_, ref) => ref
    }.flatten
  }

  "Cleanup" should {

    "not start a reconciliation when only snapshot and event operations are used" in {
      val cleanup = new Cleanup(system)

      cleanup.close()

      reconciliationTagWriters() shouldBe empty
    }

    "stop the reconciliation it started when closed" in {
      val cleanup = new Cleanup(system)
      // the returned future needs Cassandra and is deliberately not awaited, it is only used to
      // trigger the creation of the internal Reconciliation
      cleanup.deleteAllTaggedEvents("pid-1")

      val tagWriters = reconciliationTagWriters()
      tagWriters should have size 1
      watch(tagWriters.head)

      cleanup.close()

      expectTerminated(tagWriters.head)
    }
  }
}
