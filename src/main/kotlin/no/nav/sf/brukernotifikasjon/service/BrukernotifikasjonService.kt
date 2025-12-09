package no.nav.sf.brukernotifikasjon.service

import com.google.gson.Gson
import mu.KotlinLogging
import no.nav.sf.brukernotifikasjon.InaktiverVarselRequest
import no.nav.sf.brukernotifikasjon.OpprettVarselRequest
import no.nav.sf.brukernotifikasjon.config.KafkaConfig
import no.nav.sf.brukernotifikasjon.config_CONTEXT
import no.nav.sf.brukernotifikasjon.config_TMS_VARSEL_TOPIC
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

class BrukernotifikasjonService(
    private val gson: Gson = Gson(),
    private val producer: KafkaProducer<String, String> = KafkaProducer(KafkaConfig.producerProps()),
    private val varselTopic: String = System.getenv(config_TMS_VARSEL_TOPIC),
) {
    private val log = KotlinLogging.logger {}
    private val devContext: Boolean = System.getenv(config_CONTEXT) == "DEV"

    val opprettVarselHandler: HttpHandler = { request ->
        try {
            log.info("Kall til Opprett Varsel mottatt")
            val varselRequest = gson.fromJson(request.bodyString(), OpprettVarselRequest::class.java)
            val key = varselRequest.varselId
            val value = gson.toJson(varselRequest)
            producer.send(ProducerRecord(varselTopic, key, value))
            if (devContext) {
                Response(Status.OK).body("TMS varsel sent with key $key: $value")
            } else {
                Response(Status.OK)
            }
        } catch (e: Exception) {
            log.error("Failed to produce TMS varsel, " + e.stackTraceToString())
            Response(Status.BAD_REQUEST)
        }
    }

    val inaktiverVarselHandler: HttpHandler = { request ->
        try {
            log.info("Kall til Inaktiver Varsel mottatt")
            val inaktiverRequest = gson.fromJson(request.bodyString(), InaktiverVarselRequest::class.java)
            val key = inaktiverRequest.varselId
            val value = gson.toJson(inaktiverRequest)
            producer.send(ProducerRecord(varselTopic, key, value))
            if (devContext) {
                Response(Status.OK).body("TMS inaktiver varsel sent with key $key: $value")
            } else {
                Response(Status.OK)
            }
        } catch (e: Exception) {
            log.error("Failed to produce TMS inaktiver varsel, " + e.stackTraceToString())
            Response(Status.BAD_REQUEST)
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(
            object : Thread() {
                override fun run() {
                    log.info("Shutting down. Flushing and closing Kafka producer.")
                    producer.flush()
                    producer.close()
                }
            },
        )
    }
}
