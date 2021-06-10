package no.nav.sf.brukernotifikasjon

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.prometheus.client.Gauge
import mu.KotlinLogging
import no.nav.sf.library.AKafkaConsumer
import no.nav.sf.library.AnEnvironment
import no.nav.sf.library.KafkaConsumerStates
import no.nav.sf.library.KafkaMessage
import no.nav.sf.library.POSTFIX_LATEST
import no.nav.sf.library.SFsObjectRest
import no.nav.sf.library.SalesforceClient
import no.nav.sf.library.currentConsumerMessageHost
import no.nav.sf.library.encodeB64
import no.nav.sf.library.isSuccess
import no.nav.sf.library.kafkaConsumerOffsetRangeBoard
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig

private val log = KotlinLogging.logger {}

sealed class ExitReason {
    object NoSFClient : ExitReason()
    object NoKafkaClient : ExitReason()
    object NoEvents : ExitReason()
    object Work : ExitReason()
}

val kafkaSchemaReg = AnEnvironment.getEnvOrDefault("KAFKA_SCHEMA_REG", "http://localhost:8081")

data class WorkSettings(
    val kafkaConfig: Map<String, Any> = AKafkaConsumer.configBase + mapOf<String, Any>(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
            "schema.registry.url" to kafkaSchemaReg
    ) // ,
        // val sfClient: SalesforceClient = SalesforceClient()
)

// some work metrics
data class WMetrics(
    val noOfConsumedEvents: Gauge = Gauge
            .build()
            .name("kafka_consumed_event_gauge")
            .help("No. of consumed activity events from kafka since last work session")
            .register(),
    val noOfPostedEvents: Gauge = Gauge
            .build()
            .name("sf_posted_event_gauge")
            .help("No. of posted events to Salesforce since last work session")
            .register(),
    val noOfConsumedEventsOpprettet: Gauge = Gauge
            .build()
            .name("kafka_consumed_event_gauge_opprettet")
            .help("No. of consumed activity events from kafka since last work session")
            .register(),
    val noOfPostedEventsOpprettet: Gauge = Gauge
            .build()
            .name("sf_posted_event_gauge_opprettet")
            .help("No. of posted events to Salesforce since last work session")
            .register(),
    val producerIssues: Gauge = Gauge
            .build()
            .name("producer_issues")
            .help("producer issues")
            .register(),
    val consumerIssues: Gauge = Gauge
            .build()
            .name("consumer_issues")
            .help("consumer issues")
            .register()
) {
    fun clearAll() {
        noOfConsumedEvents.clear()
        noOfPostedEvents.clear()
        producerIssues.clear()
        consumerIssues.clear()
    }
}

val workMetrics = WMetrics()
val salesforceClient = SalesforceClient()

val investigateLog: MutableList<String> = mutableListOf()

val topicBeskjed = System.getenv("KAFKA_TOPIC_NYBESKJED")
val topicOppgave = System.getenv("KAFKA_TOPIC_NYOPPGAVE")
val topicDone = System.getenv("KAFKA_TOPIC_DONE")

var runOnce = false

var doneOnce = false

