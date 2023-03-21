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
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.persistence.cassandra.reconciler.Reconciliation

//#imports

class AllPersistenceIdsMigrationCompileOnly {

  // #migrate
  // System should have the same Cassandra plugin configuration as your application
  // but be careful to remove seed nodes so this doesn't join the cluster
  val system = ActorSystem()
  import system.dispatcher

  val rec = new Reconciliation(system)
  val result = rec.rebuildAllPersistenceIds()

  result.onComplete {
    case Success(_) =>
      system.log.info("All persistenceIds migrated.")
      system.terminate()
    case Failure(e) =>
      system.log.error(e, "All persistenceIds migration failed.")
      system.terminate()
  }
  // #migrate
}
