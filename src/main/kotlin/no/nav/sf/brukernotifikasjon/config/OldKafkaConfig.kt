package no.nav.sf.brukernotifikasjon.config

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.brukernotifikasjon.schemas.builders.domain.Eventtype
import no.nav.sf.brukernotifikasjon.env
import no.nav.sf.brukernotifikasjon.env_KAFKA_BROKERS
import no.nav.sf.brukernotifikasjon.env_KAFKA_CREDSTORE_PASSWORD
import no.nav.sf.brukernotifikasjon.env_KAFKA_KEYSTORE_PATH
import no.nav.sf.brukernotifikasjon.env_KAFKA_SCHEMA_REGISTRY
import no.nav.sf.brukernotifikasjon.env_KAFKA_SCHEMA_REGISTRY_PASSWORD
import no.nav.sf.brukernotifikasjon.env_KAFKA_SCHEMA_REGISTRY_USER
import no.nav.sf.brukernotifikasjon.env_KAFKA_TRUSTSTORE_PATH
import no.nav.sf.brukernotifikasjon.env_NAIS_APP_NAME
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.util.Properties

object OldKafkaConfig {
    fun producerProps(type: Eventtype): Properties =
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env(env_KAFKA_BROKERS))
            put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, env(env_KAFKA_SCHEMA_REGISTRY))
            put(ProducerConfig.CLIENT_ID_CONFIG, env(env_NAIS_APP_NAME) + type.name)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 40000)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(
                KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                "${env(env_KAFKA_SCHEMA_REGISTRY_USER)}:${env(env_KAFKA_SCHEMA_REGISTRY_PASSWORD)}",
            )
            put(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env(env_KAFKA_TRUSTSTORE_PATH))
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env(env_KAFKA_CREDSTORE_PASSWORD))
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env(env_KAFKA_KEYSTORE_PATH))
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env(env_KAFKA_CREDSTORE_PASSWORD))
            put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, env(env_KAFKA_CREDSTORE_PASSWORD))
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        }
}
