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

package doc.reconciler

//#imports
import org.apache.pekko.persistence.cassandra.reconciler.Reconciliation
import org.apache.pekko.actor.ActorSystem
import scala.concurrent.Future
import org.apache.pekko.Done

//#imports

class ReconciliationCompileOnly {

  // #reconcile
  // System should have the same Cassandra plugin configuration as your application
  // but be careful to remove seed nodes so this doesn't join the cluster
  val system = ActorSystem()
  import system.dispatcher

  val rec = new Reconciliation(system)

  // Drop and re-create data for a persistence id
  val pid = "pid1"
  for {
    // do this before dropping the data
    tags <- rec.tagsForPersistenceId(pid)
    // drop the tag view for every tag for this persistence id
    dropData <- Future.traverse(tags)(tag => rec.deleteTagViewForPersistenceIds(Set(pid), tag))
    // optional: re-build, if this is ommited then it will be re-build next time the pid is started
    _ <- rec.rebuildTagViewForPersistenceIds(pid)
  } yield Done
  // #reconcile
}