var msg = "Msg without Key:\n"
var msg2 = "\nMsg with Key:\n"
internal fun work(ws: WorkSettings): Pair<WorkSettings, ExitReason> {
    if (runOnce) {
        log.info { "Have run once already will wait.." }
        return Pair(ws, ExitReason.NoEvents)
    }
    runOnce = true

    var heartBeatConsumer = 0

    log.info { "bootstrap work session starting" }
    workMetrics.clearAll()

    var exitReason: ExitReason = ExitReason.NoSFClient

    exitReason = ExitReason.NoKafkaClient

    var cntBeskjed = 0
    var cntOppgave = 0
    var cntDone = 0

    var cntNullKey = 0
    var cntNullValue = 0
    var cntBigMessage = 0

    salesforceClient.enablesObjectPost { postActivities ->

        listOf(topicOppgave).forEach { topic ->

            log.info { "Setup sf-post connection for topic $topic" }

            val kafkaConsumer = AKafkaConsumer<GenericRecord?, GenericRecord?>(
                    config = ws.kafkaConfig,
                    fromBeginning = true,
                    topics = listOf(topic)
            )

            currentConsumerMessageHost = topic

            var sentFirst = false

            val resultOK = kafkaConsumer.consume { cRecords ->

                exitReason = ExitReason.NoEvents
                if (cRecords.isEmpty) return@consume KafkaConsumerStates.IsFinished
                if (cRecords.any { it.key() == null || it.value() == null }) {
                    log.warn { "Found nulls in batch in $topic" }
                }

                if (!doneOnce) {
                    log.info { "Example record: key: ${cRecords.first().key()} value: ${cRecords.first().value()}" }
                }
                doneOnce = true

                exitReason = ExitReason.Work
                workMetrics.noOfConsumedEvents.inc(cRecords.count().toDouble())
                // if (topic == topicOpprettet) workMetrics.noOfConsumedEventsOpprettet.inc(cRecords.count().toDouble())

                /*
                cRecords.filter { it.value()?.length ?: 0 > 100000 }.forEach {
                    log.info { "Encountered big record on $topic" }
                    cntBigMessage++
                    // investigateLog.add("Big record length ${it.value().length} as b64 length: ${it.value().encodeB64().length}")
                }

                if (cRecords.filter { it.value()?.length ?: 0 < 100000 }.count() == 0) {
                    log.info { "Only big msgs in batch, continue" }
                    return@consume KafkaConsumerStates.IsOk
                }

                 */

                cRecords.filter { it.key() == null }.forEach {
                    cntNullKey++
                }

                cRecords.filter { it.value() == null }.forEach {
                    cntNullValue++
                }
                when (topic) {
                    topicOppgave -> cntOppgave += cRecords.count()
                    topicBeskjed -> cntBeskjed += cRecords.count()
                    topicDone -> cntDone += cRecords.count()
                }

                if (heartBeatConsumer == 0) {
                    log.info { "Heartbeat consumer $topic - latest successful offset current run: ${kafkaConsumerOffsetRangeBoard[currentConsumerMessageHost + POSTFIX_LATEST]?.second ?: "Unknown"}" }
                }
                heartBeatConsumer = (heartBeatConsumer + 1) % 1000

                // if (!sentFirst) {
                //    sentFirst = true

                val body = SFsObjectRest(
                        records = cRecords.map {
                            KafkaMessage(
                                    topic = topic,
                                    key = it.key().toString().encodeB64(),
                                    value = it.value().toString().encodeB64()
                            )
                        }
                ).toJson()

                when (postActivities(body).isSuccess()) {
                    true -> {
                        log.info { "Only beskjed Successful post on topic $topic" }
                        workMetrics.noOfPostedEvents.inc(cRecords.count().toDouble())
                        // if (topic == topicOpprettet) workMetrics.noOfPostedEventsOpprettet.inc(cRecords.count().toDouble())
                        KafkaConsumerStates.IsOk // IsFinished // IsOk normally but now want to finished after first successful post
                    }
                    false -> {
                        log.error { "Failed posting to SF" }
                        workMetrics.producerIssues.inc()
                        KafkaConsumerStates.HasIssues
                    }
                }
                // } else {
                KafkaConsumerStates.IsOk
                // }
            }
            if (!resultOK) {
                log.error { "Kafka consumer reports failure" }
                workMetrics.consumerIssues.inc()
            }
        }
    }
    // File("/tmp/investigate").writeText(msg + msg2)
    log.info { "Work session finished - nullKey: $cntNullKey, nullValue: $cntNullValue, cntBigMessage: $cntBigMessage, cnt total: ${cntBeskjed + cntOppgave + cntDone}, beskjed: $cntBeskjed, oppgave: $cntOppgave, done: $cntDone - published ${workMetrics.noOfPostedEvents.get().toInt()} events" }

    return Pair(ws, exitReason)
}
