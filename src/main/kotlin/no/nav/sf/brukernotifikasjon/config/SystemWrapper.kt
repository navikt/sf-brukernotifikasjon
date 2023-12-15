package no.nav.sf.brukernotifikasjon.config

var systemWrapperDelegate: SystemWrapperInterface? = null

object SystemWrapper : SystemWrapperInterface by systemWrapperDelegate ?: SystemWrapperImpl

interface SystemWrapperInterface {
    fun getEnvVar(varName: String): String?
}

internal object SystemWrapperImpl : SystemWrapperInterface {
    override fun getEnvVar(varName: String): String? {
        return System.getenv(varName)
    }
}
