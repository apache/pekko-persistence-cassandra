/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package org.apache.pekko.persistence.cassandra.example

import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import org.apache.pekko.util.JavaDurationConverters._

object LoadGenerator {

  object Settings {
    def apply(config: Config): Settings = {
      Settings(config.getInt("persistence-ids"), config.getDuration("load-tick-duration").asScala)
    }
  }

  case class Settings(nrPersistenceIds: Int, tickDuration: FiniteDuration)

  sealed trait Command
  final case class Start(duration: FiniteDuration) extends Command
  final case class Tick() extends Command
  private case object Stop extends Command

  def apply(
      settings: Settings,
      ref: ActorRef[ShardingEnvelope[ConfigurablePersistentActor.Event]]): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      Behaviors.setup { ctx =>
        Behaviors.receiveMessage {
          case Start(duration) =>
            ctx.log.info("Starting...")
            timers.startTimerAtFixedRate(Tick(), settings.tickDuration)
            timers.startSingleTimer(Stop, duration)
            Behaviors.same
          case Tick() =>
            ctx.log.info("Sending event")
            ref ! ShardingEnvelope(
              s"p${Random.nextInt(settings.nrPersistenceIds)}",
              ConfigurablePersistentActor.Event())
            Behaviors.same
          case Stop =>
            Behaviors.same
        }
      }
    }
  }
}
