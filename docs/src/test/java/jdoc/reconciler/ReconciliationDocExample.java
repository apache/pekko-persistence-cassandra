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

package jdoc.reconciler;

// #imports
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.cassandra.reconciler.javadsl.Reconciliation;

// #imports

public class ReconciliationDocExample {

  public static void example() {

    // #reconcile
    // System should have the same Cassandra plugin configuration as your application
    // but be careful to remove seed nodes so this doesn't join the cluster
    ActorSystem system = ActorSystem.create();

    Reconciliation rec = new Reconciliation(system);

    // Drop and re-create data for a persistence id
    String pid = "pid1";
    CompletionStage<Done> result =
        // do this before dropping the data
        rec.tagsForPersistenceId(pid)
            .thenCompose(
                tags ->
                    // drop the tag view for every tag for this persistence id
                    CompletableFuture.allOf(
                        tags.stream()
                            .map(
                                tag ->
                                    rec.deleteTagViewForPersistenceIds(
                                            Collections.singleton(pid), tag)
                                        .toCompletableFuture())
                            .toArray(CompletableFuture[]::new)))
            // optional: re-build, if this is omitted then it will be re-build next time the pid is
            // started
            .thenCompose(dropped -> rec.rebuildTagViewForPersistenceIds(pid));

    // release the tag writers actor that the Reconciliation started
    result.whenComplete((done, failure) -> rec.close());
    // #reconcile
  }
}
