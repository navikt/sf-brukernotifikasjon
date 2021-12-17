package no.nav.sf.brukernotifikasjon

import com.google.gson.Gson
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.common.TextFormat
import java.io.StringWriter
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.brukernotifikasjon.schemas.builders.DoneBuilder
import no.nav.brukernotifikasjon.schemas.builders.InnboksBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.sf.brukernotifikasjon.token.containsValidToken
import no.nav.sf.library.AnEnvironment
import no.nav.sf.library.Metrics
import no.nav.sf.library.PrestopHook
import no.nav.sf.library.ShutdownHook
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.ResourceLoader.Companion.Classpath
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer

private const val EV_bootstrapWaitTime = "MS_BETWEEN_WORK" // default to 10 minutes
private val bootstrapWaitTime = AnEnvironment.getEnvOrDefault(EV_bootstrapWaitTime, "60000").toLong()

private val log = KotlinLogging.logger { }

object Bootstrap {

    private val log = KotlinLogging.logger { }

    val brukernotifikasjonService = BrukernotifikasjonService()

    val gson = Gson()

    fun start(ws: WorkSettings = WorkSettings()) {
        log.info { "Starting" }
        enableNAISAPIModified {
            // investigate(ws)
            loop(ws)
        }
        log.info { "Finished!" }
    }

    private tailrec fun loop(ws: WorkSettings) {
        val stop = ShutdownHook.isActive() || PrestopHook.isActive()
        when {
            stop -> Unit
            !stop -> loop(work(ws)
                    .let { prevWS ->
                        prevWS.first
                    }
                    .also { conditionalWait() }
            )
        }
    }

    private fun conditionalWait(ms: Long = bootstrapWaitTime) =
            runBlocking {

                log.info { "Will wait $ms ms before starting all over" }

                val cr = launch {
                    runCatching { delay(ms) }
                            .onSuccess { log.info { "waiting completed" } }
                            .onFailure { log.info { "waiting interrupted" } }
                }

                tailrec suspend fun loop(): Unit = when {
                    cr.isCompleted -> Unit
                    ShutdownHook.isActive() || PrestopHook.isActive() -> cr.cancel()
                    else -> {
                        delay(250L)
                        loop()
                    }
                }

                loop()
                cr.join()
            }
}

// From NaisDSL.kt:

const val NAIS_URL = "http://localhost:"
const val NAIS_DEFAULT_PORT = 8080

const val NAIS_ISALIVE = "/isAlive"
const val NAIS_ISREADY = "/isReady"
const val NAIS_METRICS = "/metrics"
const val NAIS_PRESTOP = "/stop"

internal val preStopHook: Gauge = Gauge
        .build()
        .name("pre_stop__hook_gauge")
        .help("No. of preStopHook activations since last restart")
        .register()

private fun String.responseByContent(): Response =
        if (this.isNotEmpty()) Response(Status.OK).body(this) else Response(Status.NO_CONTENT)

data class DoneRequest(val tidspunkt: String, val fodselsnummer: String, val grupperingsId: String)

data class InnboksRequest(
    val eksternVarsling: Boolean = false,
    val link: String = "",
    val sikkerhetsnivaa: Int = 4,
    val tekst: String,
    val prefererteKanaler: String = "",
    val tidspunkt: String,
    val fodselsnummer: String,
    val grupperingsId: String
)

fun naisAPI(): HttpHandler = routes(
        "/static" bind static(Classpath("/static")),
        "/innboks" bind Method.POST to {
            log.info { "innboks called with body ${it.bodyString()}, queries eventId: ${it.queries("eventId")}" }
            if (true /*containsValidToken(it)*/) { // TODO Skip validation for dev
                val eventId = it.queries("eventId").first()!!
                val innboksRequest = Bootstrap.gson.fromJson(it.bodyString(), InnboksRequest::class.java)
                val innboksbuilder = InnboksBuilder()
                        .withEksternVarsling(innboksRequest.eksternVarsling)
                        .withSikkerhetsnivaa(innboksRequest.sikkerhetsnivaa)
                        .withTekst(innboksRequest.tekst)
                        .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(innboksRequest.tidspunkt), ZoneOffset.UTC))
                        .withFodselsnummer(innboksRequest.fodselsnummer)
                        .withGrupperingsId(innboksRequest.grupperingsId)

                if (innboksRequest.link.isNotEmpty()) {
                    innboksbuilder.withLink(URL(innboksRequest.link))
                }
                if (innboksRequest.prefererteKanaler.isNotEmpty()) {
                    innboksbuilder.withPrefererteKanaler(*innboksRequest.prefererteKanaler.split(",").map { PreferertKanal.valueOf(it) }.toTypedArray())
                }
                val innboks = innboksbuilder.build()
                Bootstrap.brukernotifikasjonService.sendInnboks(eventId, innboks)
                Response(Status.OK).body("Published $innboks")
            } else {
                log.info { "Sf-brukernotifikasjon api call denied - missing valid token" }
                Response(Status.UNAUTHORIZED)
            }
        },
        "/done" bind Method.POST to {
            log.info { "done called with body ${it.bodyString()},  queries eventId: ${it.queries("eventId")}" }
            if (containsValidToken(it)) {
                val eventId = it.queries("eventId").first()!!
                val doneRequest = Bootstrap.gson.fromJson(it.bodyString(), DoneRequest::class.java)

                val done = DoneBuilder()
                        .withFodselsnummer(doneRequest.fodselsnummer)
                        .withGrupperingsId(doneRequest.grupperingsId)
                        .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(doneRequest.tidspunkt), ZoneOffset.UTC))
                        .build()

                Bootstrap.brukernotifikasjonService.sendDone(eventId, done)
                Response(Status.OK).body("Published $done")
            } else {
                log.info { "Sf-brukernotifikasjon api call denied - missing valid token" }
                Response(Status.UNAUTHORIZED)
            }
        },
        NAIS_ISALIVE bind Method.GET to { Response(Status.OK) },
        NAIS_ISREADY bind Method.GET to { Response(Status.OK) },
        NAIS_METRICS bind Method.GET to {
            runCatching {
                StringWriter().let { str ->
                    TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                    str
                }.toString()
            }
                    .onFailure {
                        log.error { "/prometheus failed writing metrics - ${it.localizedMessage}" }
                    }
                    .getOrDefault("")
                    .responseByContent()
        },
        NAIS_PRESTOP bind Method.GET to {
            preStopHook.inc()
            PrestopHook.activate()
            log.info { "Received PreStopHook from NAIS" }
            Response(Status.OK)
        }
)

fun naisAPIServer(port: Int): Http4kServer = naisAPI().asServer(Netty(port))

fun enableNAISAPIModified(port: Int = NAIS_DEFAULT_PORT, doSomething: () -> Unit): Boolean =
        naisAPIServer(port).let { srv ->
            try {
                srv.start().use {
                    log.info { "NAIS DSL is up and running at port $port" }
                    runCatching(doSomething)
                            .onFailure {
                                log.error { "Failure during doSomething in enableNAISAPI - ${it.localizedMessage}" }
                            }
                }
                true
            } catch (e: Exception) {
                log.error { "Failure during enable/disable NAIS api for port $port - ${e.localizedMessage}" }
                false
            } finally {
                srv.close()
                log.info { "NAIS DSL is stopped at port $port" }
            }
        }
