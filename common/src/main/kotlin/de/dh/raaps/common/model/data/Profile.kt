package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED

/**
 * A therapy profile that defines a set of therapy factors.
 * Profiles are used to switch between different metabolic states (e.g. Normal, Sport, Illness).
 */
data class Profile(
    val id: Long = ID_UNDEFINED,
    val name: String,
    val therapyData: TherapyData
)