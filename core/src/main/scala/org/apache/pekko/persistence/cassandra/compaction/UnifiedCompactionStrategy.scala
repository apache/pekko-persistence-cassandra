/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence.cassandra.compaction

import com.typesafe.config.Config

/**
 * The Unified Compaction Strategy (UCS), added in Cassandra 5.0. Using this strategy against an
 * older Cassandra version (or a database that does not support UCS) will fail when the table is created.
 *
 * Only the options set in the config are sent to Cassandra, so Cassandra's own defaults apply
 * for the rest. Sizes (`target_sstable_size`, `min_sstable_size`) and `scaling_parameters`
 * are passed through as strings, e.g. `"1GiB"` or `"T4"`, and are validated by Cassandra.
 *
 * See https://cassandra.apache.org/doc/stable/cassandra/managing/operating/compaction/ucs.html
 */
class UnifiedCompactionStrategy(config: Config)
    extends BaseCompactionStrategy(
      config,
      UnifiedCompactionStrategy.ClassName,
      UnifiedCompactionStrategy.propertyKeys) {
  import UnifiedCompactionStrategy._

  private def stringOpt(key: String): Option[String] =
    if (config.hasPath(key)) Some(config.getString(key)) else None
  private def intOpt(key: String): Option[Int] =
    if (config.hasPath(key)) Some(config.getInt(key)) else None
  private def longOpt(key: String): Option[Long] =
    if (config.hasPath(key)) Some(config.getLong(key)) else None
  private def doubleOpt(key: String): Option[Double] =
    if (config.hasPath(key)) Some(config.getDouble(key)) else None
  private def booleanOpt(key: String): Option[Boolean] =
    if (config.hasPath(key)) Some(config.getBoolean(key)) else None

  val scalingParameters: Option[String] = stringOpt(ScalingParameters)
  val targetSSTableSize: Option[String] = stringOpt(TargetSSTableSize)
  val minSSTableSize: Option[String] = stringOpt(MinSSTableSize)
  val baseShardCount: Option[Int] = intOpt(BaseShardCount)
  val sstableGrowth: Option[Double] = doubleOpt(SSTableGrowth)
  val maxSSTablesToCompact: Option[Int] = intOpt(MaxSSTablesToCompact)
  val expiredSSTableCheckFrequencySeconds: Option[Long] = longOpt(ExpiredSSTableCheckFrequencySeconds)
  val unsafeAggressiveSSTableExpiration: Option[Boolean] = booleanOpt(UnsafeAggressiveSSTableExpiration)
  val overlapInclusionMethod: Option[String] = stringOpt(OverlapInclusionMethod)

  baseShardCount.foreach(n => require(n > 0, s"$BaseShardCount must be larger than 0, but was $n"))
  sstableGrowth.foreach(g =>
    require(g >= 0.0 && g <= 1.0, s"$SSTableGrowth must be between 0 and 1, but was $g"))
  maxSSTablesToCompact.foreach(n =>
    require(n >= 0, s"$MaxSSTablesToCompact must be larger than or equal to 0, but was $n"))
  expiredSSTableCheckFrequencySeconds.foreach(n =>
    require(n > 0, s"$ExpiredSSTableCheckFrequencySeconds must be larger than 0, but was $n"))

  private def quote(s: String): String = s"'${s.replace("'", "''")}'"

  override def asCQL: String = {
    val options: List[String] =
      List(
        scalingParameters.map(v => s"'$ScalingParameters' : ${quote(v)}"),
        targetSSTableSize.map(v => s"'$TargetSSTableSize' : ${quote(v)}"),
        minSSTableSize.map(v => s"'$MinSSTableSize' : ${quote(v)}"),
        baseShardCount.map(v => s"'$BaseShardCount' : $v"),
        sstableGrowth.map(v => s"'$SSTableGrowth' : $v"),
        maxSSTablesToCompact.map(v => s"'$MaxSSTablesToCompact' : $v"),
        expiredSSTableCheckFrequencySeconds.map(v => s"'$ExpiredSSTableCheckFrequencySeconds' : $v"),
        unsafeAggressiveSSTableExpiration.map(v => s"'$UnsafeAggressiveSSTableExpiration' : $v"),
        overlapInclusionMethod.map(v => s"'$OverlapInclusionMethod' : ${quote(v.toUpperCase)}")).flatten

    (s"'class' : '$ClassName'" :: super.asCQL :: options).mkString("{\n", ",\n", "\n}")
  }
}

object UnifiedCompactionStrategy extends CassandraCompactionStrategyConfig[UnifiedCompactionStrategy] {
  override val ClassName: String = "UnifiedCompactionStrategy"

  private val ScalingParameters = "scaling_parameters"
  private val TargetSSTableSize = "target_sstable_size"
  private val MinSSTableSize = "min_sstable_size"
  private val BaseShardCount = "base_shard_count"
  private val SSTableGrowth = "sstable_growth"
  private val MaxSSTablesToCompact = "max_sstables_to_compact"
  private val ExpiredSSTableCheckFrequencySeconds = "expired_sstable_check_frequency_seconds"
  private val UnsafeAggressiveSSTableExpiration = "unsafe_aggressive_sstable_expiration"
  private val OverlapInclusionMethod = "overlap_inclusion_method"

  override def propertyKeys: List[String] =
    (BaseCompactionStrategy.propertyKeys ++ List(
      ScalingParameters,
      TargetSSTableSize,
      MinSSTableSize,
      BaseShardCount,
      SSTableGrowth,
      MaxSSTablesToCompact,
      ExpiredSSTableCheckFrequencySeconds,
      UnsafeAggressiveSSTableExpiration,
      OverlapInclusionMethod)).sorted

  override def fromConfig(config: Config): UnifiedCompactionStrategy =
    new UnifiedCompactionStrategy(config)
}
