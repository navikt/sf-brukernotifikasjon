package no.nav.sf.brukernotifikasjon

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.common.TextFormat
import java.io.StringWriter
import java.lang.reflect.Type
import java.net.URL
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Innboks
import no.nav.brukernotifikasjon.schemas.builders.DoneBuilder
import no.nav.brukernotifikasjon.schemas.builders.InnboksBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.sf.library.AnEnvironment
import no.nav.sf.library.Metrics
import no.nav.sf.library.PrestopHook
import no.nav.sf.library.ShutdownHook
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson.auto
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

    fun start(ws: WorkSettings = WorkSettings()) {
        log.info { "Starting" }
        enableNAISAPIModified { loop(ws) }
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
const val SEND = "/send"

internal val preStopHook: Gauge = Gauge
        .build()
        .name("pre_stop__hook_gauge")
        .help("No. of preStopHook activations since last restart")
        .register()

private fun String.responseByContent(): Response =
        if (this.isNotEmpty()) Response(Status.OK).body(this) else Response(Status.NO_CONTENT)

val brukernotifikasjonService = BrukernotifikasjonService()

val innboksLens = Body.auto<Innboks>().toLens()

val doneLens = Body.auto<Done>().toLens()

// val gson: Gson = GsonBuilder().registerTypeAdapter(Date::class.java, GsonUTCDateAdapter()).create()

val gson = Gson()
/*
{
  "eksternVarsling": true,
  "link": "string",
  "sikkerhetsnivaa": 4,
  "tekst": "string",
  "prefererteKanaler": "SMS,EPOST",
  "tidspunkt": "2021-06-27T12:00:00.000Z",
  "fodselsnummer": "string",
  "grupperingsId": "string"
}
 */
data class DoneRequest(val tidspunkt: String, val fodselsnummer: String, val grupperingsId: String)

data class InnboksRequest(
    val eksternVarsling: Boolean,
    val link: String,
    val sikkerhetsnivaa: Int,
    val tekst: String,
    val prefererteKanaler: String,
    val tidspunkt: String,
    val fodselsnummer: String,
    val grupperingsId: String
)

fun LocalDateTime?.toIsoDateTimeString(): String {
    return this?.format(DateTimeFormatter.ISO_DATE_TIME) ?: ""
}

fun Instant?.toIsoInstantString(): String {
    return DateTimeFormatter.ISO_INSTANT.format(this) ?: ""
}

fun naisAPI(): HttpHandler = routes(
        "/index.html" bind static(Classpath("/static/index.html")),
        "/static" bind static(Classpath("/static")),
        "/swagger" bind Method.GET to {
            val swaggerfile = Bootstrap.javaClass.classLoader.getResource("swagger.yml").readText()
            Response(Status.OK).body(swaggerfile)
        },
        "/innboks" bind Method.POST to {
            log.info { "innboks called with body ${it.bodyString()}, queries eventId: ${it.queries("eventId")}" }
            val innboks = gson.fromJson(it.bodyString(), InnboksRequest::class.java)

            val finalInnboks = InnboksBuilder()
                    .withEksternVarsling(innboks.eksternVarsling)
                    .withLink(URL(innboks.link))
                    .withSikkerhetsnivaa(innboks.sikkerhetsnivaa)
                    .withTekst(innboks.tekst)
                    .withPrefererteKanaler(*innboks.prefererteKanaler.split(",").map { PreferertKanal.valueOf(it) }.toTypedArray())
                    .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(innboks.tidspunkt), ZoneOffset.UTC))
                    .withFodselsnummer(innboks.fodselsnummer)
                    .withGrupperingsId(innboks.grupperingsId)
                    .build()

            brukernotifikasjonService.sendInnboks()
            val ldt = LocalDateTime.ofInstant(Instant.parse(innboks.tidspunkt), ZoneOffset.UTC)

            val backToInstant = ldt.toInstant(ZoneOffset.UTC)
            Response(Status.OK).body(innboks.toString() + " time instant ${Instant.parse(innboks.tidspunkt).toIsoInstantString()}, back to instant ${backToInstant.toIsoInstantString()}, time date time ${ldt.toIsoDateTimeString()}, finalDone $finalInnboks")
        },
        "/done" bind Method.POST to {

            log.info { "done called with body ${it.bodyString()}, queries eventId: ${it.queries("eventId")}" }
            val done = gson.fromJson(it.bodyString(), DoneRequest::class.java)

            val finalDone = DoneBuilder()
                    .withFodselsnummer(done.fodselsnummer)
                    .withGrupperingsId(done.grupperingsId)
                    .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(done.tidspunkt), ZoneOffset.UTC))
                    .build()

            brukernotifikasjonService.sendDone()
            val ldt = LocalDateTime.ofInstant(Instant.parse(done.tidspunkt), ZoneOffset.UTC)

            val backToInstant = ldt.toInstant(ZoneOffset.UTC)
            Response(Status.OK).body(done.toString() + " time instant ${Instant.parse(done.tidspunkt).toIsoInstantString()}, back to instant ${backToInstant.toIsoInstantString()}, time date time ${ldt.toIsoDateTimeString()}, finalDone $finalDone")
        },
        SEND bind Method.POST to {
            // if (containsValidToken(call.request)) {
            log.info { "Pretend authorized call to sf-brukernotifikasjon" }
            log.info("body in ${it.body} - ${it.bodyString()}")
            // call.respond(HttpStatusCode.Created, addArchive(requestBody))
            // } else {
            //    log.info { "Arkiv call denied - missing valid token" }
            //    call.respond(HttpStatusCode.Unauthorized)
            // }
            var response = Response(Status.OK).body("body body")
            response
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

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"
const val claim_NAME = "name"
/*
val multiIssuerConfiguration = MultiIssuerConfiguration(
        mapOf(
                "azure" to IssuerProperties(
                        URL(Environment.getEnvOrDefault(env_AZURE_APP_WELL_KNOWN_URL, "http://")),
                        listOf(Environment.getEnvOrDefault(env_AZURE_APP_CLIENT_ID, ""))
                )
        )
)

private val jwtTokenValidationHandler = JwtTokenValidationHandler(multiIssuerConfiguration)

fun containsValidToken(request: ApplicationRequest): Boolean {
    val firstValidToken = jwtTokenValidationHandler.getValidatedTokens(fromApplicationRequest(request)).firstValidToken
    return firstValidToken.isPresent
}
*/

// this class can't be static
class GsonUTCDateAdapter : JsonSerializer<Date>, JsonDeserializer<Date> {
    private val dateFormat: DateFormat
    @Synchronized
    override fun serialize(date: Date, type: Type, jsonSerializationContext: JsonSerializationContext): JsonElement {
        return JsonPrimitive(dateFormat.format(date))
    }

    @Synchronized
    override fun deserialize(jsonElement: JsonElement, type: Type, jsonDeserializationContext: JsonDeserializationContext): Date {
        return try {
            dateFormat.parse(jsonElement.getAsString())
        } catch (e: ParseException) {
            throw JsonParseException(e)
        }
    }

    init {
        dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.UK) // This is the format I need
        dateFormat.timeZone = TimeZone.getTimeZone("UTC") // This is the key line which converts the date to UTC which cannot be accessed with the default serializer
    }
}
