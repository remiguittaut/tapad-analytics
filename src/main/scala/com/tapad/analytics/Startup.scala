package com.tapad.analytics

import com.tapad.analytics.http.AppServer
import zio._
import zio.http.ServerConfig
import zio.http.ServerConfig.LeakDetectionLevel
import zio.logging.{ LogFormat, consoleJson }

object Startup extends ZIOAppDefault {

  // TODO: Improve format
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(LogFormat.default)

  override def run: UIO[ExitCode] = {
    for {
      _ <- ZIO.log("Serving Api")
      _ <- AppServer.start
    } yield ()
  }.exitCode
    .provide(
      AppServer.live,
      ServerConfig.live(
        ServerConfig.default
          .port(8080)
          .maxThreads(8)
          .leakDetection(LeakDetectionLevel.PARANOID)
      ),
      MetricsBroker.inMem
    )
}
