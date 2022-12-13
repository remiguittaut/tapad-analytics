package com.tapad.analytics.storage

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.tapad.analytics.AppConfig
import com.tapad.analytics.model.{ MetricEvent, QueryResult }
import sttp.client3.{ SttpBackend, basicRequest }

import zio._
import zio.macros.accessible

@accessible
trait InfluxClient {
  def pushMetrics(batch: Chunk[MetricEvent]): Task[Unit]
  def queryMetrics(time: Instant): Task[QueryResult]
}

object InfluxClient {

  val live: ZLayer[Scope with AppConfig with SttpBackend[Task, Any], Throwable, InfluxClient] =
    ZLayer.scoped {
      for {
        client <- ZIO.service[SttpBackend[Task, Any]]
        config <- ZIO.service[AppConfig]
        baseUri = config.influx.endpoint.uri
      } yield new InfluxClient {
        def pushMetrics(batch: Chunk[MetricEvent]): Task[Unit] =
          for {
            _ <- ZIO.logInfo("pushing metrics to influxdb")
            body = batch
              .groupMapReduce(me => (me.user, me.metric))(evt => evt -> 1)({ case ((m, count), (_, thatCount)) =>
                (m, count + thatCount)
              })
              .map({ case (_, (m, count)) =>
                s"""web_events,metric=${m.metric} value=$count,user_id="${m.user}" ${m.timestamp}"""
              })
              .mkString("\n")
            request = basicRequest
              .post(
                baseUri
                  .addPath("api", "v2", "write")
                  .addParams(
                    "org"       -> config.influx.org.value,
                    "bucket"    -> config.influx.bucket.value,
                    "precision" -> "ms"
                  )
              )
              .header("Accept", "application/json")
              .header("Authorization", s"Token ${config.influx.token.value}")
              .body(body)
            // TODO handle bad responses...
            response <- client.send(request)
            _        <- ZIO.debug(response)
          } yield ()

        private val `1h` = 1_000 * 60 * 60

        def queryMetrics(time: Instant): Task[QueryResult] =
          for {
            _ <- ZIO.logInfo(s"Querying influxdb for timestamp ${time.toString}")

            start = time.truncatedTo(ChronoUnit.HOURS)
            stop  = start.plus(1, ChronoUnit.HOURS)

            body =
              s"""
                 |data =
                 |  from(bucket: "${config.influx.bucket.value}")
                 |    |> range(start: ${start.toString}, stop: ${stop.toString})
                 |
                 |data
                 |  |> filter(fn: (r) => r["_measurement"] == "web_events")
                 |  |> filter(fn: (r) => r["_field"] == "value")
                 |  |> sum()
                 |  |> pivot(columnKey: ["metric"] , rowKey: ["_field"] , valueColumn: "_value")
                 |  |> drop(columns: ["_measurement", "_field", "_start", "_stop"])
                 |  |> yield(name: "counts")
                 |
                 |data
                 |  |> filter(fn: (r) => r["_measurement"] == "web_events")
                 |  |> filter(fn: (r) => r["_field"] == "user_id")
                 |  |> group()
                 |  |> unique()
                 |  |> count()
                 |  |> rename(columns: { _value: "count" })
                 |  |> drop(columns: ["_start", "_stop"])
                 |  |> yield(name: "unique users")
                 |""".stripMargin

            request = basicRequest
              .post(
                baseUri
                  .addPath("api", "v2", "query")
                  .addParams(
                    "org"       -> config.influx.org.value,
                    "bucket"    -> config.influx.bucket.value,
                    "precision" -> "ms"
                  )
              )
              .header("Content-Type", "application/vnd.flux")
              .header("Accept", "text/csv")
              .header("Authorization", s"Token ${config.influx.token.value}")
              .body(body)

            // TODO handle bad responses...
            response <- client.send(request)
            responseBodyOpt <- ZIO
              .from(response.body)
              .tapError(ZIO.logError(_))
              .mapBoth(new Throwable(_), content => Option(content.trim).filter(_.nonEmpty))
            _ <- ZIO.debug(responseBodyOpt)
            result <-
              ZIO
                .fromOption(responseBodyOpt)
                .foldZIO(
                  _ => ZIO.succeed(QueryResult.empty),
                  content =>
                    ZIO
                      .from(parseResponse(content))
                      .tapError(_ => ZIO.logError(s"Could not parse $content as a metrics response"))
                      .orElseFail(new Throwable(s"Could not parse $content as a metrics response"))
                )
          } yield result

        // That is very ugly. if I had more time I would have made/used a parser typeclass,
        // like zio-schema for example...
        private def parseResponse(body: String): Option[QueryResult] = {
          val lines      = body.split('\n').toList
          val splitLines = lines.map(_.split(',').map(_.trim).filter(_.nonEmpty).toList)
          for {
            usersCount <- splitLines
              .collectFirst({
                case metric :: _ :: count :: Nil if metric == "unique users" =>
                  count
              })
              .flatMap(_.toIntOption)
            (clicksString, impressionsStr) <- splitLines
              .collectFirst({
                case metric :: _ :: clicks :: impressions :: Nil if metric == "counts" =>
                  (clicks, impressions)
              })
            clicks      <- clicksString.toIntOption
            impressions <- impressionsStr.toIntOption
          } yield QueryResult(usersCount, clicks, impressions)
        }
      }
    }
}
