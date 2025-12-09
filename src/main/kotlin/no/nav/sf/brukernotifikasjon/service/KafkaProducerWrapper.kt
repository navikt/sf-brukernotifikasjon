package no.nav.sf.brukernotifikasjon.service

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaProducerWrapper<K, V>(
    private val topicName: String,
    private val kafkaProducer: KafkaProducer<K, V>,
) {
    fun sendEvent(
        key: K,
        event: V,
    ) {
        ProducerRecord(topicName, key, event).let { producerRecord ->
            try {
                kafkaProducer.send(producerRecord)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun flushAndClose() {
        kafkaProducer.flush()
        kafkaProducer.close()
    }
}
