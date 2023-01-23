package no.nav.sf.library

import mu.KotlinLogging

object PrestopHook {

    private val log = KotlinLogging.logger { }

    @Volatile
    private var prestopHook = false

    init {
        log.info { "Installing prestop hook" }
    }

    fun isActive() = prestopHook
    fun activate() { prestopHook = true }
    fun reset() { prestopHook = false }
}
