package de.dh.raaps.common.model

data class DataProvider(
    val id: Long = ID_UNDEFINED,
    val name: String,
    val type: String
)