package no.nav.sf.brukernotifikasjon.config

import no.nav.sf.brukernotifikasjon.config.TypedEnvVar.getEnvVarAsType
import no.nav.sf.brukernotifikasjon.config.TypedEnvVar.getEnvVarAsTypedList
import no.nav.sf.brukernotifikasjon.config.TypedEnvVar.getOptionalEnvVarAsType
import no.nav.sf.brukernotifikasjon.config.TypedEnvVar.getOptionalEnvVarAsTypedList

object BooleanEnvVar {
    fun getEnvVarAsBoolean(varName: String, default: Boolean? = null): Boolean =
            getEnvVarAsType(varName, default, String::toBoolean)

    fun getOptionalEnvVarAsBoolean(varName: String, default: Boolean? = null): Boolean? =
            getOptionalEnvVarAsType(varName, default, String::toBoolean)

    fun getEnvVarAsBooleanList(varName: String, default: List<Boolean>? = null, separator: String = ","): List<Boolean> =
            getEnvVarAsTypedList(varName, default, separator, String::toBoolean)

    fun getOptionalEnvVarAsBooleanList(varName: String, default: List<Boolean>? = null, separator: String = ","): List<Boolean> =
            getOptionalEnvVarAsTypedList(varName, default, separator, String::toBoolean)
}
