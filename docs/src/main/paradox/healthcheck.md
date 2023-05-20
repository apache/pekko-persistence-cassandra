# Health check

A [health check for Pekko Management](https://pekko.apache.org/docs/pekko-management/current/healthchecks.html)
is provided. To enable it you need to add the following configuration

```
pekko.management {
  health-checks {
    readiness-checks {
      pekko-persistence-cassandra = "org.apache.pekko.persistence.cassandra.healthcheck.CassandraHealthCheck"
    }
  }
}
```

By default, it will try to query the `system.local` table. The query can be configured with:

```
pekko.persistence.cassandra.healthcheck {
  health-check-cql = "SELECT now() FROM system.local"
}
``` 

 
