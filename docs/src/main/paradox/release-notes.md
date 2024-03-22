# Release Notes

## 1.0.0
Apache Pekko Persistence Cassandra 1.0.0 is based on Akka Persistence Cassandra 1.0.6. Pekko came about as a result of Lightbend's decision to make future
Akka releases under a [Business Software License](https://www.lightbend.com/blog/why-we-are-changing-the-license-for-akka),
a license that is not compatible with Open Source usage.

Apache Pekko has changed the package names, among other changes. The new packages begin with `org.apache.pekko.persistence.cassandra` instead of `akka.persistence.cassandra`.

Config names have changed to use `pekko` instead of `akka` in their names.

Users switching from Akka to Pekko should read our [Migration Guide](https://pekko.apache.org/docs/pekko/current/project/migration-guides.html).

Generally, we have tried to make it as easy as possible to switch existing Akka based projects over to using Pekko.

We have gone through the code base and have tried to properly acknowledge all third party source code in the
Apache Pekko code base. If anyone believes that there are any instances of third party source code that is not
properly acknowledged, please get in touch.

### Bug Fixes
We haven't had to fix any significant bugs that were in Akka Persistence Cassandra 1.0.6.

### Additions

* Scala 3 support (minimum version of Scala 3.3.0)

### Dependency Upgrades
We have tried to limit the changes to third party dependencies that were used in Akka Persistence Cassandra 1.0.6. These are some exceptions:

* com.datastax.oss:java-driver-core was updated to 4.15.0

### Known Issues
We hope to fix the following issue in a new release in the near future.

* (103)[https://github.com/apache/pekko-persistence-cassandra/issues/103]: There is a bug where if you experience Cassandra connectivity issues when initializing then Pekko Persistence Cassandra 1.0.0 does not recover.
