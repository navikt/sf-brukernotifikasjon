package no.nav.sf.henvendelse.api.proxy.token

import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import no.nav.sf.brukernotifikasjon.env
import no.nav.sf.brukernotifikasjon.env_AZURE_APP_CLIENT_ID
import no.nav.sf.brukernotifikasjon.env_AZURE_APP_WELL_KNOWN_URL
import no.nav.sf.brukernotifikasjon.token.TokenValidator
import org.http4k.core.Request
import java.io.File
import java.net.URL
import java.util.Optional

class DefaultTokenValidator : TokenValidator {
    private val azureAlias = "azure"
    private val azureUrl = env(env_AZURE_APP_WELL_KNOWN_URL)
    private val azureAudience = env(env_AZURE_APP_CLIENT_ID).split(',')

    private val multiIssuerConfiguration = MultiIssuerConfiguration(
        mapOf(
            azureAlias to IssuerProperties(URL(azureUrl), azureAudience)
        )
    )

    private val jwtTokenValidationHandler = JwtTokenValidationHandler(multiIssuerConfiguration)

    override fun firstValidToken(request: Request): Optional<JwtToken> {
        val result: Optional<JwtToken> = jwtTokenValidationHandler.getValidatedTokens(request.toNavRequest()).firstValidToken
        if (!result.isPresent) {
            File("/tmp/novalidtoken").writeText(request.toMessage())
        }
        return result
    }

    private fun Request.toNavRequest(): HttpRequest {
        val req = this
        return object : HttpRequest {
            override fun getHeader(headerName: String): String {
                return req.header(headerName) ?: ""
            }
            override fun getCookies(): Array<HttpRequest.NameValue> {
                return arrayOf()
            }
        }
    }
}
