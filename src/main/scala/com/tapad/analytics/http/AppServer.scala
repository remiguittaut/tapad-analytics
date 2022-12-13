package com.tapad.analytics.http

import com.tapad.analytics.query.QueryService
import com.tapad.analytics.{ AppConfig, MetricsBroker }

import zio.http.ServerConfig.LeakDetectionLevel
import zio.http.{ Server, ServerConfig }
import zio.macros.accessible
import zio.{ Task, URLayer, ZIO, ZLayer }

@accessible
trait AppServer {
  val start: Task[Nothing]
}

object AppServer {

  val live: URLayer[AppConfig with QueryService with MetricsBroker, AppServer] =
    ZLayer.fromZIO {
      for {
        config       <- ZIO.service[AppConfig]
        broker       <- ZIO.service[MetricsBroker]
        queryService <- ZIO.service[QueryService]
      } yield new AppServer {

        val start: Task[Nothing] =
          Server
            .serve(Endpoints.query ++ Endpoints.ingest)
            .provide(
              Server.live,
              ServerConfig.live(
                ServerConfig.default
                  .port(config.http.port.value)
                  .maxThreads(8)
                  .leakDetection(LeakDetectionLevel.PARANOID)
              ),
              ZLayer.succeed(broker),
              ZLayer.succeed(queryService)
            )
      }
    }
}
