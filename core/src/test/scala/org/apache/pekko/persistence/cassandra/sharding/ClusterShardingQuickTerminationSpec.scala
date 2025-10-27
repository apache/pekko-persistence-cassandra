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

package org.apache.pekko.persistence.cassandra.sharding

import org.apache.pekko
import pekko.actor.{ ActorLogging, ActorRef, Props, ReceiveTimeout }
import pekko.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import pekko.cluster.{ Cluster, MemberStatus }
import pekko.persistence.PersistentActor
import pekko.persistence.cassandra.CassandraSpec
import pekko.testkit.TestProbe

import scala.concurrent.duration._

object ClusterShardingQuickTerminationSpec {

  case object Increment
  case object Decrement
  final case class Get(counterId: Long)
  final case class EntityEnvelope(id: Long, payload: Any)
  case object Ack

  case object Stop
  final case class CounterChanged(delta: Int)

  class Counter extends PersistentActor with ActorLogging {
    import ShardRegion.Passivate

    context.setReceiveTimeout(5.seconds)

    // self.path.name is the entity identifier (utf-8 URL-encoded)
    override def persistenceId: String = "Counter-" + self.path.name

    var count = 0

    def updateState(event: CounterChanged): Unit =
      count += event.delta

    override def receiveRecover: Receive = {
      case evt: CounterChanged => updateState(evt)
      case other               => log.debug("Other: {}", other)
    }

    override def receiveCommand: Receive = {
      case Increment      => persist(CounterChanged(+1))(updateState)
      case Decrement      => persist(CounterChanged(-1))(updateState)
      case Get(_)         => sender() ! count
      case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
      case Stop           =>
        sender() ! Ack
        context.stop(self)
    }
  }
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) => (id.toString, payload)
    case msg @ Get(id)               => (id.toString, msg)
  }

  val numberOfShards = 100

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) => (id % numberOfShards).toString
    case Get(id)               => (id % numberOfShards).toString
  }
}

class ClusterShardingQuickTerminationSpec extends CassandraSpec("""
    pekko.actor.provider = cluster
  """.stripMargin) {

  import ClusterShardingQuickTerminationSpec._

  "Cassandra Plugin with Cluster Sharding" must {
    "clear state if persistent actor shuts down" in {
      Cluster(system).join(Cluster(system).selfMember.address)
      awaitAssert {
        Cluster(system).selfMember.status shouldEqual MemberStatus.Up
      }
      ClusterSharding(system).start(
        typeName = "tagging",
        entityProps = Props[Counter](),
        settings = ClusterShardingSettings(system),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId)

      (0 to 100).foreach { i =>
        val counterRegion: ActorRef = ClusterSharding(system).shardRegion("tagging")
        awaitAssert {
          val sender = TestProbe()
          counterRegion.tell(Get(123), sender.ref)
          sender.expectMsg(500.millis, i)
        }

        counterRegion ! EntityEnvelope(123, Increment)
        counterRegion ! Get(123)
        expectMsg(i + 1)

        counterRegion ! EntityEnvelope(123, Stop)
        expectMsg(Ack)
      }
    }
  }
}
