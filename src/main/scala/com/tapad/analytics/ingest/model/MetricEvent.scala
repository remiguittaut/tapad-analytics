package com.tapad.analytics.ingest.model

case class MetricEvent(timestamp: Long /* we keep epoch for perf reasons */, user: String, metric: String)
