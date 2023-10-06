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

package org.apache.pekko.persistence.cassandra.util

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, Future }

class LazyFutureEvalSpec extends AnyWordSpecLike with Matchers with Eventually {
  private val timeout = 5.seconds

  "LazyFutureEval" should {
    "eval only once when there is just 1 thread" in {
      val lazyEval = LazyFutureEval(() => Future.successful(UUID.randomUUID()))
      val uuid1 = Await.result(lazyEval.futureResult(), timeout)
      uuid1 should not be null
      Await.result(lazyEval.futureResult(), timeout) shouldEqual uuid1
    }
    "not store a failed eval result" in {
      val eval = new EvalThatFailsFirstTime
      val lazyEval = LazyFutureEval(() => Future(eval.getResult()))
      val res1 = Await.ready(lazyEval.futureResult(), timeout)
      res1.value.get.isFailure shouldBe true
      eventually {
        // 2nd call should succeed but the cached result is removed asynchronously so we need to retry
        val res2 = Await.ready(lazyEval.futureResult(), timeout)
        res2.value.get.isSuccess shouldBe true
      }
    }
  }

  private class EvalThatFailsFirstTime {
    private var initialized = false
    def getResult(): Boolean = {
      if (!initialized) {
        initialized = true
        throw new IllegalStateException("fails on first call")
      }
      initialized
    }
  }
}
