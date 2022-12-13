package com.tapad.analytics.ingest

import com.tapad.analytics.model.MetricEvent
import com.tapad.analytics.storage.InfluxClient

import zio.macros.accessible
import zio.stream.ZSink
import zio.{ Chunk, Task, URLayer, ZIO, ZLayer }

// first version is only storing in cold storage

@accessible
trait IngestService {
  protected def handleBatch(events: Chunk[MetricEvent]): Task[Unit]

  val eventSink: ZSink[Any, Throwable, Chunk[MetricEvent], Nothing, Unit] =
    ZSink.foreach(handleBatch)
}

object IngestService {

  val live: URLayer[InfluxClient, IngestService] =
    ZLayer.fromZIO {
      ZIO
        .service[InfluxClient]
        .map(influxClient =>
          new IngestService {
            // There should be a fork between Influx and Redis here

            def handleBatch(events: Chunk[MetricEvent]): Task[Unit] = influxClient.pushMetrics(events)
          }
        )
    }
}
