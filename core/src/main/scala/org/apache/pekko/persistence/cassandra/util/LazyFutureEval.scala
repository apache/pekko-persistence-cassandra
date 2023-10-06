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

import org.apache.pekko.annotation.InternalApi

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Failure

/**
 * An internal utility class that lazily creates an instance of T but that can recover if the evalFunction fails.
 *
 * It does not guarantee that the evalFunction succeeds only once.
 *
 * This class is not recommended for non-Pekko usage.
 */
@InternalApi
case class LazyFutureEval[T](evalFunction: () => Future[T]) {
  private val instance = new AtomicReference[Future[T]]()

  def futureResult()(implicit ec: ExecutionContext): Future[T] = {
    val f = instance.get()
    if (f == null) {
      val result = evalFunction()
      instance.set(result)
      result.onComplete {
        case _: Failure[T] => instance.set(null)
        case _             =>
      }
      result
    } else {
      f
    }
  }
}
