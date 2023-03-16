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

package org.apache.pekko.persistence.cassandra.journal

import java.util.UUID

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.persistence.cassandra.BucketSize
import org.apache.pekko.util.HashCode
import com.datastax.oss.driver.api.core.uuid.Uuids

/** INTERNAL API */
@InternalApi private[pekko] object TimeBucket {

  def apply(timeuuid: UUID, bucketSize: BucketSize): TimeBucket =
    apply(Uuids.unixTimestamp(timeuuid), bucketSize)

  def apply(epochTimestamp: Long, bucketSize: BucketSize): TimeBucket =
    // round down to bucket size so the times are deterministic
    new TimeBucket(roundDownBucketSize(epochTimestamp, bucketSize), bucketSize)

  private def roundDownBucketSize(time: Long, bucketSize: BucketSize): Long = {
    val key: Long = time / bucketSize.durationMillis
    key * bucketSize.durationMillis
  }
}

/** INTERNAL API */
@InternalApi private[pekko] final class TimeBucket private (val key: Long, val bucketSize: BucketSize) {
  def inPast: Boolean =
    key < TimeBucket.roundDownBucketSize(System.currentTimeMillis(), bucketSize)

  def isCurrent: Boolean = {
    val now = System.currentTimeMillis()
    now >= key && now < (key + bucketSize.durationMillis)
  }

  def within(uuid: UUID): Boolean = {
    val when = Uuids.unixTimestamp(uuid)
    when >= key && when < (key + bucketSize.durationMillis)
  }

  def next(): TimeBucket =
    new TimeBucket(key + bucketSize.durationMillis, bucketSize)

  def previous(steps: Int): TimeBucket =
    if (steps == 0) this
    else new TimeBucket(key - steps * bucketSize.durationMillis, bucketSize)

  def >(other: TimeBucket): Boolean =
    key > other.key

  def <(other: TimeBucket): Boolean =
    key < other.key

  def <=(other: TimeBucket): Boolean =
    key <= other.key

  override def equals(other: Any): Boolean = other match {
    case that: TimeBucket =>
      key == that.key &&
      bucketSize == that.bucketSize
    case _ => false
  }

  override def hashCode(): Int = {
    var result = HashCode.SEED
    result = HashCode.hash(result, key)
    result = HashCode.hash(result, bucketSize)
    result
  }

  import org.apache.pekko.persistence.cassandra._

  override def toString =
    s"TimeBucket($key, $bucketSize, inPast: $inPast, currentBucket: $isCurrent. time: ${formatUnixTime(key)} )"
}
