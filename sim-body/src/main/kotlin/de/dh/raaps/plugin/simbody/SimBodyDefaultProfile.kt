package de.dh.raaps.plugin.simbody

import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.TargetBlock
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.plugin.simbody.model.BodyProfile

/**
 * A standard therapy profile.
 */
val DEFAULT_SIM_THERAPY_PROFILE = TherapyData(
    basalBlocks = listOf(Block(Minutes.ofHours(24), 0.5)),
    isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
    icBlocks = listOf(Block(Minutes.ofHours(24), 10.0)),
    targetBlocks = listOf(
        TargetBlock(
            duration = Minutes.ofHours(24),
            lowTarget = BgValue.fromMgDl(80),
            highTarget = BgValue.fromMgDl(120)
        )
    )
)

/**
 * A standard body profile.
 */
val DEFAULT_SIM_BODY_PROFILE = BodyProfile(
    liverGlucoseOutputBlocks = listOf(Block(Minutes.ofHours(24), 5.0)),
    isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
    icBlocks = listOf(Block(Minutes.ofHours(24), 10.0))
)