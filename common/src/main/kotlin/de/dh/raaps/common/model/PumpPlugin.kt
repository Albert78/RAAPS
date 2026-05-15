package de.dh.raaps.common.model

interface PumpPlugin: Plugin {
    val name: String
    // TODO

    fun start()
    fun stop()
}