package no.nav.sf.brukernotifikasjon

import mu.KotlinLogging
import no.nav.sf.library.AKafkaConsumer
import no.nav.sf.library.KafkaConsumerStates
import no.nav.sf.library.currentConsumerMessageHost
import no.nav.sf.library.encodeB64
import org.apache.avro.generic.GenericRecord

private val log = KotlinLogging.logger {}

fun investigate(ws: WorkSettings) {
    var heartBeatConsumer = 0

    log.info { "Investigate - session starting" }
    workMetrics.clearAll()

    var exitReason: ExitReason = ExitReason.NoSFClient

    exitReason = ExitReason.NoKafkaClient

    var cntBeskjed = 0
    var cntOppgave = 0
    var cntDone = 0

    var cntNullKey = 0
    var cntNullValue = 0
    var cntBigMessage = 0

    // var investigatedElements : MutableMap<String, Int> = mutableMapOf()

    var latestFoundPosition: MutableMap<String, Long> = mutableMapOf()
    var latestFoundPositionByKey: MutableMap<String, Long> = mutableMapOf()

    listOf(topicBeskjed, topicOppgave, topicDone).forEach { topic ->
        // investigatedElements[topic] = 0
        latestFoundPosition[topic] = 0L
        latestFoundPositionByKey[topic] = 0L
    }

    var refBeskjed = "eyJzeXN0ZW1icnVrZXIiOiAic3J2aG0tZGl0dG5hdiIsICJldmVudElkIjogIjZkYTE2Yjc4LTE1ZWUtNDViNC1iMzEwLTVjOGQ4Y2RjYmMwMCJ9"
    var refOppgave = "eyJzeXN0ZW1icnVrZXIiOiAic3J2cGFtYnJ1a2Vybm90IiwgImV2ZW50SWQiOiAiMDE5YjA2NDMtMzE5NC00ODFiLTg2OTctODIwNmU0ZTk0MzgwIn0="
    var refDone = "eyJzeXN0ZW1icnVrZXIiOiAic3J2c3lmb3Nva2JydWtlcm50ZiIsICJldmVudElkIjogIjc3OWU3OTIzLWYxMTQtNGE0OC05ZjE4LTQ2NWE0OTFkYjJjZiJ9"

    listOf(topicBeskjed, topicOppgave, topicDone).forEach { topic ->

        // log.info { "Setup sf-post connection for topic $topic" }
        log.info { "Investigate intermezzo report by val: $latestFoundPosition, by key: $latestFoundPositionByKey" }

        val kafkaConsumer = AKafkaConsumer<GenericRecord?, GenericRecord?>(
            config = ws.kafkaConfigAlt,
            fromBeginning = true,
            topics = listOf(topic)
        )

        currentConsumerMessageHost = topic + "-INV"

        kafkaConsumer.consume { cRecords ->

            exitReason = ExitReason.NoEvents
            if (cRecords.isEmpty) return@consume KafkaConsumerStates.IsFinished
            if (cRecords.any { it.key() == null || it.value() == null }) {
                log.warn { "Investigate - Found nulls in batch in $topic" }
            }

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
                topicOppgave -> {
                    cntOppgave += cRecords.count()
                    cRecords.filter { it.value().toString().encodeB64().equals(refOppgave) }.forEach {
                        log.info { "Investigate Found By Val Oppgave ref on post ${it.offset()}" }
                        latestFoundPosition[topicOppgave] = it.offset()
                    }
                    cRecords.filter { it.key().toString().encodeB64().equals(refOppgave) }.forEach {
                        log.info { "Investigate Found By Key Oppgave ref on post ${it.offset()}" }
                        latestFoundPositionByKey[topicOppgave] = it.offset()
                    }
                }
                topicBeskjed -> {
                    cntBeskjed += cRecords.count()
                    cRecords.filter { it.value().toString().encodeB64().equals(refBeskjed) }.forEach {
                        log.info { "Investigate Found By Val Beskjed ref on post ${it.offset()}" }
                        latestFoundPosition[topicBeskjed] = it.offset()
                    }
                    cRecords.filter { it.key().toString().encodeB64().equals(refBeskjed) }.forEach {
                        log.info { "Investigate Found By Key Beskjed ref on post ${it.offset()}" }
                        latestFoundPositionByKey[topicBeskjed] = it.offset()
                    }
                }
                topicDone -> {
                    cntDone += cRecords.count()
                    cRecords.filter { it.value().toString().encodeB64().equals(refDone) }.forEach {
                        log.info { "Investigate Found By Val Done ref on post ${it.offset()}" }
                        latestFoundPosition[topicDone] = it.offset()
                    }
                    cRecords.filter { it.key().toString().encodeB64().equals(refDone) }.forEach {
                        log.info { "Investigate Found By Key Done ref on post ${it.offset()}" }
                        latestFoundPositionByKey[topicDone] = it.offset()
                    }
                }
            }

            if (heartBeatConsumer == 0) {
                // log.info { "Heartbeat consumer $topic - latest successful offset current run: ${kafkaConsumerOffsetRangeBoard[currentConsumerMessageHost + POSTFIX_LATEST]?.second ?: "Unknown"}" }
            }
            heartBeatConsumer = (heartBeatConsumer + 1) % 1000

            // if (!sentFirst) {
            //    sentFirst = true

            /*
            val body = SFsObjectRest(
                records = cRecords.map {
                    KafkaMessage(
                        topic = topic,
                        key = it.key().toString().encodeB64(),
                        value = it.value().toString().encodeB64()
                    )
                }
            ).toJson()

             */

            KafkaConsumerStates.IsOk
/*
                when (postActivities(body).isSuccess()) {
                    true -> {
                        log.info { "(Latest beskjed oppgave. Load for Done) Successful post on topic $topic" }
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

 */
        } // end of consume
    } // end of per topic clause
    // File("/tmp/investigate").writeText(msg + msg2)
    log.info {
        "Investigate session finished - report by val: $latestFoundPosition, by key: $latestFoundPositionByKey - nullKey: $cntNullKey, nullValue: $cntNullValue, cntBigMessage: $cntBigMessage, cnt total: ${cntBeskjed + cntOppgave + cntDone}, beskjed: $cntBeskjed, oppgave: $cntOppgave, done: $cntDone "
    }
}
