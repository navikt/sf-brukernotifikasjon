package no.nav.sf.brukernotifikasjon

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import mu.KotlinLogging
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.StringWriter

object Metrics {
    private val log = KotlinLogging.logger { }

    // Gauge for counting API issues (e.g. unauthorized access, general errors)
    val apiIssues = registerGauge("api_issues")

    // Example: Add any future TMS-event related metrics here
    // val requestsVarsel = registerGauge("request_varsel")

    private val metricsAsText: String get() {
        val str = StringWriter()
        TextFormat.write004(str, CollectorRegistry.defaultRegistry.metricFamilySamples())
        return str.toString()
    }

    val metricsHandler = { _: Request ->
        try {
            val result = metricsAsText
            if (result.isEmpty()) {
                Response(Status.NO_CONTENT)
            } else {
                Response(Status.OK).body(result)
            }
        } catch (e: Exception) {
            log.error { "Failed writing metrics - ${e.message}" }
            Response(Status.INTERNAL_SERVER_ERROR)
        }
    }

    private fun registerGauge(name: String): Gauge {
        return Gauge.build().name(name).help(name).register()
    }

    init {
        DefaultExports.initialize()
        log.info { "Prometheus metrics are ready" }
    }
}