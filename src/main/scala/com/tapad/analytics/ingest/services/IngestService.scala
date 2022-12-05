package com.tapad.analytics.ingest.services

import com.tapad.analytics.ingest.model.MetricEvent
import zio.macros.accessible
import zio.{ Dequeue, Queue, UIO, ULayer, ZIO, ZLayer }

@accessible
trait IngestService {
  def accept(event: MetricEvent): UIO[Unit]
}

object IngestService {

  // placeholder until we have a live implementation
  val noOp: ULayer[IngestService] =
    ZLayer.succeed(new IngestService {
      override def accept(event: MetricEvent): UIO[Unit] = ZIO.unit
    })

  sealed trait TestIngestService extends IngestService {
    val reportedEvents: Dequeue[MetricEvent]
  }

  val test: ULayer[TestIngestService] =
    ZLayer.fromZIO {
      Queue
        .unbounded[MetricEvent]
        .map(eventsQueue =>
          new TestIngestService {

            // the offer returns a boolean, indicating if the queue couldn't accept the element
            // because it was full. here, we are using an unbounded queue for testing, so it's ok
            // to assume that the offer always succeeds
            def accept(event: MetricEvent): UIO[Unit] = eventsQueue.offer(event).unit

            val reportedEvents: Dequeue[MetricEvent] = eventsQueue
          }
        )
    }
}
