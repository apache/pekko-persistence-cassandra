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

package org.apache.pekko.persistence.cassandra.query

import java.time.LocalDateTime

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * The events by tag specs use `Day` time buckets and a CI run can cross midnight UTC, so the times
 * that the tests write events at must be derived in a way that is safe just after the bucket changes.
 */
class EventsByTagSpecTimesSpec extends AnyWordSpec with Matchers {
  import EventsByTagSpec.recentTimeInCurrentBucket

  "recentTimeInCurrentBucket" must {
    "be 5 minutes ago when that is in the same day bucket" in {
      val now = LocalDateTime.of(2026, 11, 26, 12, 0, 0)
      recentTimeInCurrentBucket(now) shouldEqual now.minusMinutes(5)
    }

    "stay in the current day bucket just after midnight" in {
      // 5 minutes ago is in the previous day's bucket
      val now = LocalDateTime.of(2026, 11, 26, 0, 2, 13)
      val t1 = recentTimeInCurrentBucket(now)
      t1.toLocalDate shouldEqual now.toLocalDate
      // the tests also use timestamps a second or so either side
      t1.minusSeconds(1).toLocalDate shouldEqual now.toLocalDate
      t1.plusSeconds(3).isBefore(now) shouldBe true
    }

    "not go before the start of the day bucket at midnight" in {
      val now = LocalDateTime.of(2026, 11, 26, 0, 0, 0)
      recentTimeInCurrentBucket(now).minusSeconds(1) shouldEqual now
    }
  }
}
