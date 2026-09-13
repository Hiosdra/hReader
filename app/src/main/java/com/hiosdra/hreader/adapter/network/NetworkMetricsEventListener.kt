package com.hiosdra.hreader.adapter.network

import com.hiosdra.hreader.core.application.observability.NetworkMetricsCollector
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response

class NetworkMetricsEventListener(
    private val collector: NetworkMetricsCollector
) : EventListener() {
    private var request: NetworkMetricsCollector.RequestToken? = null

    override fun callStart(call: Call) {
        request = collector.requestStarted()
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        request?.let { collector.responseHeadersReceived(it, response.code) }
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        request?.let { collector.responseBodyReceived(it, byteCount) }
    }

    override fun callFailed(call: Call, ioe: java.io.IOException) {
        request?.let(collector::requestFailed)
        request = null
    }

    override fun callEnd(call: Call) {
        request?.let(collector::requestFinished)
        request = null
    }

    class Factory(private val collector: NetworkMetricsCollector) : EventListener.Factory {
        override fun create(call: Call): EventListener = NetworkMetricsEventListener(collector)
    }
}
