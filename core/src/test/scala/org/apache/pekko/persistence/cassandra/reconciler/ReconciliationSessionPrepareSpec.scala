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

package org.apache.pekko.persistence.cassandra.reconciler

import org.apache.pekko
import pekko.persistence.cassandra.{ CassandraSpec, CassandraStatements, PluginSettings }
import pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import pekko.stream.scaladsl.Sink
import com.typesafe.config.ConfigValueFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{ Seconds, Span }

object ReconciliationSessionPrepareSpec {
  // not created until the test does so, so that the first prepare of every statement fails
  val laterKeyspace = "ReconciliationSessionPrepareLater"
}

class ReconciliationSessionPrepareSpec extends CassandraSpec with Eventually {
  import ReconciliationSessionPrepareSpec._

  override def keyspaces(): Set[String] = super.keyspaces() + laterKeyspace

  "ReconciliationSession" should {
    "recover once a statement that failed to prepare can be prepared" in {
      import system.dispatcher
      val pluginConfig = system.settings.config
        .getConfig(PluginSettings.DefaultConfigPath)
        .withValue("journal.keyspace", ConfigValueFactory.fromAnyRef(laterKeyspace))
      val statements = new CassandraStatements(PluginSettings(system, pluginConfig))
      val session = CassandraSessionRegistry(system).sessionFor(PluginSettings.DefaultConfigPath)

      val reconciliationSession = new ReconciliationSession(session, statements)
      reconciliationSession.deleteTagProgress("tag", "pid").failed.futureValue

      session.underlying().flatMap(statements.executeAllCreateKeyspaceAndTables(_, system.log))
        .futureValue(timeout(Span(30, Seconds)))

      // the failed prepare is forgotten asynchronously, so a call made straight away can still see it
      eventually {
        reconciliationSession.deleteTagProgress("tag", "pid").futureValue
      }
      reconciliationSession.deleteTagScannning("pid").futureValue
      reconciliationSession.selectTagProgress("pid").runWith(Sink.seq).futureValue shouldBe empty
    }
  }
}
