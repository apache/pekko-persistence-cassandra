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
import pekko.actor.{ ActorSystem, NoSerializationVerificationNeeded }
import pekko.annotation.InternalApi
import com.typesafe.config.Config

import scala.concurrent.duration._

@InternalApi
private[pekko] final class HealthCheckSettings(system: ActorSystem, config: Config)
    extends NoSerializationVerificationNeeded {

  private val healthCheckConfig = config.getConfig("healthcheck")

  val pluginLocation: String = healthCheckConfig.getString("plugin-location")

  val timeout: FiniteDuration = healthCheckConfig.getDuration("timeout", MILLISECONDS).millis

  val healthCheckCql: String = healthCheckConfig.getString("health-check-cql")

}
