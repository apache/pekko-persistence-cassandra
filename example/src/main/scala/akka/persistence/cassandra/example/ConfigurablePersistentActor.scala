/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package akka.persistence.cassandra.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

object ConfigurablePersistentActor {

  case class Settings(nrTags: Int)

  val Key: EntityTypeKey[Event] = EntityTypeKey[Event]("configurable")

  def init(settings: Settings, system: ActorSystem[_]): ActorRef[ShardingEnvelope[Event]] = {
    ClusterSharding(system).init(Entity(Key)(ctx => apply(settings, ctx.entityId)).withRole("write"))
  }

  final case class Event(timeCreated: Long = System.currentTimeMillis()) extends CborSerializable

  final case class State(eventsProcessed: Long) extends CborSerializable

  def apply(settings: Settings, persistenceId: String): Behavior[Event] =
    Behaviors.setup { ctx =>
      EventSourcedBehavior[Event, Event, State](
        persistenceId = PersistenceId.ofUniqueId(persistenceId),
        State(0),
        (_, event) => {
          ctx.log.info("persisting event {}", event)
          Effect.persist(event)
        },
        (state, _) => state.copy(eventsProcessed = state.eventsProcessed + 1)).withTagger(event =>
        Set("tag-" + math.abs(event.hashCode() % settings.nrTags)))
    }

}
