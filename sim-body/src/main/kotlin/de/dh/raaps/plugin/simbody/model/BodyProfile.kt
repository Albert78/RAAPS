package de.dh.raaps.plugin.simbody.model

import de.dh.raaps.common.model.data.Block

/**
 * Metabolic profile of a body for simulation.
 */
data class BodyProfile(
    val crBlocks: List<Block>,
    val isfBlocks: List<Block>,
    val liverGlucoseOutputBlocks: List<Block>
)