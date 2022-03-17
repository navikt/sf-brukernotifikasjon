package no.nav.sf.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.netty.util.NetUtil
import java.net.InetSocketAddress
import java.net.URI
import java.util.Properties
import mu.KotlinLogging
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.Eventtype
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.InnboksInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.sf.brukernotifikasjon.config.Environment
import no.nav.sf.brukernotifikasjon.config.SecurityVars
import no.nav.sf.library.AKafkaProducer
import no.nav.sf.library.AnEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs

fun fetchEnv(env: String): String {
    return AnEnvironment.getEnvOrDefault(env, "$env missing")
}

const val EV_kafkaKeystorePath = "KAFKA_KEYSTORE_PATH"
const val EV_kafkaCredstorePassword = "KAFKA_CREDSTORE_PASSWORD"
const val EV_kafkaTruststorePath = "KAFKA_TRUSTSTORE_PATH"

class BrukernotifikasjonService {
    fun producerProps(env: Environment, type: Eventtype): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.aivenBrokers)
            put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, env.aivenSchemaRegistry)
            put(ProducerConfig.CLIENT_ID_CONFIG, type.name + NetUtil.getHostname(InetSocketAddress(0)))
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

    val schemaUsed = fetchEnv(kafkaSchemaRegistry)
    val kafkaProducerConfig: Map<String, Any> = AKafkaProducer.configBase + mapOf<String, Any>(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            "security.protocol" to "SSL",
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to fetchEnv(EV_kafkaKeystorePath),
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to fetchEnv(EV_kafkaCredstorePassword),
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to fetchEnv(EV_kafkaTruststorePath),
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to fetchEnv(EV_kafkaCredstorePassword),
            "schema.registry.url" to URI(schemaUsed)
    )

    /*
    val kafkaProducerGcp: Map<String, Any> = AKafkaProducer.configBase + mapOf<String, Any>(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
            "security.protocol" to "SSL",
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to fetchEnv(EV_kafkaKeystorePath),
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to fetchEnv(EV_kafkaCredstorePassword),
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to fetchEnv(EV_kafkaTruststorePath),
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to fetchEnv(EV_kafkaCredstorePassword)
     */

    // KafkaProducer<NokkelInput, BeskjedInput>(Kafka.producerProps(environment, Eventtype.BESKJED)
    // val kafkaProducerDone = KafkaProducerWrapper(System.getenv("KAFKA_TOPIC_DONE"), KafkaProducer<NokkelInput, DoneInput>(kafkaProducerConfig))
    val kafkaProducerDone = KafkaProducerWrapper(System.getenv("KAFKA_TOPIC_DONE"), KafkaProducer<NokkelInput, DoneInput>(kafkaProducerConfig))

    val kafkaProducerInnboks = KafkaProducerWrapper(System.getenv("KAFKA_TOPIC_INNBOKS"), KafkaProducer<NokkelInput, InnboksInput>(kafkaProducerConfig))

    private val appName = "sf-brukernotifikasjon"
    private val namespace = "teamnks"

    fun createKey(eventId: String, grupperingsId: String, fodselsnummer: String): NokkelInput {
        return NokkelInputBuilder()
            .withEventId(eventId)
            .withFodselsnummer(fodselsnummer)
            .withGrupperingsId(grupperingsId)
            .withNamespace(namespace)
            .withAppnavn(appName)
            .build()
    }

    fun sendInnboks(eventId: String, grupperingsId: String, fodselsnummer: String, innboks: InnboksInput) {
        kafkaProducerInnboks.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), innboks)
    }

    fun sendDone(eventId: String, grupperingsId: String, fodselsnummer: String, done: DoneInput) {
        kafkaProducerDone.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), done)
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
