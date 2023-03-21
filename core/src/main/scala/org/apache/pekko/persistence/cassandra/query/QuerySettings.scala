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

package org.apache.pekko.persistence.cassandra.query

import scala.concurrent.duration._

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.NoSerializationVerificationNeeded
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.annotation.InternalStableApi
import org.apache.pekko.persistence.cassandra.EventsByTagSettings
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalStableApi
@InternalApi private[pekko] class QuerySettings(
    system: ActorSystem,
    config: Config,
    val eventsByTagSettings: EventsByTagSettings)
    extends NoSerializationVerificationNeeded {

  private val queryConfig = config.getConfig("query")

  val readProfile: String = queryConfig.getString("read-profile")

  val refreshInterval: FiniteDuration =
    queryConfig.getDuration("refresh-interval", MILLISECONDS).millis

  val gapFreeSequenceNumbers: Boolean = queryConfig.getBoolean("gap-free-sequence-numbers")

  val maxBufferSize: Int = queryConfig.getInt("max-buffer-size")

  val deserializationParallelism: Int = queryConfig.getInt("deserialization-parallelism")

  val pluginDispatcher: String = queryConfig.getString("plugin-dispatcher")

  val eventsByPersistenceIdEventTimeout: FiniteDuration =
    queryConfig.getDuration("events-by-persistence-id-gap-timeout", MILLISECONDS).millis

}
