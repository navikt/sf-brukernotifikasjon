package no.nav.sf.brukernotifikasjon.config

internal object SystemWrapper {
    fun getEnvVar(varName: String): String? {
        return System.getenv(varName)
    }
}
