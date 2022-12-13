package com.tapad.analytics

import com.tapad.analytics.model.MetricEvent

import zio._
import zio.macros.accessible

@accessible
trait MetricsBroker {
  def report(event: MetricEvent): UIO[Unit]
  def reportAll(batch: Chunk[MetricEvent]): UIO[Unit]
  val hub: Hub[MetricEvent]
}

object MetricsBroker {

  // first version without Kafka using a local stream. Not persistent, not multi-instances / scala-out capable,
  // just a pure bus. But it's enough to explore concepts and start ingesting.
  val inMem: ULayer[MetricsBroker] =
    ZLayer.scoped {
      for {
        innerHub <- Hub.bounded[MetricEvent](4096 /* better with power of 2 */ )
        _ <- ZIO.addFinalizer {
          innerHub.shutdown *> innerHub.awaitShutdown
        }
      } yield new MetricsBroker {

        // the publish returns a boolean, indicating if the hub couldn't accept the element
        // because it was full. here, we are using a bounded hub, so it's ok to discard
        // as a bounded hub won't return false but backpressure publishers.
        def report(event: MetricEvent): UIO[Unit] = innerHub.publish(event).unit

        def reportAll(batch: Chunk[MetricEvent]): UIO[Unit] = innerHub.publishAll(batch).unit

        val hub: Hub[MetricEvent] = innerHub
      }
    }
}
