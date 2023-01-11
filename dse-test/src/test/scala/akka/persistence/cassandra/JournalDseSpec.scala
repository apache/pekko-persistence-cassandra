/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

package akka.persistence.cassandra

import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory

object JournalDseSpec {
  val config = ConfigFactory.parseString(s"""
   akka.persistence.cassandra.journal.keyspace=JournalDseSpec
   akka.persistence.cassandra.snapshot.keyspace=JournalDseSpec
                                 
   //# override-session-provider
   akka.persistence.cassandra {
     session-provider = "your.pack.DseSessionProvider"
   }
   //# override-session-provider
    """).withFallback(CassandraLifecycle.config)

}

class JournalDseSpec extends JournalSpec(JournalDseSpec.config) with CassandraLifecycle {
  override def systemName: String = "JournalDseSpec"
  override def supportsRejectingNonSerializableObjects = false
}
