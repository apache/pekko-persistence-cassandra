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

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.persistence.cassandra.cleanup.CleanupSettings
import pekko.persistence.cassandra.cleanup.javadsl.Cleanup
import pekko.persistence.cassandra.reconciler.ReconciliationSettings
import pekko.persistence.cassandra.reconciler.javadsl.Reconciliation

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * The Java DSL facades own the Scala instance that they delegate to, and close it. The constructors that
 * take that delegate must not be part of the public API, since a delegate could then be shared between facades.
 */
class JavaDslConstructorsSpec extends AnyWordSpec with Matchers {

  private def publicConstructors(clazz: Class[?]): Set[List[Class[?]]] =
    clazz.getConstructors.map(_.getParameterTypes.toList).toSet

  "The Java DSL Cleanup" should {
    "only have public constructors that take an actor system" in {
      publicConstructors(classOf[Cleanup]) shouldEqual Set(
        List(classOf[ClassicActorSystemProvider]),
        List(classOf[ClassicActorSystemProvider], classOf[CleanupSettings]))
    }
  }

  "The Java DSL Reconciliation" should {
    "only have public constructors that take an actor system" in {
      publicConstructors(classOf[Reconciliation]) shouldEqual Set(
        List(classOf[ClassicActorSystemProvider]),
        List(classOf[ClassicActorSystemProvider], classOf[ReconciliationSettings]))
    }
  }
}
