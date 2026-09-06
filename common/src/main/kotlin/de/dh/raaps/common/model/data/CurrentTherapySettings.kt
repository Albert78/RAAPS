package de.dh.raaps.common.model.data

import de.dh.raaps.common.model.ID_UNDEFINED

/**
 * Represents the current active therapy settings of the app.
 * It references the active [InsulinProfile].
 */
data class CurrentTherapySettings(
    var id: Long = ID_UNDEFINED,
    val insulinProfile: InsulinProfile,
    val defaultBgBlocks: List<BgBlock> = emptyList(),
    val insulinAdjustmentPercentage: Int = 0,
    val targetBgOverride: BgValue? = null,
    val lowThresholdOverride: BgValue? = null,
    val adjustmentHint: String? = null,
) {
    /**
     * Calculates and caches the effective insulin profile considering [insulinAdjustmentPercentage].
     * Evaluated lazily once per instance.
     */
    val effectiveInsulinProfile: InsulinProfile by lazy {
        if (insulinAdjustmentPercentage == 0) {
            insulinProfile
        } else {
            val factor = (100.0 + insulinAdjustmentPercentage) / 100.0
            insulinProfile.copy(
                basalBlocks = insulinProfile.basalBlocks.map { it.copy(amount = it.amount * factor) },
                crBlocks = insulinProfile.crBlocks.map { it.copy(amount = it.amount / factor) },
                isfBlocks = insulinProfile.isfBlocks.map { it.copy(amount = (it.amount / factor)) }
            )
        }
    }
}