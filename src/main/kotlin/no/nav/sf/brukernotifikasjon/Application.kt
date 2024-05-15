package no.nav.sf.brukernotifikasjon

import mu.KotlinLogging
import no.nav.sf.brukernotifikasjon.service.BrukernotifikasjonService
import no.nav.sf.brukernotifikasjon.token.TokenValidator
import no.nav.sf.henvendelse.api.proxy.token.DefaultTokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.PathMethod
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.ApacheServer
import org.http4k.server.Http4kServer
import org.http4k.server.asServer

@Volatile var shuttingDown: Boolean = false

class Application(
    private val tokenValidator: TokenValidator = DefaultTokenValidator(),
    private val brukernotifikasjonService: BrukernotifikasjonService = BrukernotifikasjonService(),
) {
    private val log = KotlinLogging.logger { }

    fun start() {
        log.info { "Starting" }
        apiServer(8080).start()
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(ApacheServer(port))

    fun api(): HttpHandler = routes(
        "/internal/isAlive" bind Method.GET to { Response(Status.OK) },
        "/internal/isReady" bind Method.GET to isReadyHandler,
        "/internal/metrics" bind Method.GET to Metrics.metricsHandler,
        "/innboks" authbind Method.POST to brukernotifikasjonService.innboksHandler,
        "/done" authbind Method.POST to brukernotifikasjonService.doneHandler,
    )

    private val isReadyHandler: HttpHandler = {
        if (shuttingDown) {
            Response(Status.SERVICE_UNAVAILABLE).body("Application is shutting down")
        } else {
            Response(Status.OK)
        }
    }

    /**
     * authbind: a variant of bind that takes care of authentication with use of tokenValidator
     */
    infix fun String.authbind(method: Method) = AuthRouteBuilder(this, method, tokenValidator)

    data class AuthRouteBuilder(val path: String, val method: Method, private val tokenValidator: TokenValidator) {
        infix fun to(action: HttpHandler): RoutingHttpHandler =
            PathMethod(path, method) to { request ->
                val token = tokenValidator.firstValidToken(request)
                if (token.isPresent) {
                    action(request)
                } else {
                    Metrics.apiIssues.inc()
                    Response(Status.UNAUTHORIZED)
                }
            }
    }
}
