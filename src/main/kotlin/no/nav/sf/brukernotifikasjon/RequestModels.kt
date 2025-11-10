package no.nav.sf.brukernotifikasjon

data class Tekst(
    val spraakkode: String,
    val tekst: String,
    val default: Boolean
)

data class EksternVarsling(
    val prefererteKanaler: List<String>,
    val smsVarslingstekst: String?,
    val epostVarslingstittel: String?,
    val epostVarslingstekst: String?,
    val kanBatches: Boolean?
)

data class Produsent(
    val cluster: String,
    val namespace: String,
    val appnavn: String
)

data class OpprettVarselRequest(
    val event_name: String,
    val type: String,
    val varselId: String,
    val ident: String,
    val tekster: List<Tekst>,
    val link: String,
    val sensitivitet: String,
    val aktivFremTil: String,
    val eksternVarsling: EksternVarsling,
    val produsent: Produsent
)