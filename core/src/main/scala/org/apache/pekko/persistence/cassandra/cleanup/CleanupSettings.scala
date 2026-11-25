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

package org.apache.pekko.persistence.cassandra.cleanup

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

import org.apache.pekko.annotation.ApiMayChange

import com.typesafe.config.Config

@ApiMayChange
class CleanupSettings(config: Config) {
  val pluginLocation: String = config.getString("plugin-location")
  val operationTimeout: FiniteDuration = config.getDuration("operation-timeout", TimeUnit.MILLISECONDS).millis

  /**
   * Java API: the same value as `operationTimeout`, as a `java.time.Duration`.
   */
  def getOperationTimeout: java.time.Duration = operationTimeout.toJava

  val logProgressEvery: Int = config.getInt("log-progress-every")
  val dryRun: Boolean = config.getBoolean("dry-run")
}
