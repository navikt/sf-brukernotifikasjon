package no.nav.sf.brukernotifikasjon

import org.http4k.core.Response
import org.http4k.core.Status

fun `that`(res: Response) = ResponseValidator(res)
fun `that`(value: String) = ResponseValidator(value)

class ResponseValidator() {
    private lateinit var response: Response
    private lateinit var value: String

    constructor(resp: Response) : this() {
        this.response = resp
    }

    constructor(value: String) : this() {
        this.value = value
    }

    fun `is`(code: Status) = response.status == Response(code).status
    fun `is`(v: String) = value == v
}
