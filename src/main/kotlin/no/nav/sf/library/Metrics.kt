package no.nav.sf.library

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.hotspot.DefaultExports
import mu.KotlinLogging

object Metrics {

    private val log = KotlinLogging.logger { }

    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    fun registerGauge(name: String): Gauge {
        return Gauge.build().name(name).help(name).register()
    }

    fun registerLabelGauge(name: String, label: String): Gauge {
        return Gauge.build().name(name).help(name).labelNames(label).register()
    }

    init {
        DefaultExports.initialize()
        log.info { "Prometheus metrics are ready" }
    }
}
