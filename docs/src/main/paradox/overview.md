# Overview

The Pekko Persistence Cassandra plugin allows for using [Apache Cassandra](https://cassandra.apache.org) as a backend for @extref:[Pekko Persistence](pekko:typed/persistence.html) and @extref:[Pekko Persistence Query](pekko:persistence-query.html). It uses @extref:[Pekko Connectors Cassandra](pekko-connectors:cassandra.html) for Cassandra access which is based on the @extref:[Datastax Java Driver](java-driver:).

## Project Info

@@project-info{ projectId="core" }

## Dependencies

This plugin requires **Pekko $pekko.version$** or later. See [Pekko's Binary Compatibility Rules](https://pekko.apache.org/docs/pekko/current/common/binary-compatibility-rules.html) for details.

@@dependency [sbt,Maven,Gradle] {
  group=org.apache.pekko
  artifact=pekko-persistence-cassandra_$scala.binary.version$
  version=$project.version$
  symbol=PekkoVersion
  value=$pekko.version$
  group1=org.apache.pekko
  artifact1=pekko-persistence_$scala.binary.version$
  version1=PekkoVersion
  group2=org.apache.pekko
  artifact2=pekko-persistence-query_$scala.binary.version$
  version2=PekkoVersion
  group3=org.apache.pekko
  artifact3=pekko-cluster-tools_$scala.binary.version$
  version3=PekkoVersion
}

Note that it is important that all `pekko-*` dependencies are in the same version, so it is recommended to depend on them explicitly to avoid problems with transient dependencies causing an unlucky mix of versions.

The table below shows Pekko Persistence Cassandra’s direct dependencies and the second tab shows all libraries it depends on transitively.

@@dependencies{ projectId="core" }

## Snapshots

Snapshots are published to a snapshot repository in Sonatype after every successful build on main branch. Add the following to your project build definition to resolve snapshots:

sbt
:   ```scala
    resolvers += "Apache Snapshots" at "https://repository.apache.org/content/groups/snapshots"
    ```

Maven
:   ```xml
    <project>
    ...
      <repositories>
        <repository>
          <id>apache-snapshots</id>
          <name>Apache Snapshots</name>
          <url>https://repository.apache.org/content/groups/snapshots</url>
        </repository>
      </repositories>
    ...
    </project>
    ```

Gradle
:   ```gradle
    repositories {
      maven {
        url  "https://repository.apache.org/content/groups/snapshots"
      }
    }
    ```

Snapshot builds are available at [https://repository.apache.org/content/groups/snapshots/org/apache/pekko/](https://repository.apache.org/content/groups/snapshots/org/apache/pekko/). All Pekko modules that belong to the same build have the same version.

The [snapshot documentation](https://nightlies.apache.org/pekko/docs/pekko-persistence-cassandra/main-snapshot/) is updated with every snapshot build.

## History

This [Apache Cassandra](https://cassandra.apache.org/) plugin to Pekko Persistence was initiated [originally](https://github.com/krasserm/akka-persistence-cassandra) by Martin Krasser, [@krasserm](https://github.com/krasserm) in 2014.

It moved to the [Akka](https://github.com/akka/) organisation in 2016 and the first release after that move was 0.7 in January 2016.

Apache Pekko forked the plugin after Akka changed its licensing.

## Contributing

Please feel free to contribute to Pekko and Pekko Persistence Cassandra by reporting issues you identify, or by suggesting changes to the code. Please refer to our [contributing instructions](https://github.com/apache/incubator-pekko/blob/main/CONTRIBUTING.md) to learn how it can be done.

We want Pekko to strive in a welcoming and open atmosphere and expect all contributors to respect our [code of conduct](https://www.apache.org/foundation/policies/conduct.html).
