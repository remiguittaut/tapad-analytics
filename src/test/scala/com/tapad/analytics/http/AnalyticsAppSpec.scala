package com.tapad.analytics.http

import com.tapad.analytics.ingest.model.MetricEvent
import com.tapad.analytics.ingest.services.IngestService
import com.tapad.analytics.ingest.services.IngestService.TestIngestService
import zio._
import zio.http._
import zio.http.model._
import zio.test._

import java.time.temporal.ChronoUnit
import java.time.{ Instant, OffsetDateTime, ZoneId }

object AnalyticsAppSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("The analytics App")(
      (querySuite + ingestSuite).provide(IngestService.test)
    )

  lazy val querySuite: Spec[TestIngestService, Throwable] = {
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
        Apps
          .analytics(goodRequest)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status.isSuccess))
      },
      test("should answer with text plain content") {
        Apps
          .analytics(goodRequest)
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

        Apps
          .analytics(goodRequest)
          .orElseFail(new Throwable)
          .flatMap(_.body.asString)
          .map(body => assertTrue(contentIsCorrect(body)))
      },
      test(
        "should fail with BadRequest if no epoch timestamp is provided as query param"
      ) {
        Apps
          .analytics(badRequestNoTs)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status == Status.BadRequest))
      },
      test(
        "should fail with BadRequest if a bad epoch timestamp is provided as query param"
      ) {
        Apps
          .analytics(badRequestBadTs)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status == Status.BadRequest))
      }
    )
  }

  lazy val ingestSuite: Spec[TestIngestService, Throwable] = {
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

    def genMetric(from: Instant, to: Instant): Gen[Any, MetricEvent] =
      Gen
        .instant(from, to)
        .map(_.toEpochMilli)
        .zip(Gen.stringN(12)(Gen.alphaChar))
        .zip(Gen.elements("click", "impression"))
        .map(MetricEvent.tupled)

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
        Apps
          .analytics(goodRequestClick)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status.isSuccess))
      },
      test("should answer with no content") {
        Apps
          .analytics(goodRequestImpression)
          .mapBoth(
            _ => new Throwable,
            response => assertTrue(response.status == Status.NoContent)
          )
      },
      test(
        "should fail with BadRequest if a query param is missing"
      ) {
        Apps
          .analytics(badRequestMissingParam)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status == Status.BadRequest))
      },
      test(
        "should fail with BadRequest if a bad epoch timestamp is provided as query param"
      ) {
        Apps
          .analytics(badRequestBadTs)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status == Status.BadRequest))
      },
      test(
        "should fail with BadRequest if a bad metric type is provided as query param"
      ) {
        Apps
          .analytics(badRequestBadMetric)
          .mapBoth(_ => new Throwable, response => assertTrue(response.status == Status.BadRequest))
      },
      test(
        "should parse the metrics params and report the event to the ingestService"
      ) {
        val utc = ZoneId.of("UTC")

        for {
          now <- Clock.instant
          lastHAgoTs = OffsetDateTime
            .ofInstant(now, utc)
            .truncatedTo(ChronoUnit.HOURS)
            .toInstant
          today = OffsetDateTime
            .ofInstant(now, utc)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant
          // Not exactly the definition of the exercise, supposed to be 95% matching the current ts,
          // but for testing, it's good enough to generate a distribution of 95% within the current hour
          // and 5% from today, before. ts are also not in order...
          outDatedMetrics <- genMetric(today, lastHAgoTs).runCollectN(50)
          metrics         <- genMetric(lastHAgoTs, now).runCollectN(950)
          ingestService   <- ZIO.service[TestIngestService]
          all             <- Random.shuffle(outDatedMetrics ::: metrics)
          _ <- ZIO
            .foreachParDiscard(all.map(requestForMetric)) { req =>
              Apps.analytics(req).orElseFail(new Throwable)
            }
          reported <- ingestService.reportedEvents.takeAll
        } yield assertTrue(metrics.forall(reported.contains))
      }
    )
  }
}
