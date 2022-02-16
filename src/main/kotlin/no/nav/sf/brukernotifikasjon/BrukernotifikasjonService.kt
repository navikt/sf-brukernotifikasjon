package no.nav.sf.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroSerializer
import mu.KotlinLogging
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Innboks
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.sf.library.AKafkaProducer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord

class BrukernotifikasjonService {
    val kafkaProducerConfig: Map<String, Any> = AKafkaProducer.configBase + mapOf<String, Any>(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            "schema.registry.url" to kafkaSchemaReg
    )

    val kafkaProducerDone = KafkaProducerWrapper(System.getenv("KAFKA_TOPIC_DONE"), KafkaProducer<Nokkel, Done>(kafkaProducerConfig))
    val kafkaProducerInnboks = KafkaProducerWrapper(System.getenv("KAFKA_TOPIC_INNBOKS"), KafkaProducer<Nokkel, Innboks>(kafkaProducerConfig))

    private val serviceuser = "srvsfnksbrknot"

    fun createKey(eventId: String): Nokkel {
        return NokkelBuilder().withSystembruker(serviceuser).withEventId(eventId).build()
    }

    fun sendInnboks(eventId: String, innboks: Innboks) {
        kafkaProducerInnboks.sendEvent(createKey(eventId), innboks)
    }

    fun sendDone(eventId: String, done: Done) {
        kafkaProducerDone.sendEvent(createKey(eventId), done)
    }
}

class KafkaProducerWrapper<K, V>(
    private val topicName: String,
    private val kafkaProducer: KafkaProducer<K, V>
) {

    private val log = KotlinLogging.logger { }

    fun sendEvent(key: K, event: V) {
        ProducerRecord(topicName, key, event).let { producerRecord ->
            try {
                kafkaProducer.send(producerRecord)
            } catch (e: Exception) {
                workMetrics.apiIssues.inc()
                throw e
            }
        }
    }

    fun flushAndClose() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            log.info("Produsent for kafka-eventer er flushet og lukket.")
        } catch (e: Exception) {
            workMetrics.apiIssues.inc()
            log.warn("Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert.")
        }
    }
}
