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

import org.apache.pekko
import pekko.actor.{ ExtendedActorSystem, Props }
import pekko.persistence.RecoveryCompleted
import pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec, Persister }
import pekko.serialization.BaseSerializer
import pekko.testkit.TestProbe
import com.typesafe.config.ConfigFactory

object CassandraSerializationSpec {
  val config = ConfigFactory.parseString(s"""
       |pekko {
       |  actor.serialize-messages=false
       |  actor.serializers.crap="org.apache.pekko.persistence.cassandra.journal.BrokenDeSerialization"
       |  actor.serialization-identifiers."org.apache.pekko.persistence.cassandra.journal.BrokenDeSerialization" = 666
       |  actor.serialization-bindings {
       |    "org.apache.pekko.persistence.cassandra.Persister$$CrapEvent" = crap
       |  }
       |  persistence {
       |    journal.max-deletion-batch-size = 3
       |    publish-confirmations = on
       |    publish-plugin-commands = on
       |    cassandra.journal.target-partition-size = 5
       |    cassandra.max-result-size = 3
       |    cassandra.journal.keyspace=CassandraIntegrationSpec
       |    cassandra.snapshot.keyspace=CassandraIntegrationSpecSnapshot
       |  }
       |}
    """.stripMargin).withFallback(CassandraLifecycle.config)

}

class BrokenDeSerialization(override val system: ExtendedActorSystem) extends BaseSerializer {
  override def includeManifest: Boolean = false
  override def toBinary(o: AnyRef): Array[Byte] =
    // I was serious with the class name
    Array.emptyByteArray
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    throw new RuntimeException("I can't deserialize a single thing")
}

class CassandraSerializationSpec extends CassandraSpec(CassandraSerializationSpec.config) {

  import pekko.persistence.cassandra.Persister._

  "A Cassandra journal" must {

    "Fail recovery when deserialization fails" in {
      val probe = TestProbe()
      val incarnation1 = system.actorOf(Props(new Persister("id1", probe.ref)))
      probe.expectMsgType[RecoveryCompleted]

      incarnation1 ! CrapEvent(1)
      probe.expectMsg(CrapEvent(1))

      probe.watch(incarnation1)
      system.stop(incarnation1)
      probe.expectTerminated(incarnation1)

      val incarnation2 = system.actorOf(Props(new Persister("id1", probe.ref)))
      probe.expectMsgType[RuntimeException].getMessage shouldBe "I can't deserialize a single thing"
      incarnation2
    }

  }

}
