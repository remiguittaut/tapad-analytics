package com.tapad.analytics.http

import zio.{ Random, ZIO }
import zio.http._
import zio.http.model.{ Method, Status }

import java.time.Instant
import com.tapad.analytics.http.ops._
import com.tapad.analytics.ingest.model.MetricEvent
import com.tapad.analytics.ingest.services.IngestService

object Apps {

  val analytics = queryEndpoint ++ ingestEndpoint

  // GET /analytics?timestamp={millis_since_epoch}
  lazy val queryEndpoint: UHttpApp = Http
    .collectZIO[Request] { case req @ Method.GET -> !! / "analytics" =>
      for {
        // we don't actually use the timestamp yet, but, for testing,
        //  we want to validate the bad request answer, so we parse it.
        _ <- ZIO
          .from(req.getParam[Instant]("timestamp"))
          .orElseFail(
            Response
              .badRequest(s"missing valid 'timestamp' query parameter format (should be epoch milli)")
          )
        users       <- Random.nextIntBounded(100_000)
        clicks      <- Random.nextIntBetween(100_000, 1_000_000)
        impressions <- Random.nextIntBetween(100_000, 1_000_000)
      } yield Response.text(
        s"""unique_users,$users
           |clicks,$clicks
           |impressions,$impressions""".stripMargin
      )
    }
    .merge

  // POST /analytics?timestamp={millis_since_epoch}&user={username}&{click|impression}
  lazy val ingestEndpoint: HttpApp[IngestService, Nothing] = Http
    .collectZIO[Request] { case req @ Method.POST -> !! / "analytics" =>
      for {
        // we don't actually use the params yet, but, for testing,
        //  we want to validate the bad request answer, so we parse it.
        ts <- ZIO
          .from(req.getParam[Long]("timestamp"))
          .orElseFail(
            Response
              .badRequest(s"missing valid 'timestamp' query parameter format (should be epoch milli)")
          )
        user <- ZIO
          .from(req.getParam[String]("user").filter(_.nonEmpty))
          .orElseFail(
            Response
              .badRequest(s"missing valid 'user' query parameter")
          )
        metricParam <- ZIO
          .from(req.getParamFirstOf[String]("click", "impression"))
          .orElseFail(
            Response
              .badRequest(s"missing valid 'click' or 'impression' metric name query parameter")
          )
        (metricName, _) = metricParam
        metricEvent     = MetricEvent(ts, user, metricName)
        _ <- IngestService.accept(metricEvent)
      } yield Response.status(Status.NoContent)
    }
    .merge
}
