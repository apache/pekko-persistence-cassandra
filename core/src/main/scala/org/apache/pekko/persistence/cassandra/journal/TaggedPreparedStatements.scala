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

package org.apache.pekko.persistence.cassandra.journal

import com.datastax.oss.driver.api.core.cql.PreparedStatement
import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class TaggedPreparedStatements(
    statements: CassandraJournalStatements,
    prepare: String => Future[PreparedStatement])(implicit val ec: ExecutionContext) {

  def init(): Unit = {
    WriteTagViewWithoutMeta
    WriteTagViewWithMeta
    WriteTagProgress
    SelectTagProgress
    SelectTagProgressForPersistenceId
    WriteTagScanning
    SelectTagScanningForPersistenceId
  }

  lazy val WriteTagViewWithoutMeta: Future[PreparedStatement] = prepare(statements.writeTags(false))
  lazy val WriteTagViewWithMeta: Future[PreparedStatement] = prepare(statements.writeTags(true))
  lazy val WriteTagProgress: Future[PreparedStatement] = prepare(statements.writeTagProgress)
  lazy val SelectTagProgress: Future[PreparedStatement] = prepare(statements.selectTagProgress)
  lazy val SelectTagProgressForPersistenceId: Future[PreparedStatement] =
    prepare(statements.selectTagProgressForPersistenceId)
  lazy val WriteTagScanning: Future[PreparedStatement] = prepare(statements.writeTagScanning)
  lazy val SelectTagScanningForPersistenceId: Future[PreparedStatement] =
    prepare(statements.selectTagScanningForPersistenceId)
}
