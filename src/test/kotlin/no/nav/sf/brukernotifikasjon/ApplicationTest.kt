package no.nav.sf.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.InnboksInput
import no.nav.sf.brukernotifikasjon.Application.api
import no.nav.sf.brukernotifikasjon.config.SystemWrapperInterface
import no.nav.sf.brukernotifikasjon.config.systemWrapperDelegate
import org.http4k.core.MemoryBody
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.EXPECTATION_FAILED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApplicationTest {

    private lateinit var response: Response
    private lateinit var kafkaEventId: String
    private lateinit var kafkakaInnboksInput: InnboksInput
    private lateinit var kafkaDoneInput: DoneInput

    @BeforeEach
    fun setup() {
        systemWrapperDelegate = object : SystemWrapperInterface {
            override fun getEnvVar(varName: String) = "broker.com:26484"
        }
    }

    @Test
    fun `GET isReady isAlive stop and metrics should all answer OK`() {
        response = api().invoke(Request(GET, "/isReady"))
        assertTrue(`that`(response).`is`(OK))

        response = api().invoke(Request(GET, "/isAlive"))
        assertTrue(`that`(response).`is`(OK))

        response = api().invoke(Request(GET, "/stop"))
        assertTrue(`that`(response).`is`(OK))

        response = api().invoke(Request(GET, "/metrics"))
        assertTrue(`that`(response).`is`(OK))
    }

    @Test
    fun `POST innboks with valid token should answer OK`() {
        // arrange
        val payload = innboksRequest()
        // act
        response =
            api(valid = true)
                .invoke(Request(POST, "/innboks").body(payload))
        // assert
        assertTrue(`that`(response).`is`(OK))
    }

    @Test
    fun `POST innboks with invalid token should answer UNAUTHORIZED`() {
        // arrange
        val payload = innboksRequest()
        // act
        response =
            api(valid = false)
                .invoke(Request(POST, "/innboks").body(payload))
        // assert
        assertTrue(`that`(response).`is`(UNAUTHORIZED))
    }

    @Test
    fun `POST innboks with invalid payload should answer EXPECTATION_FAILED`() {
        // arrange
        val payload = innboksRequest(link = "malformed_link")
        // act
        response =
            api()
                .invoke(Request(POST, "/innboks").body(payload))
        // assert
        assertTrue(`that`(response).`is`(EXPECTATION_FAILED))
    }

    @Test
    fun `POST innboks with payload should produce eventId value on Kafka`() {
        // arrange
        val myEventId = "461a85ac-2f3f-494a-a37c-2cbee6dcf640"
        val payload = innboksRequest(myEventId)
        // act
        response =
            api()
                .invoke(Request(POST, "/innboks").body(payload))
        // assert
        assertTrue(`that`(kafkaEventId).`is`(myEventId))
    }

    @Test
    fun `POST innboks with payload should produce InnboksInput value on Kafka`() {
        // arrange
        val myTekst = "myTekst"
        val payload = innboksRequest(tekst = myTekst)
        // act
        response =
            api()
                .invoke(Request(POST, "/innboks").body(payload))
        // assert
        assertTrue(`that`(kafkakaInnboksInput.getTekst()).`is`(myTekst))
    }

    @Test
    fun `POST innboks should handle link`() {
        // arrange
        val myLink = "http://my.link"
        val payload = innboksRequest(link = myLink)
        // act
        response =
            api()
                .invoke(Request(POST, "/innboks").body(payload))
        // assert
        assertTrue(`that`(kafkakaInnboksInput.getLink()).`is`(myLink))
    }

    @Test
    fun `POST innboks should handle prefererteKanaler`() {
        // arrange
        val myChannel = "EPOST"
        val payload = innboksRequest(channels = myChannel)
        // act
        response =
            api()
                .invoke(Request(POST, "/innboks").body(payload))
        // assert
        assertTrue(`that`(kafkakaInnboksInput.getPrefererteKanaler().first()).`is`(myChannel))
    }

    @Test
    fun `POST done with invalid token should answer UNAUTHORIZED`() {
        // act
        response =
            api(valid = false)
                .invoke(Request(POST, "/done").body(doneRequest()))
        // assert
        assertTrue(`that`(response).`is`(UNAUTHORIZED))
    }

    @Test
    fun `POST done with valid token should answer OK`() {
        // act
        response =
            api(valid = true)
                .invoke(Request(POST, "/done").body(doneRequest()))
        // assert
        assertTrue(`that`(response).`is`(OK))
    }

    @Test
    fun `POST done with invalid payload should answer EXPECTATION_FAILED`() {
        // arrange
        val payload = doneRequest(tidspunkt = "invalid_tidspunkt")
        // act
        response =
            api(valid = true)
                .invoke(Request(POST, "/done").body(payload))
        // assert
        assertTrue(`that`(response).`is`(EXPECTATION_FAILED))
    }

    @Test
    fun `POST done with payload should produce eventId value on Kafka`() {
        // arrange
        val myEventId = "461a85ac-2f3f-494a-a37c-2cbee6dcf640"
        val payload = doneRequest(eventId = myEventId)
        // act
        response =
            api(valid = true)
                .invoke(Request(POST, "/done").body(payload))
        // assert
        assertTrue(`that`(kafkaEventId).`is`(myEventId))
    }

    @Test
    fun `POST done with payload should produce DoneInput value on Kafka`() {
        // arrange
        val myTime = "1970-01-01T00:00:00Z"
        val payload = doneRequest(tidspunkt = myTime)
        // act
        response =
            api(valid = true)
                .invoke(Request(POST, "/done").body(payload))
        // assert
        assertTrue(`that`(kafkaDoneInput.toString()).`is`("""{"tidspunkt": 0}"""))
    }

    private fun api(valid: Boolean = true) = api(isValid(valid), sendInnboks(), sendDone())
    private fun isValid(valid: Boolean) = { _: Request -> valid }

    private fun sendInnboks() =
        { eventId: String, _: String, _: String, innboks: InnboksInput ->
            this.kafkaEventId = eventId
            this.kafkakaInnboksInput = innboks
        }

    private fun sendDone() =
        { eventId: String, _: String, _: String, done: DoneInput ->
            this.kafkaEventId = eventId
            this.kafkaDoneInput = done
        }

    private fun innboksRequest(
        eventId: String = "b6303e3a-83b4-11ee-b962-0242ac120002",
        tekst: String = "tekst",
        link: String = "http://li.nk",
        channels: String = "SMS"
    ) =
        MemoryBody(
            """[
                  {
                     "eventId":"$eventId",
                     "eksternVarsling":true,
                     "link":"$link",
                     "sikkerhetsnivaa":4,
                     "tekst":"$tekst",
                     "prefererteKanaler":"$channels",
                     "tidspunkt":"2020-01-01T10:00:00Z",
                     "fodselsnummer":"22067612345",
                     "grupperingsId":"id",
                     "epostVarslingstekst":"epostVarslingstekst",
                     "epostVarslingstittel":"epostVarslingstittel",
                     "smsVarslingstekst":"smsVarslingstekst"
                  }
                ]
            """.trimIndent()
        )

    private fun doneRequest(
        eventId: String = "b6303e3a-83b4-11ee-b962-0242ac120002",
        tidspunkt: String = "2020-01-01T10:00:00Z"
    ) =
        MemoryBody(
            """[
                  {
                     "eventId":"$eventId",
                     "tidspunkt":"$tidspunkt",
                     "fodselsnummer":"22067612345",
                     "grupperingsId":"id"
                  }
                ]
            """.trimIndent()
        )
}
