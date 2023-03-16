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

import org.apache.pekko.actor.{ ExtendedActorSystem, Props }
import org.apache.pekko.persistence.RecoveryCompleted
import org.apache.pekko.persistence.cassandra.{ CassandraLifecycle, CassandraSpec, Persister }
import org.apache.pekko.serialization.BaseSerializer
import org.apache.pekko.testkit.TestProbe
import com.typesafe.config.ConfigFactory

object CassandraSerializationSpec {
  val config = ConfigFactory.parseString(s"""
       |akka.actor.serialize-messages=false
       |akka.actor.serializers.crap="pekko.persistence.cassandra.journal.BrokenDeSerialization"
       |akka.actor.serialization-identifiers."pekko.persistence.cassandra.journal.BrokenDeSerialization" = 666
       |akka.actor.serialization-bindings {
       |  "pekko.persistence.cassandra.Persister$$CrapEvent" = crap
       |}
       |akka.persistence.journal.max-deletion-batch-size = 3
       |akka.persistence.publish-confirmations = on
       |akka.persistence.publish-plugin-commands = on
       |pekko.persistence.cassandra.journal.target-partition-size = 5
       |pekko.persistence.cassandra.max-result-size = 3
       |pekko.persistence.cassandra.journal.keyspace=CassandraIntegrationSpec
       |pekko.persistence.cassandra.snapshot.keyspace=CassandraIntegrationSpecSnapshot
       |
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

  import org.apache.pekko.persistence.cassandra.Persister._

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
