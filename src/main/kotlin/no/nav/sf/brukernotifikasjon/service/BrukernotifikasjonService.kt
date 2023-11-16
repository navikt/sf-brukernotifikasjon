package no.nav.sf.brukernotifikasjon.service

import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.Eventtype
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.InnboksInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.sf.brukernotifikasjon.config.Environment
import no.nav.sf.brukernotifikasjon.config.KafkaConfig
import no.nav.sf.brukernotifikasjon.config.StringEnvVar.getEnvVar
import org.apache.kafka.clients.producer.KafkaProducer

class BrukernotifikasjonService {
    val environment = Environment()
    val kafkaProducerDone = KafkaProducerWrapper(
        getEnvVar("KAFKA_TOPIC_DONE"),
        KafkaProducer<NokkelInput, DoneInput>(
            KafkaConfig.producerProps(environment, Eventtype.DONE)
        )
    )
    val kafkaProducerInnboks = KafkaProducerWrapper(
        getEnvVar("KAFKA_TOPIC_INNBOKS"),
        KafkaProducer<NokkelInput, InnboksInput>(
            KafkaConfig.producerProps(environment, Eventtype.INNBOKS)
        )
    )

    fun sendInnboks(eventId: String, grupperingsId: String, fodselsnummer: String, innboks: InnboksInput) {
        kafkaProducerInnboks.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), innboks)
    }

    fun sendDone(eventId: String, grupperingsId: String, fodselsnummer: String, done: DoneInput) {
        kafkaProducerDone.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), done)
    }

    fun createKey(eventId: String, grupperingsId: String, fodselsnummer: String): NokkelInput {
        return NokkelInputBuilder()
            .withEventId(eventId)
            .withFodselsnummer(fodselsnummer)
            .withGrupperingsId(grupperingsId)
            .withNamespace(environment.namespace)
            .withAppnavn(environment.appnavn)
            .build()
    }
}
