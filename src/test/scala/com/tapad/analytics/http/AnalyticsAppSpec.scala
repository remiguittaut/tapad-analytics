package com.tapad.analytics.http

import com.tapad.analytics.mocks.MetricsBrokerMock
import com.tapad.analytics.mocks.MetricsBrokerMock.Report
import com.tapad.analytics.model.MetricEvent
import zio.Scope
import zio.http._
import zio.http.model._
import zio.test._

import java.time.Instant

object AnalyticsAppSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("The analytics App")(
      querySuite + ingestSuite
    )

  lazy val querySuite /*: Spec[Any, Throwable]*/ = {
    val timestamp = Instant.now.toEpochMilli

    val goodRequest =
      Request(
        Body.empty,
        Headers.empty,
        Method.GET,
        URL(!! / s"analytics", queryParams = QueryParams("timestamp" -> timestamp.toString)),
        Version.Http_1_1,
        None
      )

    val badRequestNoTs = goodRequest.copy(url = goodRequest.url.copy(queryParams = QueryParams.empty))

    val badRequestBadTs =
      goodRequest.copy(url = goodRequest.url.copy(queryParams = QueryParams("timestamp" -> "nojSNJ")))

    suite("When the client queryies")(
      test(
        "should answer successfully on the GET /analytics?timestamp=??? uri, if a valid epoch timestamp is provided"
      ) {
        Endpoints
          .query(goodRequest)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status.isSuccess))
      },
      test("should answer with text plain content") {
        Endpoints
          .query(goodRequest)
          .mapBoth(
            _ => new Throwable,
            response =>
              assertTrue(response.headerValue(HeaderNames.contentType).contains(HeaderValues.textPlain.toString))
          )
      },
      test("should answer with content of the right format") {
        def contentIsCorrect(content: String) =
          content.split('\n').toList match {
            case uniqueUsers :: clicks :: impressions :: Nil =>
              (uniqueUsers.split(',').toList, clicks.split(',').toList, impressions.split(',').toList) match {
                case (metric1 :: value1 :: Nil, metric2 :: value2 :: Nil, metric3 :: value3 :: Nil) =>
                  metric1 == "unique_users" && value1.toLongOption.isDefined &&
                    metric2 == "clicks" && value2.toLongOption.isDefined &&
                    metric3 == "impressions" && value3.toLongOption.isDefined
                case _ => false
              }
            case _ => false
          }

        Endpoints
          .query(goodRequest)
          .orElseFail(new Throwable)
          .flatMap(_.body.asString)
          .map(body => assertTrue(contentIsCorrect(body)))
      },
      test(
        "should fail with BadRequest if no epoch timestamp is provided as query param"
      ) {
        Endpoints
          .query(badRequestNoTs)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status == Status.BadRequest))
      },
      test(
        "should fail with BadRequest if a bad epoch timestamp is provided as query param"
      ) {
        Endpoints
          .query(badRequestBadTs)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status == Status.BadRequest))
      }
    )
  }

  lazy val ingestSuite /*: Spec[MProxy, Throwable]*/ = {
    val timestamp = Instant.now.toEpochMilli
    val user      = "98237982374"
    val metric    = "click"

    val goodRequestClick =
      Request(
        Body.empty,
        Headers.empty,
        Method.POST,
        URL(
          !! / s"analytics",
          queryParams = QueryParams(
            "timestamp" -> timestamp.toString,
            "user"      -> user,
            metric      -> ""
          )
        ),
        Version.Http_1_1,
        None
      )

    val goodRequestImpression =
      goodRequestClick.copy(url =
        goodRequestClick.url.copy(queryParams = (goodRequestClick.url.queryParams - "click").add("impression", ""))
      )

    val badRequestMissingParam =
      goodRequestClick.copy(url = goodRequestClick.url.copy(queryParams = goodRequestClick.url.queryParams - "user"))

    val badRequestBadMetric =
      goodRequestClick.copy(url =
        goodRequestClick.url.copy(queryParams = (goodRequestClick.url.queryParams - metric).add("click__", ""))
      )

    val badRequestBadTs =
      goodRequestClick.copy(url =
        goodRequestClick.url.copy(queryParams = (goodRequestClick.url.queryParams - metric).add("timestamp", "-10"))
      )

    def requestForMetric(m: MetricEvent): Request =
      Request(
        Body.empty,
        Headers.empty,
        Method.POST,
        URL(
          !! / s"analytics",
          queryParams = QueryParams(
            "timestamp" -> m.timestamp.toString,
            "user"      -> m.user,
            m.metric    -> ""
          )
        ),
        Version.Http_1_1,
        None
      )

    suite("When the client pushes metrics")(
      test(
        "should answer successfully on the /analytics?timestamp={millis_since_epoch}&user={username}&{click|impression} uri, " +
          "if valid query params are provided"
      ) {
        Endpoints
          .ingest(goodRequestClick)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status.isSuccess) && assertCompletes)
          .provideLayer(MetricsBrokerMock.compose)
      },
      test("should answer with no content") {
        Endpoints
          .ingest(goodRequestImpression)
          .mapBoth(
            _ => new Throwable,
            response => assertTrue(response.status == Status.NoContent)
          )
          .provideLayer(MetricsBrokerMock.compose)
      },
      test(
        "should report the event to the metrics broker"
      ) {

        (for {
          _              <- Endpoints.ingest(goodRequestClick).orElseFail(new Throwable)
          wasReported    <- MetricsBrokerMock.wasInvokedWith[Report, MetricEvent](MetricEvent(timestamp, user, metric))
          wasInvokedOnce <- MetricsBrokerMock.wasInvokedOnce
        } yield assertTrue(wasReported) && assertTrue(wasInvokedOnce))
          .provideLayer(MetricsBrokerMock.compose)
      },
      test(
        "should fail with BadRequest if a query param is missing"
      ) {
        (for {
          response      <- Endpoints.ingest(badRequestMissingParam).orElseFail(new Throwable)
          wasNotInvoked <- MetricsBrokerMock.wasNotInvoked
        } yield assertTrue(response.status == Status.BadRequest) && assertTrue(wasNotInvoked))
          .provideLayer(MetricsBrokerMock.compose)
      },
      test(
        "should fail with BadRequest if a bad epoch timestamp is provided as query param"
      ) {
        (for {
          response      <- Endpoints.ingest(badRequestBadTs).orElseFail(new Throwable)
          wasNotInvoked <- MetricsBrokerMock.wasNotInvoked
        } yield assertTrue(response.status == Status.BadRequest) && assertTrue(wasNotInvoked))
          .provideLayer(MetricsBrokerMock.compose)
      },
      test(
        "should fail with BadRequest if a bad metric type is provided as query param"
      ) {
        (for {
          response      <- Endpoints.ingest(badRequestBadMetric).orElseFail(new Throwable)
          wasNotInvoked <- MetricsBrokerMock.wasNotInvoked
        } yield assertTrue(response.status == Status.BadRequest) && assertTrue(wasNotInvoked))
          .provideLayer(MetricsBrokerMock.compose)
      } /*,
      test(
        "should parse the metrics params and report the event to the metricBroker"
      ) {

        for {
          now <- Clock.ClockLive.instant.map(_.toEpochMilli)
          lastHAgoTs = now - (now % 3_600_000)
          today      = now - (now % (3_600_000 * 24))
          // Not exactly the definition of the exercise, supposed to be 95% matching the current ts,
          // but for testing, it's good enough to generate a distribution of 95% within the current hour
          // and 5% from today, before. ts are also not in order...
          outDatedMetrics <- genMetric(today, lastHAgoTs).runCollectN(50)
          metrics         <- genMetric(lastHAgoTs, now).runCollectN(950)
          all             <- Random.shuffle(outDatedMetrics ::: metrics)
          broker          <- ZIO.service[MetricsBroker]
          _ <- ZIO
            .foreachParDiscard(all.map(requestForMetric)) { req =>
              Apps.ingestEndpoint(req).orElseFail(new Throwable)
            }
          reported <- events.get
        } yield assertTrue(all.forall(reported.contains)) && assertTrue(reported.size == 1000)
        // TODO: Is order guaranteed? If so, find a better assert
      }*/
    )
  }
}
