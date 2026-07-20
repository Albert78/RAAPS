package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED

data class TherapyData(
    var id: Long = ID_UNDEFINED,
    val basalBlocks: List<Block>,
    val isfBlocks: List<Block>,
    val icBlocks: List<Block>,
    val bgBlocks: List<BgBlock>
)