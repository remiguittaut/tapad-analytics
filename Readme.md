# Tapad analytics api

## Initial assignement

A server accepts the following HTTP requests:
```
POST /analytics?timestamp={millis_since_epoch}&user={username}&{click|impression}
```
```
GET /analytics?timestamp={millis_since_epoch}
```

When the `POST` request is made, nothing is returned to the client. We simply side-effect and add the details from the request to our tracking data store.

When the `GET` request is made, we return the following information in plain-text to the client, for the hour of the requested timestamp:
```
unique_users,{number_of_unique_usernames}
clicks,{number_of_clicks}
impressions,{number_of_impressions}
```

The server will receive many more `GET` requests (95%) for the **current hour** than for hours in the past (5%).

Most `POST` requests received by the server will contain a timestamp that matches the current timestamp (95%). However, it is possible for the timestamp in a `POST` request to contain a *historic* timestamp (5%) -- e.g. it is currently the eighth hour of the day, yet the request contains a timestamp for hour six of the day.

Additional servers should be able to be spun up, at any time, without effecting the correctness of our metrics.

The `POST` and `GET` endpoints should be optimized for high throughput and low latency.

## Initial assumptions

Analytics Api - initial assumptions, implementation ideas.

To solve the problem at hand, I make the following initial assumptions.

- "For the current hour" is interpreted at HH:00:00, (0 * * * *) = no sliding window
- the number of metrics events per second is under 1B (so it's alright to use ISO ts at the nano level).
- Remark: the whole system is based on epoch, so we have no tz constraint, we can put UTC Instants all over the place.

Initial implementation ideas:
- No experience yet with Scala3, I stick to 2.xx
- I'm used to ZIO, so I will use it. Only xp with ZIO 1, but using ZIO 2 should be ok.
- I'm used to zio-http, zio-json, so let's stick to it.
- ZIO ecosystem large enough to try staying ZIO native for everything
- 95% of GET/POST requests are made for the current hour:
    - InMem storage for the current hour, with key expiration at the next hour
        - We want to be super fast -> no updating of the counter in mem for the given user id, we append-only, and will aggregate at query time
          It means that we assume a higher rate of writes than reads (there is no mention of that in the exercise, but we can't have the cake and eat it too, and write events should happen much more often than querying the metrics.)
    - Cold storage for historical. we can pay the query /ingest time price for rare requests, so InMem cache miss are alright for it.
- We need to fork the storage of incoming events between hot and cold
    - we can't have cold ingestion throttling the ingestion in InMem cache
    - we need a queue with 2 subscriptions, so different ingestion rates are OK.
    - it makes historical data consistency eventual but "almost strong".

Plan:
- Initial app structure (base DI, logging, Http App)
- Mocked endpoint to Ingest data, tests
    - arguments parsing
    - etc
- Mocked endpoint to query data, tests
    - same remarks
- Stubs for Ingest service, query service, etc. naming to be decided (might be the most difficult!)
    - initial failing tests
    - upgrade endpoints to live ones using the stubs
    - upgrade endpoints tests
- Make some more research on what 3rd party stuff to use
    - Queue: Kafka seems natural, try to find out if there might something less heavy giving us the subset of features we need.
    - InMemory cache: Redis? memcached could be enough
    - Cold storage: time series db. evaluate between Influxdb, Victoria metrics, timescaledb, something else out there?
    - Don't know much about most of this, let's make the most of the exercise and use the opportunity to learn new stuff.

