package no.nav.sf.brukernotifikasjon.config

import java.net.URL
import no.nav.sf.brukernotifikasjon.config.TypedEnvVar.getEnvVarAsType
import no.nav.sf.brukernotifikasjon.config.TypedEnvVar.getOptionalEnvVarAsType

object UrlEnvVar {
    fun getEnvVarAsURL(varName: String, default: URL? = null, trimTrailingSlash: Boolean = false): URL {
        return getEnvVarAsType(varName, default) { envVar ->
            if (trimTrailingSlash) {
                URL(envVar.trimEnd('/'))
            } else {
                URL(envVar)
            }
        }
    }

    fun getOptionalEnvVarAsURL(varName: String, default: URL? = null, trimTrailingSlash: Boolean = false): URL? {
        return getOptionalEnvVarAsType(varName, default) { envVar ->
            if (trimTrailingSlash) {
                URL(envVar.trimEnd('/'))
            } else {
                URL(envVar)
            }
        }
    }
}
