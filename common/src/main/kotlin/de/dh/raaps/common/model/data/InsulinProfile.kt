package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType

/**
 * A therapy profile that defines a set of therapy factors.
 * Profiles are used to switch between different metabolic states (e.g. Normal, Sport, Illness).
 */
data class InsulinProfile(
    var id: Long = ID_UNDEFINED,
    val name: String,
    val basalBlocks: List<Block>,
    val isfBlocks: List<Block>,
    val crBlocks: List<Block>,
    val insulinType: InsulinType,
    val dia: Minutes,
    val peak: Minutes
)