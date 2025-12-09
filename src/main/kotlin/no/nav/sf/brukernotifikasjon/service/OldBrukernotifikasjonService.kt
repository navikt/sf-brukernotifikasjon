package no.nav.sf.brukernotifikasjon.service

import com.google.gson.Gson
import mu.KotlinLogging
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.InnboksInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.Eventtype
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.InnboksInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.sf.brukernotifikasjon.DoneRequest
import no.nav.sf.brukernotifikasjon.InnboksRequest
import no.nav.sf.brukernotifikasjon.Metrics
import no.nav.sf.brukernotifikasjon.config.OldKafkaConfig
import no.nav.sf.brukernotifikasjon.config_CONTEXT
import no.nav.sf.brukernotifikasjon.config_KAFKA_TOPIC_DONE
import no.nav.sf.brukernotifikasjon.config_KAFKA_TOPIC_INNBOKS
import no.nav.sf.brukernotifikasjon.env
import no.nav.sf.brukernotifikasjon.env_NAIS_APP_NAME
import no.nav.sf.brukernotifikasjon.env_NAIS_NAMESPACE
import no.nav.sf.brukernotifikasjon.shuttingDown
import org.apache.kafka.clients.producer.KafkaProducer
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class OldBrukernotifikasjonService(
    private val naisNamespace: String = env(env_NAIS_NAMESPACE),
    private val naisAppName: String = env(env_NAIS_APP_NAME),
    private val kafkaProducerDone: KafkaProducerWrapper<NokkelInput, DoneInput> =
        KafkaProducerWrapper(
            env(config_KAFKA_TOPIC_DONE),
            KafkaProducer<NokkelInput, DoneInput>(
                OldKafkaConfig.producerProps(Eventtype.DONE),
            ),
        ),
    private val kafkaProducerInnboks: KafkaProducerWrapper<NokkelInput, InnboksInput> =
        KafkaProducerWrapper(
            env(config_KAFKA_TOPIC_INNBOKS),
            KafkaProducer<NokkelInput, InnboksInput>(
                OldKafkaConfig.producerProps(Eventtype.INNBOKS),
            ),
        ),
) {
    private val gson = Gson()

    private val log = KotlinLogging.logger { }

    private val devContext: Boolean = System.getenv(config_CONTEXT) == "DEV"

    val innboksHandler: HttpHandler = { request ->
        Metrics.requestsInnboks.inc()
        log.info { "innboks called${if (devContext) " with body ${request.bodyString()}" else ""}" }
        try {
            val innboksRequest = gson.fromJson(request.bodyString(), Array<InnboksRequest>::class.java)
            val result: MutableList<InnboksInput> = mutableListOf()
            innboksRequest.forEach {
                val innboksbuilder =
                    InnboksInputBuilder()
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
                        *it.prefererteKanaler
                            .split(",")
                            .map { PreferertKanal.valueOf(it) }
                            .toTypedArray(),
                    )
                }
                val innboks = innboksbuilder.build()
                sendInnboks(
                    it.eventId,
                    it.grupperingsId,
                    it.fodselsnummer,
                    innboks,
                )
                result.add(innboks)
            }
            log.info("Published ${result.count()} Innboks events")
            Response(Status.OK).body("Published ${result.count()} Innboks events ${if (devContext) result.toString() else ""}")
        } catch (e: Exception) {
            log.error { e.toString() }
            Metrics.apiIssues.inc()
            Response(Status.EXPECTATION_FAILED).body(e.toString())
        }
    }

    val doneHandler: HttpHandler = { request ->
        Metrics.requestsDone.inc()
        log.info { "done called${if (devContext) " with body ${request.bodyString()}" else ""}" }
        try {
            val doneRequest = gson.fromJson(request.bodyString(), Array<DoneRequest>::class.java)
            val result: MutableList<DoneInput> = mutableListOf()
            doneRequest.forEach {
                val done =
                    DoneInputBuilder()
                        .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(it.tidspunkt), ZoneOffset.UTC))
                        .build()
                sendDone(it.eventId, it.grupperingsId, it.fodselsnummer, done)
                result.add(done)
            }
            log.info("Published ${result.count()} Done events")
            Response(Status.OK).body("Published ${result.count()} Done events ${if (devContext) result.toString() else ""}")
        } catch (e: Exception) {
            Metrics.apiIssues.inc()
            log.error { e }
            Response(Status.EXPECTATION_FAILED)
        }
    }

    fun sendInnboks(
        eventId: String,
        grupperingsId: String,
        fodselsnummer: String,
        innboks: InnboksInput,
    ) = kafkaProducerInnboks.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), innboks)

    fun sendDone(
        eventId: String,
        grupperingsId: String,
        fodselsnummer: String,
        done: DoneInput,
    ) = kafkaProducerDone.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), done)

    fun createKey(
        eventId: String,
        grupperingsId: String,
        fodselsnummer: String,
    ): NokkelInput =
        NokkelInputBuilder()
            .withEventId(eventId)
            .withFodselsnummer(fodselsnummer)
            .withGrupperingsId(grupperingsId)
            .withNamespace(naisNamespace)
            .withAppnavn(naisAppName)
            .build()

    init {
        Runtime
            .getRuntime()
            .addShutdownHook(
                object : Thread() {
                    private val log = KotlinLogging.logger { }

                    override fun run() {
                        log.info { "Trigger shutdown hook" }
                        shuttingDown = true
                        sleep(25000) // Sleep for 25 seconds (readiness periodSeconds x failureThreshold)
                        log.info { "Flush and close producers" }
                        kafkaProducerInnboks.flushAndClose()
                        kafkaProducerDone.flushAndClose()
                    }
                },
            )
    }
}
