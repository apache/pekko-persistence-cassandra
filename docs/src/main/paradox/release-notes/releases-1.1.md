<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Release Notes (1.1.x)

## 1.1.0
Release notes for Apache Pekko Persistence Cassandra 1.1.0. See [GitHub Milestone](https://github.com/apache/pekko-persistence-cassandra/milestone/1?closed=1) for a fuller list of changes.

### Bug Fixes
* When PreparedStatement initialization fails, the code used not to recover ([#103](https://github.com/apache/pekko-persistence-cassandra/issues/103))

### Other Changes
* Improve the pattern matching for CassandraJournal options ([PR28](https://github.com/apache/pekko-persistence-cassandra/pull/28))

### Dependency Upgrades
* Apache Cassandra Driver 4.18 (replaces the Datastax Driver that was used before, this was donated to Apache Cassandra and they released 4.18 from this)
