package com.tapad.analytics

import com.tapad.analytics.model.MetricEvent
import com.tapad.analytics.specs.utils.TestData.genMetric

import zio._
import zio.stream.ZStream
import zio.test.{ Spec, TestClock, TestEnvironment, ZIOSpecDefault, assertTrue }

object MetricsBrokerSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = brokerSuite.provideLayer(MetricsBroker.inMem)

  val brokerSuite: Spec[MetricsBroker, Nothing] =
    suite("The metrics broker")(
      test(
        "should process the incoming single events and broadcast them on the hub"
      ) {

        // Version staying with epochs, intuitively more performant, however it seems to be comparable...
        // TODO: review later, java time might be (/ is) smarter than me and instants might be the same :)

        for {
          now <- Clock.ClockLive.instant.map(_.toEpochMilli)
          lastHAgoTs = now - (now % 3_600_000)
          today      = now - (now % (3_600_000 * 24))
          // Not exactly the definition of the exercise, supposed to be 95% matching the current ts,
          // but for testing, it's good enough to generate a distribution of 95% within the current hour
          // and 5% from today, before. ts are also not in order...
          outDatedMetrics <- genMetric(today, lastHAgoTs).runCollectN(50)
          metrics         <- genMetric(lastHAgoTs, now).runCollectN(950)
          all             <- Random.shuffle(outDatedMetrics ::: metrics)
          events          <- Ref.make(Chunk.empty[MetricEvent])
          broker          <- ZIO.service[MetricsBroker]
          consumerReady   <- Promise.make[Nothing, Unit]
          stream = ZStream.unwrapScoped {
            ZStream.fromHubScoped(broker.hub) <* consumerReady.succeed(())
          }
          subscriptionFiber <- stream
            .groupedWithin(256, 100.millis)
            .tap(chunk => events.update(_ ++ chunk))
            .timeout(200.millis)
            .runDrain
            .fork
          _        <- consumerReady.await
          _        <- ZIO.foreachParDiscard(all)(event => MetricsBroker.report(event))
          _        <- TestClock.adjust(1.second)
          _        <- subscriptionFiber.join
          reported <- events.get
        } yield assertTrue(all.forall(reported.contains)) && assertTrue(reported.size == 1000)
      },
      test(
        "should process the incoming event batches and broadcast them on the hub"
      ) {

        // Version staying with epochs, intuitively more performant, however it seems to be comparable...
        // TODO: review later, java time might be (/ is) smarter than me and instants might be the same :)

        for {
          now <- Clock.ClockLive.instant.map(_.toEpochMilli)
          lastHAgoTs = now - (now % 3_600_000)
          today      = now - (now % (3_600_000 * 24))
          // Not exactly the definition of the exercise, supposed to be 95% matching the current ts,
          // but for testing, it's good enough to generate a distribution of 95% within the current hour
          // and 5% from today, before. ts are also not in order...
          outDatedMetrics <- genMetric(today, lastHAgoTs).runCollectN(50)
          metrics         <- genMetric(lastHAgoTs, now).runCollectN(950)
          all             <- Random.shuffle(outDatedMetrics ::: metrics)
          events          <- Ref.make(Chunk.empty[MetricEvent])
          broker          <- ZIO.service[MetricsBroker]
          consumerReady   <- Promise.make[Nothing, Unit]
          stream = ZStream.unwrapScoped {
            ZStream.fromHubScoped(broker.hub) <* consumerReady.succeed(())
          }
          subscriptionFiber <- stream
            .groupedWithin(256, 100.millis)
            .tap(chunk => events.update(_ ++ chunk))
            .timeout(200.millis)
            .runDrain
            .fork
          _        <- consumerReady.await
          _        <- MetricsBroker.reportAll(Chunk.fromIterable(all))
          _        <- TestClock.adjust(1.second)
          _        <- subscriptionFiber.join
          reported <- events.get
        } yield assertTrue(all.forall(reported.contains)) && assertTrue(reported.size == 1000)
      }
    )
}
