package no.nav.sf.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroSerializer
import java.time.LocalDateTime
import java.time.ZoneOffset
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Innboks
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.builders.DoneBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.sf.library.AKafkaProducer
import org.apache.kafka.clients.consumer.ConsumerConfig

fun produceKafkaEvent() {
    val kafkaProducerConfig: Map<String, Any> = AKafkaProducer.configBase + mapOf<String, Any>(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            "schema.registry.url" to kafkaSchemaReg
    )
}

fun createNokkel(): Nokkel {
    return NokkelBuilder()
            .withSystembruker("srv")
            .withEventId("An event Id")
            .build()
}
fun createDone(): Done {
    return DoneBuilder()
            .withFodselsnummer("fnr")
            .withGrupperingsId("Grupperingsid")
            .withTidspunkt(LocalDateTime.now())
            .build()
}

fun createInnboks(): Innboks {
    return Innboks(
            LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
            "fodselsnummer",
            "grupperingsId",
            "tekst",
            "link",
            4)
}
