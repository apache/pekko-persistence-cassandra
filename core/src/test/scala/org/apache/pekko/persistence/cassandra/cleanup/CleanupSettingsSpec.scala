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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

class CleanupSettingsSpec extends AnyWordSpec with Matchers {

  private val settings = new CleanupSettings(
    ConfigFactory
      .parseString("operation-timeout = 1500 ms")
      .withFallback(ConfigFactory.load().getConfig("pekko.persistence.cassandra.cleanup")))

  "CleanupSettings" should {
    "expose the operation timeout as a Scala duration" in {
      settings.operationTimeout shouldEqual 1500.millis
    }

    "expose the operation timeout as a Java duration" in {
      val operationTimeout: java.time.Duration = settings.getOperationTimeout
      operationTimeout shouldEqual java.time.Duration.ofMillis(1500)
    }
  }
}
