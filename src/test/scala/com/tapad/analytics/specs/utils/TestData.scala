package com.tapad.analytics.specs.utils

import com.tapad.analytics.model.MetricEvent

import zio.test.Gen

object TestData {

  def genMetric(fromEpoch: Long, toEpoch: Long): Gen[Any, MetricEvent] =
    Gen
      .long(fromEpoch, toEpoch)
      .zip(Gen.stringN(12)(Gen.alphaChar))
      .zip(Gen.elements("click", "impression"))
      .map(MetricEvent.tupled)
}
