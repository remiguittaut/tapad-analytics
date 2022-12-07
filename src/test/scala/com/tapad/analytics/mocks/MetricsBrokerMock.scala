package com.tapad.analytics.mocks

import com.tapad.analytics.MetricsBroker
import com.tapad.analytics.model.MetricEvent
import zio.{ Chunk, Hub, Ref, UIO, ULayer, ZLayer }

trait MetricsBrokerMock extends MetricsBroker with Mock

object MetricsBrokerMock extends MockHelpers[MetricsBrokerMock] {

  case class Report(in: MetricEvent) extends Invocation[Report, MetricEvent]

  val compose: ULayer[MetricsBrokerMock] =
    ZLayer {
      for {
        innerHub <- Hub.unbounded[MetricEvent]
        ref      <- Ref.make(Chunk.empty[Invocation[_, _]])
      } yield new MetricsBrokerMock {

        override protected val invocationRef: Ref[Chunk[Invocation[_, _]]] =
          ref

        override def report(event: MetricEvent): UIO[Unit] = invocationRef.update(_ :+ Report(event))

        override val hub: Hub[MetricEvent] = innerHub

      }
    }
}
