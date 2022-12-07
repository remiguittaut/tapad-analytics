package com.tapad.analytics.model

case class MetricEvent(
  timestamp: Long /* we keep epoch for perf reasons (improvement to be confirmed) */,
  user: String,
  metric: String
)
