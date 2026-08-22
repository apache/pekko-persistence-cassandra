# Configuration

Make your edits/overrides in your `application.conf`.

## Default configuration

The reference configuration file with the default values:

@@snip [reference.conf](/core/src/main/resources/reference.conf)

Journal configuration is under `pekko.persistence.cassandra.journal`.

Snapshot configuration is under `pekko.persistence.cassandra.snapshot`.

Query configuration is under `pekko.persistence.cassandra.query`.

Events by tag configuration is under `pekko.persistence.cassandra.events-by-tag` and shared
b `journal` and `query`.

The settings that shared by the `journal`, `query`, and `snapshot` parts of the plugin and are under
`pekko.persistence.cassandra`.

## Cassandra driver configuration

All Cassandra driver settings are via its @extref:[standard profile mechanism](java-driver:manual/core/configuration/).

One important setting is to configure the database driver to retry the initial connection:

`datastax-java-driver.advanced.reconnect-on-init = true`

It is not enabled automatically as it is in the driver's reference.conf and is not overridable in a profile.

If the ip addresses of your cassandra nodes might change (e.g. if you use k8s) then 

`datastax-java-driver.advanced.resolve-contact-points = false`

should also be set (resolves a dns address again when new connections are created). This also implies disabling java's dns cache with `-Dnetworkaddress.cache.ttl=0`. 


### Page size

The page size controls how many rows the driver retrieves per network round-trip. Queries that can return many
rows are fetched a page at a time, and the driver requests the next page automatically as the results are
consumed, so this is a matter of how the reads are chunked rather than how many rows an operation returns.

The driver default is 5000 rows. To change it for every query:

```
datastax-java-driver.basic.request.page-size = 1000
```

The plugin issues its queries under two execution profiles, so the page size can also be set for one of them on
its own. The journal and query parts use `pekko-persistence-cassandra-profile`, the snapshot store uses
`pekko-persistence-cassandra-snapshot-profile`:

```
datastax-java-driver.profiles {
  pekko-persistence-cassandra-profile {
    basic.request.page-size = 1000
  }
  pekko-persistence-cassandra-snapshot-profile {
    basic.request.page-size = 100
  }
}
```

A smaller page size lowers the number of rows held per round-trip and the amount of work in a single Cassandra
read; a larger one reduces the number of round-trips needed to read a large result set. It bounds the rows in
flight, not the total an operation retains, so it does not by itself cap the memory used by a query whose whole
result is collected before it is acted on.

### Cassandra driver overrides

@@snip [reference.conf](/core/src/main/resources/reference.conf) { #profile }

## Contact points configuration

The Cassandra server contact points can be defined with the @extref:[Cassandra driver configuration](java-driver:manual/core/configuration/)

```
datastax-java-driver {
  basic.contact-points = ["127.0.0.1:9042"]
  basic.load-balancing-policy.local-datacenter = "datacenter1"
}
```

Alternatively, Pekko Discovery can be used for finding the Cassandra server contact points as described
in the @extref:[Pekko Connectors Cassandra documentation](pekko-connectors:cassandra.html#using-pekko-discovery).

Without any configuration it will use `localhost:9042` as default.
