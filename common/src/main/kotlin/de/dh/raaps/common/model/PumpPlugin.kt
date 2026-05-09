package de.dh.raaps.common.model

interface PumpPlugin {
    val name: String
    // TODO

    fun start()
    fun stop()
}