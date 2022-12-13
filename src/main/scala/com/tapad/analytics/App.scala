package com.tapad.analytics

import com.tapad.analytics.http.AppServer
import com.tapad.analytics.ingest.IngestService
import com.tapad.analytics.query.QueryService
import com.tapad.analytics.storage.InfluxClient
import sttp.client3.httpclient.zio.HttpClientZioBackend

import zio._
import zio.stream.ZStream

object App {

  val boot: ZIO[Scope, Throwable, Unit] = {
    for {
      _ <- ZIO.log("Bootstraping the app")
      _ <- registerIngesters
      _ <- ZIO.log("ingesters started")
      _ <- ZIO.log("Serving Api")
      _ <- AppServer.start
    } yield ()
  }.provideSome[Scope](
    AppServer.live,
    MetricsBroker.inMem,
    IngestService.live,
    InfluxClient.live,
    AppConfig.live,
    QueryService.live,
    HttpClientZioBackend.layer()
  ).tapErrorCause(ZIO.logFatalCause(s"The app could not start or died.", _))

  private lazy val registerIngesters: URIO[Scope with MetricsBroker with IngestService, Unit] =
    for {
      _             <- ZIO.unit
      hub           <- ZIO.serviceWith[MetricsBroker](_.hub)
      consumerReady <- Promise.make[Nothing, Unit]
      eventStream = ZStream.unwrapScoped {
        ZStream.fromHubScoped(hub) <* consumerReady.succeed(())
      }
      subscriptionFiber <-
        eventStream
          .groupedWithin(4096, 100.millis)
          .tap(metrics => ZIO.debug(metrics))
          .tap(IngestService.handleBatch)
          .runDrain
          .fork
      _ <- consumerReady.await
      _ <- ZIO.addFinalizer(subscriptionFiber.interrupt)
    } yield ()

}
