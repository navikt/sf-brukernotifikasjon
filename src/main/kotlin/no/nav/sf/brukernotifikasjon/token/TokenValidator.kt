package no.nav.sf.brukernotifikasjon.token

import no.nav.security.token.support.core.jwt.JwtToken
import org.http4k.core.Request
import java.util.Optional

interface TokenValidator {
    fun firstValidToken(request: Request): Optional<JwtToken>
}
