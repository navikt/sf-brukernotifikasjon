package no.nav.sf.brukernotifikasjon

import com.google.gson.Gson
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.common.TextFormat
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.InnboksInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.InnboksInput
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

object Application {
    val gson = Gson()

    private val log = KotlinLogging.logger { }

    val brukernotifikasjonService = BrukernotifikasjonService()

    fun start() {
        log.info { "Starting" }
        naisAPIServer(NAIS_DEFAULT_PORT).start()
        // enableAPI {
        //    loop()
        // }
        log.info { "Finished!" }
    }

    private tailrec fun loop() {
        val stop = ShutdownHook.isActive() || PrestopHook.isActive()
        when {
            stop -> Unit
            !stop -> {
                // Currently no work sessions, just a dummy loop listening to hooks
                conditionalWait()
                loop()
            }
        }
    }

    private fun conditionalWait(ms: Long = bootstrapWaitTime) =
            runBlocking {

                log.debug { "Will wait $ms ms before starting all over" }

                val cr = launch {
                    runCatching { delay(ms) }
                            .onSuccess { log.debug { "waiting completed" } }
                            .onFailure { log.debug { "waiting interrupted" } }
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

data class DoneRequest(val eventId: String, val tidspunkt: String, val fodselsnummer: String, val grupperingsId: String)

data class InnboksRequest(
    val eventId: String,
    val eksternVarsling: Boolean = false,
    val link: String = "",
    val sikkerhetsnivaa: Int = 4,
    val tekst: String,
    val prefererteKanaler: String = "",
    val tidspunkt: String,
    val fodselsnummer: String,
    val grupperingsId: String,
    val epostVarslingstekst: String,
    val epostVarslingstittel: String,
    val smsVarslingstekst: String
)

fun naisAPI(): HttpHandler = routes(
        "/static" bind static(Classpath("/static")),
        "/innboks" bind Method.POST to {
            log.info { "innboks called with body ${it.bodyString()}" }
            if (containsValidToken(it)) { // TODO Skip validation for dev
                try {
                    val innboksRequest = Application.gson.fromJson(it.bodyString(), Array<InnboksRequest>::class.java)
                    val result: MutableList<InnboksInput> = mutableListOf()
                    innboksRequest.forEach {
                        val innboksbuilder = InnboksInputBuilder()
                            .withEksternVarsling(it.eksternVarsling)
                            .withSikkerhetsnivaa(it.sikkerhetsnivaa)
                            .withTekst(it.tekst)
                            .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(it.tidspunkt), ZoneOffset.UTC))
                            .withEpostVarslingstekst(it.epostVarslingstekst)
                            .withEpostVarslingstittel(it.epostVarslingstittel)
                            .withSmsVarslingstekst(it.smsVarslingstekst)
                        if (it.link.isNotEmpty()) {
                            innboksbuilder.withLink(URL(it.link))
                        }
                        if (it.prefererteKanaler.isNotEmpty()) {
                            innboksbuilder.withPrefererteKanaler(
                                *it.prefererteKanaler.split(",").map { PreferertKanal.valueOf(it) }
                                    .toTypedArray()
                            )
                        }
                        val innboks = innboksbuilder.build()
                        Application.brukernotifikasjonService.sendInnboks(
                            it.eventId,
                            it.grupperingsId,
                            it.fodselsnummer,
                            innboks
                        )
                        result.add(innboks)
                    }
                    Response(Status.OK).body("Published $result")
                } catch (e: Exception) {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    e.printStackTrace(pw)
                    val error = sw.toString()
                    log.error { error }
                    Response(Status.EXPECTATION_FAILED).body(error)
                }
            } else {
                log.info { "Sf-brukernotifikasjon api call denied - missing valid token" }
                Response(Status.UNAUTHORIZED)
            }
        },
        "/done" bind Method.POST to {
            // workMetrics.requestsDone.inc()
            log.info { "done called with body ${it.bodyString()}" }
            if (containsValidToken(it)) {
                try {
                    val doneRequest = Application.gson.fromJson(it.bodyString(), Array<DoneRequest>::class.java)
                    val result: MutableList<DoneInput> = mutableListOf()
                    doneRequest.forEach {
                        val done = DoneInputBuilder()
                            .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(it.tidspunkt), ZoneOffset.UTC))
                            .build()
                        Application.brukernotifikasjonService.sendDone(it.eventId, it.grupperingsId, it.fodselsnummer, done)
                        result.add(done)
                    }
                    Response(Status.OK).body("Published $result")
                } catch (e: Exception) {
                    log.error { e }
                    Response(Status.EXPECTATION_FAILED)
                }
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

fun enableAPI(port: Int = NAIS_DEFAULT_PORT, doSomething: () -> Unit): Boolean =
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
