/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.persistence.cassandra.example

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.{ Behaviors, LoggerOps }
import pekko.cluster.typed.{ Cluster, SelfUp, Subscribe }
import pekko.management.cluster.bootstrap.ClusterBootstrap
import pekko.management.scaladsl.PekkoManagement
import pekko.persistence.cassandra.example.LoadGenerator.Start
import pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry

import scala.concurrent.Await
import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {

    ActorSystem(Behaviors.setup[SelfUp] {
        ctx =>
          val readSettings = ReadSide.Settings(ctx.system.settings.config.getConfig("cassandra.example"))
          val writeSettings = ConfigurablePersistentActor.Settings(readSettings.nrTags)
          val loadSettings = LoadGenerator.Settings(ctx.system.settings.config.getConfig("cassandra.example"))

          PekkoManagement(ctx.system).start()
          ClusterBootstrap(ctx.system).start()
          val cluster = Cluster(ctx.system)
          cluster.subscriptions ! Subscribe(ctx.self, classOf[SelfUp])

          val topic = ReadSideTopic.init(ctx)

          if (cluster.selfMember.hasRole("read")) {
            val session = CassandraSessionRegistry(ctx.system).sessionFor("pekko.persistence.cassandra")
            val offsetTableStmt =
              """
              CREATE TABLE IF NOT EXISTS pekko.offsetStore (
                eventProcessorId text,
                tag text,
                timeUuidOffset timeuuid,
                PRIMARY KEY (eventProcessorId, tag)
              )
           """

            Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
          }

          Behaviors.receiveMessage {
            case SelfUp(state) =>
              ctx.log.infoN(
                "Cluster member joined. Initializing persistent actors. Roles {}. Members {}",
                cluster.selfMember.roles,
                state.members)
              val ref = ConfigurablePersistentActor.init(writeSettings, ctx.system)
              if (cluster.selfMember.hasRole("read")) {
                ctx.spawnAnonymous(Reporter(topic))
              }
              ReadSide(ctx.system, topic, readSettings)
              if (cluster.selfMember.hasRole("load")) {
                ctx.log.info("Starting load generation")
                val load = ctx.spawn(LoadGenerator(loadSettings, ref), "load-generator")
                load ! Start(10.seconds)
              }
              Behaviors.empty
          }
      }, "apc-example")
  }
}
