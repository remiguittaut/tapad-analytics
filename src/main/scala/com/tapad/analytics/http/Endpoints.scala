package com.tapad.analytics.http

import java.time.Instant

import com.tapad.analytics.MetricsBroker
import com.tapad.analytics.http.ops._
import com.tapad.analytics.model.MetricEvent
import com.tapad.analytics.query.QueryService

import zio._
import zio.http._
import zio.http.model.{ Method, Status }

object Endpoints {

  lazy val query: HttpApp[QueryService, Nothing] = Http
    .collectZIO[Request] { case req @ Method.GET -> !! / "analytics" =>
      for {
        time <- ZIO
          .from(req.getParam[Instant]("timestamp"))
          .orElseFail(
            Response
              .badRequest(s"missing valid 'timestamp' query parameter format (should be epoch milli)")
          )
        result <- QueryService
          .queryForTimestamp(time)
          .tapErrorCause(ZIO.logErrorCause(_))
          .mapError(err =>
            Response
              .status(Status.InternalServerError)
              .copy(body = Body.fromString(err.getMessage))
          )
      } yield Response.text(result.asText)
    }
    .merge

  // POST /analytics?timestamp={millis_since_epoch}&user={username}&{click|impression}
  lazy val ingest: HttpApp[MetricsBroker, Nothing] = Http
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
        _ <- MetricsBroker.report(metricEvent)
      } yield Response.status(Status.NoContent)
    }
    .merge
}
