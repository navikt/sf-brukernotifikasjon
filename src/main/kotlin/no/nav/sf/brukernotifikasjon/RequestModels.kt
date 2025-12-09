package no.nav.sf.brukernotifikasjon

import com.google.gson.annotations.SerializedName

data class Tekst(
    val spraakkode: String,
    val tekst: String,
    val default: Boolean,
)

data class EksternVarsling(
    val preferertKanal: String?,
    val smsVarslingstekst: String?,
    val epostVarslingstittel: String?,
    val epostVarslingstekst: String?,
    val kanBatches: Boolean?,
)

data class Produsent(
    val cluster: String,
    val namespace: String,
    val appnavn: String,
)

data class OpprettVarselRequest(
    @SerializedName("@event_name")
    val event_name: String,
    val type: String,
    val varselId: String,
    val ident: String,
    val tekster: List<Tekst>,
    val link: String,
    val sensitivitet: String,
    val aktivFremTil: String?,
    val eksternVarsling: EksternVarsling,
    val produsent: Produsent,
)

data class InaktiverVarselRequest(
    @SerializedName("@event_name")
    val event_name: String,
    val varselId: String,
    val produsent: Produsent,
)

// TODO DEPRECATED MODELS:
data class DoneRequest(
    val eventId: String,
    val tidspunkt: String,
    val fodselsnummer: String,
    val grupperingsId: String,
)

data class InnboksRequest(
    val eventId: String,
    val eksternVarsling: Boolean = false,
    val link: String = "",
    val sikkerhetsnivaa: Int = 4,
    val tekst: String,
    val prefererteKanaler: String = "",
    val tidspunkt: String,
    val fodselsnummer: String,
    val grupperingsId: String,
    val epostVarslingstekst: String,
    val epostVarslingstittel: String,
    val smsVarslingstekst: String,
)
