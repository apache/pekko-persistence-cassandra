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

package org.apache.pekko.persistence.cassandra.snapshot

import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.nowarn
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.util.Failure

import com.datastax.oss.driver.api.core.cql.PreparedStatement

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class SnapshotPreparedStatementsSpec
    extends TestKit(ActorSystem("SnapshotPreparedStatementsSpec"))
    with AnyWordSpecLike
    with Matchers
    with Eventually
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.parasitic
  private val timeout = 5.seconds
  private val settings = new SnapshotSettings(system, system.settings.config.getConfig("pekko.persistence.cassandra"))
  private val queries = new CassandraSnapshotStatements(settings)
  private val statement = Proxy
    .newProxyInstance(
      classOf[PreparedStatement].getClassLoader,
      Array(classOf[PreparedStatement]),
      (_, method, _) => throw new UnsupportedOperationException(method.getName))
    .asInstanceOf[PreparedStatement]

  override protected def afterAll(): Unit =
    try TestKit.shutdownActorSystem(system)
    finally super.afterAll()

  // Thread.threadId is only available from JDK 19.
  @nowarn("msg=method getId")
  private def threadId(thread: Thread): Long = thread.getId

  "Snapshot prepared statements" should {
    "prepare each explicit-column query once on demand per store" in {
      val calls = mutable.Buffer.empty[String]
      def prepare(query: String): Future[PreparedStatement] = {
        calls += query
        Future.successful(statement)
      }
      val prepared = new CassandraSnapshotStore.PreparedStatements(queries, settings.maxLoadAttempts, prepare)
      val operations = List(
        prepared.writeSnapshot -> queries.writeSnapshot(withMeta = false),
        prepared.writeSnapshotWithMeta -> queries.writeSnapshot(withMeta = true),
        prepared.selectSnapshotMetadata -> queries.selectSnapshotMetadata(),
        prepared.selectSnapshotMetadataWithLimit -> queries.selectSnapshotMetadata(Some(settings.maxLoadAttempts)),
        prepared.deleteSnapshot -> queries.deleteSnapshot,
        prepared.deleteSnapshotRange -> queries.deleteAllSnapshotForPersistenceIdAndSequenceNrBetween)

      calls shouldBe empty
      operations.foreach { case (get, _) =>
        val first = get()
        (Await.result(first, timeout) eq statement) shouldBe true
        (get() eq first) shouldBe true
      }
      calls.toList shouldBe operations.map(_._2)
      calls.distinct.size shouldBe 6

      val otherStore = new CassandraSnapshotStore.PreparedStatements(queries, settings.maxLoadAttempts, prepare)
      (Await.result(otherStore.selectSnapshotMetadata(), timeout) eq statement) shouldBe true
      calls.count(_ == queries.selectSnapshotMetadata()) shouldBe 2
    }

    "leave wildcard snapshot reads to the driver's cache" in {
      val calls = mutable.Buffer.empty[String]
      val prepared = new CassandraSnapshotStore.PreparedStatements(queries, settings.maxLoadAttempts,
        query => {
          calls += query
          Future.successful(statement)
        })

      (Await.result(prepared.selectSnapshot(), timeout) eq statement) shouldBe true
      (Await.result(prepared.selectSnapshot(), timeout) eq statement) shouldBe true
      calls.toList shouldBe List.fill(2)(queries.selectSnapshot)
    }

    "reuse a failed prepare until asynchronous cleanup, then retry without clearing other statements" in {
      val pending = Promise[PreparedStatement]()
      val callbacks = mutable.Queue.empty[Runnable]
      val queued = new ExecutionContext {
        override def execute(runnable: Runnable): Unit = {
          callbacks.enqueue(runnable)
          ()
        }
        override def reportFailure(cause: Throwable): Unit = fail("Unexpected prepare callback failure", cause)
      }
      var selectAttempts = 0
      var deleteAttempts = 0
      val prepared = new CassandraSnapshotStore.PreparedStatements(queries, settings.maxLoadAttempts,
        query =>
          if (query == queries.selectSnapshotMetadata()) {
            selectAttempts += 1
            if (selectAttempts == 1) pending.future else Future.successful(statement)
          } else {
            deleteAttempts += 1
            Future.successful(statement)
          })(queued)
      val delete = prepared.deleteSnapshot()
      val select = prepared.selectSnapshotMetadata()
      (prepared.selectSnapshotMetadata() eq select) shouldBe true
      selectAttempts shouldBe 1

      val unavailable = new IllegalStateException("prepare initially unavailable")
      pending.failure(unavailable)
      select.value shouldBe Some(Failure(unavailable))
      (prepared.selectSnapshotMetadata() eq select) shouldBe true
      selectAttempts shouldBe 1

      // The production execution context may delay the cleanup callback after the future has already failed.
      while (callbacks.nonEmpty) callbacks.dequeue().run()

      val retry = prepared.selectSnapshotMetadata()
      (Await.result(retry, timeout) eq statement) shouldBe true
      (prepared.selectSnapshotMetadata() eq retry) shouldBe true
      (prepared.deleteSnapshot() eq delete) shouldBe true
      selectAttempts shouldBe 2
      deleteAttempts shouldBe 1
    }

    "retry after inline cleanup of an already failed prepare" in {
      var calls = 0
      val unavailable = new IllegalStateException("prepare initially unavailable")
      val prepared = new CassandraSnapshotStore.PreparedStatements(queries, settings.maxLoadAttempts,
        _ => {
          calls += 1
          if (calls == 1) Future.failed(unavailable) else Future.successful(statement)
        })

      Await.result(prepared.selectSnapshotMetadata().failed, timeout) shouldBe unavailable
      val retry = prepared.selectSnapshotMetadata()
      (Await.result(retry, timeout) eq statement) shouldBe true
      (prepared.selectSnapshotMetadata() eq retry) shouldBe true
      calls shouldBe 2
    }

    "start a single prepare when two callers overlap the first invocation" in {
      val workers = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
      val calls = new AtomicInteger()
      val entered = Promise[Thread]()
      val release = Promise[Unit]()
      val started = Promise[Thread]()
      val prepared = new CassandraSnapshotStore.PreparedStatements(queries, settings.maxLoadAttempts,
        _ => {
          if (calls.incrementAndGet() == 1) {
            entered.success(Thread.currentThread())
            Await.result(release.future, timeout)
          }
          Future.successful(statement)
        })
      try {
        val first = Future(prepared.selectSnapshotMetadata())(workers)
        val firstCaller = Await.result(entered.future, timeout)
        val second = Future {
          started.success(Thread.currentThread())
          prepared.selectSnapshotMetadata()
        }(workers)
        val secondCaller = Await.result(started.future, timeout)
        // The first lookup has not installed its future yet. A worker having started does not prove
        // contention: hold the first prepare until the second reaches its monitor (or finishes, which
        // would expose duplicate preparation if the lookup were no longer serialized).
        val threads = ManagementFactory.getThreadMXBean
        eventually(Timeout(timeout)) {
          val lockOwner = Option(threads.getThreadInfo(threadId(secondCaller))).map(_.getLockOwnerId)
          (second.isCompleted || lockOwner.contains(threadId(firstCaller))) shouldBe true
        }
        release.success(())
        (Await.result(first, timeout) eq Await.result(second, timeout)) shouldBe true
        calls.get() shouldBe 1
      } finally {
        release.trySuccess(())
        workers.shutdownNow()
      }
    }
  }
}
