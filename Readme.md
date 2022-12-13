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

## Update / instructions:

So far, only the influxdb implementation is provided, without only local streams / hub, 
which means that it does not work in a distributed environment, only with a single replica.

The api needs a reachable instance of influxdb, whether it is a managed version or a local container for testing.

For convenience, a script is given to launch influxdb using docker. the script must be executed from an empty directory,
and will create a directory called `influx-data` mounted as a volume in the container.

All the values can be changed in the script (port, user password, auth-token, etc), but the app config
must be updated to match these values in `./src/main/resources/application.conf` OR can be overriden through environment
variables when running the API (names can be found in the conf file).

This first working version is far from being production ready, because of lack of time, but should already
allow to test most of the demanded features. It lacks enough tests, a better config layer, better logging conf,
the ability to run with several replicas, etc.

If I manage, I'll try to get a fast cache for the current hour working with redis counters and HyperLogLog by this evening,
but that will be short...

What is lacking for several replicas is simply pushing to Kafka from en ingest endpoint, and subscribing with a stream 
broadcasting to 2 local streams. If we are in the current hour, broacast to write to influx and incr counters in Redis 
(plus unique users using an HyperLogLog data structure), and commit the offset only when the batch was correctly written to 
influx (acting as "cold" storage, but already fast).
It means 3 things:
  - We have a guaranteed durability in cold storage
  - we are eventually consistent in the numbers for the current hour.
  - We could easily add a parameter to the endpoint for the user to require non-staleness and forcing querying cold storage

To summarize, in ACID, we have:
  - Atomicity: I did not have time to think about a deduplication mechanism. (I'm pretty sure that Kafka is at-least-once delivery)
  - Consistency: We're eventually consistent for the current hour, but guaranteed consistent afterwards. We could require both wites to go through before comitting if it's not sufficient. A secondary queue with retries could solve that
  - Isolation: I did not have time to solve this one in case of replicas. Would need to dive into Kafka doc to better understand if we can have only 1 worker getting given events.
  - Durability: We're guaranteed that events will be persisted in influx and not lost. 

Remark about HyperLogLog, integrated in Redis: It's not 100% guaranteed to be correct, but nearly, and if Reddit uses it for 
unique posts views, I'd say it's good enough. We know that unique visitors is a hard problem, so it is a good middle-ground.

Another missing thing is to get rid of all the shortcuts throughout the API regarding errors. Everything is Throwable based,
if not ignored. With more time I would define a proper error types domain, mapping at the end of the world to status codes.

I feel that this is unfinished, and would have like to make it a bit more "production ready" with more time,
but I might have been a bit too ambitious, especially learning new things along the way, like influxdb, which turned out to be
really great. No matter the result of this test, at least I will have learnt something...

Part of the plan was also to write some IT tests launching an influx test container to test actual ingestion and querying

To be continued.