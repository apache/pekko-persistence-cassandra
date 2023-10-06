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
import org.apache.pekko.persistence.cassandra.util.LazyFutureEval

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class TaggedPreparedStatements(
    statements: CassandraJournalStatements,
    prepare: String => Future[PreparedStatement])(implicit val ec: ExecutionContext) {

  def init(): Unit = {
    WriteTagViewWithoutMeta.futureResult()
    WriteTagViewWithMeta.futureResult()
    WriteTagProgress.futureResult()
    SelectTagProgress.futureResult()
    SelectTagProgressForPersistenceId.futureResult()
    WriteTagScanning.futureResult()
    SelectTagScanningForPersistenceId.futureResult()
  }

  val WriteTagViewWithoutMeta: LazyFutureEval[PreparedStatement] = LazyFutureEval(() =>
    prepare(statements.writeTags(false)))
  val WriteTagViewWithMeta: LazyFutureEval[PreparedStatement] = LazyFutureEval(() =>
    prepare(statements.writeTags(true)))
  val WriteTagProgress: LazyFutureEval[PreparedStatement] = LazyFutureEval(() =>
    prepare(statements.writeTagProgress))
  val SelectTagProgress: LazyFutureEval[PreparedStatement] = LazyFutureEval(() =>
    prepare(statements.selectTagProgress))
  val SelectTagProgressForPersistenceId: LazyFutureEval[PreparedStatement] = LazyFutureEval(() =>
    prepare(statements.selectTagProgressForPersistenceId))
  val WriteTagScanning: LazyFutureEval[PreparedStatement] = LazyFutureEval(() =>
    prepare(statements.writeTagScanning))
  val SelectTagScanningForPersistenceId: LazyFutureEval[PreparedStatement] = LazyFutureEval(() =>
    prepare(statements.selectTagScanningForPersistenceId))
}
