/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package your.pack

import org.apache.pekko.stream.connectors.cassandra.CqlSessionProvider
import com.datastax.dse.driver.api.core.DseSession
import com.datastax.oss.driver.api.core.CqlSession

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

//#dse-session-provider
class DseSessionProvider extends CqlSessionProvider {
  override def connect()(implicit ec: ExecutionContext): Future[CqlSession] = {
    DseSession
      .builder()
      // .withAuthProvider() can add any DSE specific authentication here
      .buildAsync()
      .toScala
  }
}
//#dse-session-provider
