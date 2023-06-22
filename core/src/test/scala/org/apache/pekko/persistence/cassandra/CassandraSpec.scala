/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.cassandra

import java.io.{ OutputStream, PrintStream }
import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.event.Logging.{ LogEvent, StdOutLogger }
import pekko.persistence.cassandra.CassandraSpec._
import pekko.persistence.cassandra.query.scaladsl.CassandraReadJournal
import pekko.persistence.query.{ NoOffset, PersistenceQuery }
import pekko.stream.scaladsl.{ Keep, Sink }
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.{ EventFilter, ImplicitSender, SocketUtil, TestKitBase }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Milliseconds, Seconds, Span }
import org.scalatest.{ Outcome, Suite }
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers
import scala.collection.immutable
import scala.concurrent.duration._

import pekko.persistence.cassandra.journal.CassandraJournal
import pekko.serialization.SerializationExtension
import scala.util.control.NonFatal

import pekko.persistence.cassandra.TestTaggingActor.Ack
import pekko.actor.PoisonPill

object CassandraSpec {
  def getCallerName(clazz: Class[_]): String = {
    val s = Thread.currentThread.getStackTrace
      .map(_.getClassName)
      .dropWhile(
        _.matches("(java.lang.Thread|.*Abstract.*|pekko.persistence.cassandra.CassandraSpec\\$|.*CassandraSpec)"))
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s.drop(z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_").take(48) // Max length of a c* keyspace
  }

  def configOverrides(journalKeyspace: String, snapshotStoreKeyspace: String): Config =
    ConfigFactory.parseString(s"""
      pekko.persistence.cassandra {
        journal.keyspace = $journalKeyspace
        
        snapshot {
          keyspace = $snapshotStoreKeyspace
        }
      }     
    """)

  val enableAutocreate = ConfigFactory.parseString("""
      pekko.persistence.cassandra {
        events-by-tag {
          eventual-consistency-delay = 200ms
        }
        snapshot {
          keyspace-autocreate = true
          tables-autocreate = true
        } 
        journal {
          keyspace-autocreate = true
          tables-autocreate = true
        }
      } 
     """)

  val fallbackConfig = ConfigFactory.parseString(s"""
      pekko.loggers = ["org.apache.pekko.persistence.cassandra.SilenceAllTestEventListener"]
      pekko.loglevel = DEBUG
      pekko.use-slf4j = off

      datastax-java-driver {
        basic.request {
          timeout = 10s # drop keyspaces 
        }
      }
    """).withFallback(enableAutocreate)

}

/**
 * Picks a free port for Cassandra before starting the ActorSystem
 */
abstract class CassandraSpec(
    config: Config = CassandraLifecycle.config,
    val journalName: String = getCallerName(getClass),
    val snapshotName: String = getCallerName(getClass),
    dumpRowsOnFailure: Boolean = true)
    extends TestKitBase
    with Suite
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with CassandraLifecycle
    with ScalaFutures {

  def this(config: String) = this(ConfigFactory.parseString(config))

  def this() = this(CassandraLifecycle.config)

  private var failed = false
  lazy val randomPort = SocketUtil.temporaryLocalPort()

  val shortWait = 10.millis

  lazy val queryJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  def keyspaces(): Set[String] = Set(journalName, snapshotName)

  private val ids = new AtomicInteger(0)

  def nextId(): String = s"pid-${ids.incrementAndGet()}"

  override protected def withFixture(test: NoArgTest): Outcome = {
    // When filtering just collects events into this var (yeah, it's a hack to do that in a filter).
    // We assume that the filter will always ever be used from a single actor, so a regular var should be fine.
    var events: List[LogEvent] = Nil

    object LogEventCollector extends EventFilter(Int.MaxValue) {
      override protected def matches(event: Logging.LogEvent): Boolean = {
        events ::= event
        true
      }
    }

    val myLogger = Logging(system, classOf[CassandraSpec])
    val res = LogEventCollector.intercept {
      myLogger.debug(s"Logging started for test [${test.name}]")
      val r = test()
      myLogger.debug(s"Logging finished for test [${test.name}]")
      r
    }

    if (!(res.isSucceeded || res.isPending)) {
      failed = true
      println(s"--> [${Console.BLUE}${test.name}${Console.RESET}] Start of log messages of test that [$res]")
      val logger = new StdOutLogger {}
      withPrefixedOut("| ") { events.reverse.foreach(logger.print) }
      println(s"<-- [${Console.BLUE}${test.name}${Console.RESET}] End of log messages of test that [$res]")
    }

    res
  }

  /** Adds a prefix to every line printed out during execution of the thunk. */
  private def withPrefixedOut[T](prefix: String)(thunk: => T): T = {
    val oldOut = Console.out
    val prefixingOut =
      new PrintStream(new OutputStream {
        override def write(b: Int): Unit = oldOut.write(b)
      }) {
        override def println(x: Any): Unit =
          oldOut.println(prefix + String.valueOf(x).replace("\n", s"\n$prefix"))
      }

    Console.withOut(prefixingOut) {
      thunk
    }
  }

  override protected def externalCassandraCleanup(): Unit = {
    try {
      if (failed && dumpRowsOnFailure) {
        println("RowDump::")
        import scala.jdk.CollectionConverters._
        if (system.settings.config.getBoolean("pekko.persistence.cassandra.events-by-tag.enabled")) {
          println("tag_views")
          cluster
            .execute(s"select * from $journalName.tag_views")
            .asScala
            .foreach(row => {
              println(s"""Row:${row.getString("tag_name")},${row.getLong("timebucket")},${formatOffset(
                  row.getUuid("timestamp"))},${row.getString("persistence_id")},${row
                  .getLong("tag_pid_sequence_nr")},${row.getLong("sequence_nr")}""")

            })
        }
        println("messages")
        cluster
          .execute(s"select * from ${journalName}.messages")
          .asScala
          .foreach(row => {
            println(s"""Row:${row.getLong("partition_nr")}, ${row.getString("persistence_id")}, ${row.getLong(
                "sequence_nr")}""")
          })

        println("snapshots")
        cluster
          .execute(s"select * from ${snapshotName}.snapshots")
          .asScala
          .foreach(row => {
            println(
              s"""Row:${row.getString("persistence_id")}, ${row.getLong("sequence_nr")}, ${row.getLong("timestamp")}""")
          })

      }
      keyspaces().foreach { keyspace =>
        cluster.execute(s"drop keyspace if exists $keyspace")
      }
    } catch {
      case NonFatal(t) =>
        println("Exception during cleanup")
        t.printStackTrace(System.out)
    }
  }

  final implicit lazy val system: ActorSystem = {
    // always use this port and keyspace generated here, then test config, then the lifecycle config
    val finalConfig =
      configOverrides(journalName, snapshotName)
        .withFallback(config) // test's config
        .withFallback(fallbackConfig) // generally good config that tests can override
        .withFallback(CassandraLifecycle.config)
        .withFallback(ConfigFactory.load())
        .resolve()

    val as = ActorSystem(journalName, finalConfig)
    as.log.info("Using key spaces: {} {}", journalName, snapshotName)
    as
  }

  final override def systemName = system.name

  implicit val patience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Milliseconds))

  val pidCounter = new AtomicInteger()
  def nextPid = s"pid=${pidCounter.incrementAndGet()}"

  val eventDeserializer: CassandraJournal.EventDeserializer = new CassandraJournal.EventDeserializer(system)

  lazy val queries: CassandraReadJournal =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  def writeEventsFor(tag: String, persistenceId: String, nrEvents: Int): Unit =
    writeEventsFor(Set(tag), persistenceId, nrEvents)

  def writeEventsFor(tags: Set[String], persistenceId: String, nrEvents: Int): Unit = {
    val ref = system.actorOf(TestTaggingActor.props(persistenceId, tags))
    for (i <- 1 to nrEvents) {
      ref ! s"$persistenceId event-$i"
      expectMsg(Ack)
    }
    watch(ref)
    ref ! PoisonPill
    expectTerminated(ref)
  }

  def eventsPayloads(pid: String): Seq[Any] =
    queries
      .currentEventsByPersistenceId(pid, 0, Long.MaxValue)
      .map(e => e.event)
      .toMat(Sink.seq)(Keep.right)
      .run()
      .futureValue

  def events(pid: String): immutable.Seq[Extractors.TaggedPersistentRepr] =
    queries
      .eventsByPersistenceId(
        pid,
        0,
        Long.MaxValue,
        100,
        None,
        readProfile = "pekko-persistence-cassandra-profile",
        "test",
        extractor = Extractors.taggedPersistentRepr(eventDeserializer, SerializationExtension(system)))
      .toMat(Sink.seq)(Keep.right)
      .run()
      .futureValue

  def eventPayloadsWithTags(pid: String): immutable.Seq[(Any, Set[String])] =
    queries
      .eventsByPersistenceId(
        pid,
        0,
        Long.MaxValue,
        100,
        None,
        readProfile = "pekko-persistence-cassandra-profile",
        "test",
        extractor = Extractors.taggedPersistentRepr(eventDeserializer, SerializationExtension(system)))
      .map { tpr =>
        (tpr.pr.payload, tpr.tags)
      }
      .toMat(Sink.seq)(Keep.right)
      .run()
      .futureValue

  def eventsByTag(tag: String): TestSubscriber.Probe[Any] =
    queries.eventsByTag(tag, NoOffset).map(_.event).runWith(TestSink.probe)

  def expectEventsForTag(tag: String, elements: String*): Unit = {
    val probe = queries.eventsByTag(tag, NoOffset).map(_.event).runWith(TestSink.probe)

    probe.request(elements.length + 1)
    elements.foreach(probe.expectNext)
    probe.expectNoMessage(10.millis)
    probe.cancel()
  }
}
