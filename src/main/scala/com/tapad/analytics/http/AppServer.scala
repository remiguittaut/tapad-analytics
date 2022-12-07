package com.tapad.analytics.http

import com.tapad.analytics.MetricsBroker
import zio.http.{ Server, ServerConfig }
import zio.macros.accessible
import zio.{ Task, URLayer, ZIO, ZLayer }

@accessible
trait AppServer {
  val start: Task[Nothing]
}

object AppServer {

  val live: URLayer[MetricsBroker with ServerConfig, AppServer] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.service[ServerConfig]
        broker <- ZIO.service[MetricsBroker]
      } yield new AppServer {

        val start: Task[Nothing] =
          Server
            .serve(Endpoints.query ++ Endpoints.ingest)
            .provide(
              Server.live,
              ServerConfig.live(config),
              ZLayer.succeed(broker)
            )
      }
    }
}
