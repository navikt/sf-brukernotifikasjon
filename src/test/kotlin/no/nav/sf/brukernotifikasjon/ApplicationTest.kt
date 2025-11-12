package no.nav.sf.brukernotifikasjon

import io.mockk.every
import io.mockk.mockk
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.InnboksInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.sf.brukernotifikasjon.service.BrukernotifikasjonService
import no.nav.sf.brukernotifikasjon.service.KafkaProducerWrapper
import no.nav.sf.brukernotifikasjon.service.OldBrukernotifikasjonService
import no.nav.sf.brukernotifikasjon.token.TokenValidator
import org.junit.jupiter.api.BeforeEach

class ApplicationTest {
    private val mockTokenValidator = mockk<TokenValidator>()
    private val mockToken = mockk<JwtToken>()

    private val mockKafkaProducerDone = mockk<KafkaProducerWrapper<NokkelInput, DoneInput>>()
    private val mockKafkaProducerInnboks = mockk<KafkaProducerWrapper<NokkelInput, InnboksInput>>()

    private val mockBrukernotifikasjonService = mockk<BrukernotifikasjonService>()

    @BeforeEach
    fun setup() {
        every { mockTokenValidator.firstValidToken(any()) } returns mockToken
        every { mockKafkaProducerInnboks.flushAndClose() } returns Unit
        every { mockKafkaProducerDone.flushAndClose() } returns Unit
    }

    private val application = Application(
        mockTokenValidator,
        mockBrukernotifikasjonService,
        OldBrukernotifikasjonService(
            naisNamespace = "namespace",
            naisAppName = "appName",
            kafkaProducerDone = mockKafkaProducerDone,
            kafkaProducerInnboks = mockKafkaProducerInnboks
        )
    )
    /*

    @Test
    fun `GET isReady isAlive stop and metrics should all answer OK`() {
        assertEquals(OK, application.api().invoke(Request(GET, "/internal/isReady")).status)

        assertEquals(OK, application.api().invoke(Request(GET, "/internal/isAlive")).status)

        assertEquals(OK, application.api().invoke(Request(GET, "/internal/metrics")).status)
    }

    @Test
    fun `POST innboks with valid token should answer OK`() {
        val payload = createTestInnboksRequest()
        every { mockKafkaProducerInnboks.sendEvent(any(), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/innboks").body(payload))

        verify(exactly = 1) { mockKafkaProducerInnboks.sendEvent(any(), any()) }
        assertEquals(OK, response.status)
    }

    @Test
    fun `POST innboks with invalid token should answer UNAUTHORIZED`() {
        val payload = createTestInnboksRequest()

        every { mockTokenValidator.firstValidToken(any()) } returns null

        every { mockKafkaProducerInnboks.sendEvent(any(), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/innboks").body(payload))

        assertEquals(UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST innboks with invalid payload should answer EXPECTATION_FAILED`() {
        val payload = createTestInnboksRequest(link = "malformed_link")
        every { mockKafkaProducerInnboks.sendEvent(any(), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/innboks").body(payload))

        assertEquals(EXPECTATION_FAILED, response.status)
    }

    @Test
    fun `POST innboks with payload should produce eventId on Kafka`() {
        val myEventId = "461a85ac-2f3f-494a-a37c-2cbee6dcf640"
        val payload = createTestInnboksRequest(
            eventId = myEventId
        )

        val nokkelInputSlot: CapturingSlot<NokkelInput> = slot()
        every { mockKafkaProducerInnboks.sendEvent(capture(nokkelInputSlot), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/innboks").body(payload))

        val capturedNokkelInput = nokkelInputSlot.captured

        verify(exactly = 1) { mockKafkaProducerInnboks.sendEvent(any(), any()) }
        assertEquals(myEventId, capturedNokkelInput.getEventId())
    }

    @Test
    fun `POST innboks with payload should produce corresponding values on Kafka`() {
        val myTekst = "myTekst"
        val myLink = "http://my.link"
        val myChannel = "EPOST"
        val payload = createTestInnboksRequest(
            tekst = myTekst,
            link = myLink,
            channels = myChannel
        )

        val innboksInputSlot: CapturingSlot<InnboksInput> = slot()
        every { mockKafkaProducerInnboks.sendEvent(any(), capture(innboksInputSlot)) } returns Unit

        val response = application.api().invoke(Request(POST, "/innboks").body(payload))

        val capturedInnboksInput = innboksInputSlot.captured

        verify(exactly = 1) { mockKafkaProducerInnboks.sendEvent(any(), any()) }
        assertEquals(myTekst, capturedInnboksInput.getTekst())
        assertEquals(myLink, capturedInnboksInput.getLink())
        assertEquals(myChannel, capturedInnboksInput.getPrefererteKanaler().first())
    }

    @Test
    fun `POST done with valid token should answer OK`() {
        val payload = createTestInnboksRequest()
        every { mockKafkaProducerDone.sendEvent(any(), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/done").body(payload))

        verify(exactly = 1) { mockKafkaProducerDone.sendEvent(any(), any()) }
        assertEquals(OK, response.status)
    }

    @Test
    fun `POST done with invalid token should answer UNAUTHORIZED`() {
        val payload = createTestDoneRequest()

        every { mockTokenValidator.firstValidToken(any()) } returns null

        every { mockKafkaProducerDone.sendEvent(any(), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/done").body(payload))

        assertEquals(UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST done with invalid payload should answer EXPECTATION_FAILED`() {
        val payload = createTestDoneRequest(tidspunkt = "invalid_tidspunkt")
        every { mockKafkaProducerDone.sendEvent(any(), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/done").body(payload))

        assertEquals(EXPECTATION_FAILED, response.status)
    }

    @Test
    fun `POST done with payload should produce eventId on Kafka`() {
        val myEventId = "461a85ac-2f3f-494a-a37c-2cbee6dcf640"
        val payload = createTestDoneRequest(
            eventId = myEventId
        )

        val nokkelInputSlot: CapturingSlot<NokkelInput> = slot()
        every { mockKafkaProducerDone.sendEvent(capture(nokkelInputSlot), any()) } returns Unit

        val response = application.api().invoke(Request(POST, "/done").body(payload))

        val capturedNokkelInput = nokkelInputSlot.captured

        verify(exactly = 1) { mockKafkaProducerDone.sendEvent(any(), any()) }
        assertEquals(myEventId, capturedNokkelInput.getEventId())
    }

    @Test
    fun `POST done with payload should produce DoneInput value on Kafka`() {
        val unixEpochTime = "1970-01-01T00:00:00Z"
        val payload = createTestDoneRequest(
            tidspunkt = unixEpochTime
        )

        val doneInputSlot: CapturingSlot<DoneInput> = slot()
        every { mockKafkaProducerDone.sendEvent(any(), capture(doneInputSlot)) } returns Unit

        val response = application.api().invoke(Request(POST, "/done").body(payload))

        val capturedDoneInput = doneInputSlot.captured

        verify(exactly = 1) { mockKafkaProducerDone.sendEvent(any(), any()) }

        // 0 is long representation of unix epoch time
        assertEquals(0L, capturedDoneInput.getTidspunkt())
    }

    private fun createTestInnboksRequest(
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

    private fun createTestDoneRequest(
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

     */
}
