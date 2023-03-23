/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.cassandra.healthcheck

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.pattern.{ ask, AskTimeoutException }
import pekko.persistence.Persistence
import pekko.persistence.cassandra.PluginSettings
import pekko.persistence.cassandra.journal.CassandraJournal.HealthCheckQuery
import pekko.util.Timeout

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.control.NonFatal

final class CassandraHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {

  private val log = Logging.getLogger(system, getClass)

  private val settings = new PluginSettings(system, system.settings.config.getConfig("pekko.persistence.cassandra"))
  private val healthCheckSettings = settings.healthCheckSettings
  private val journalPluginId = s"${healthCheckSettings.pluginLocation}.journal"
  private val journalRef = Persistence(system).journalFor(journalPluginId)

  private implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(s"$journalPluginId.plugin-dispatcher")
  private implicit val timeout: Timeout = healthCheckSettings.timeout

  override def apply(): Future[Boolean] = {
    (journalRef ? HealthCheckQuery).map(_ => true).recoverWith {
      case _: AskTimeoutException =>
        log.warning("Failed to execute health check due to ask timeout")
        Future.successful(false)
      case NonFatal(e) =>
        log.warning("Failed to execute health check due to: {}", e)
        Future.successful(false)
    }
  }
}
