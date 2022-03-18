package no.nav.sf.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import java.util.Properties
import no.nav.brukernotifikasjon.schemas.builders.domain.Eventtype
import no.nav.sf.brukernotifikasjon.config.Environment
import no.nav.sf.brukernotifikasjon.config.SecurityVars
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs

object Kafka {
    fun producerProps(env: Environment, type: Eventtype): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.aivenBrokers)
            put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, env.aivenSchemaRegistry)
            put(ProducerConfig.CLIENT_ID_CONFIG, env.appnavn + type.name)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 40000)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            if (env.securityConfig.enabled) {
                putAll(credentialPropsAiven(env.securityConfig.variables!!))
            }
        }
    }

    private fun credentialPropsAiven(securityVars: SecurityVars): Properties {
        return Properties().apply {
            put(KafkaAvroSerializerConfig.USER_INFO_CONFIG, "${securityVars.aivenSchemaRegistryUser}:${securityVars.aivenSchemaRegistryPassword}")
            put(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, securityVars.aivenTruststorePath)
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, securityVars.aivenKeystorePath)
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        }
    }

    /*
    val kafkaProducerConfig: Map<String, Any> = AKafkaProducer.configBase + mapOf<String, Any>(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
        "security.protocol" to "SSL",
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to fetchEnv(EV_kafkaKeystorePath),
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to fetchEnv(EV_kafkaCredstorePassword),
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to fetchEnv(EV_kafkaTruststorePath),
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to fetchEnv(EV_kafkaCredstorePassword),
        "schema.registry.url" to fetchEnv(kafkaSchemaRegistry)
    )
     */
}
