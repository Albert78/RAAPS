package de.dh.raaps.core.aps

import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.DEFAULT_CR_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_BG_TARGET_MGDL
import de.dh.raaps.common.model.DEFAULT_BG_LOW_THRESHOLD_MGDL
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import de.dh.raaps.common.model.data.getBgForMinute
import de.dh.raaps.core.repository.TherapyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TherapyManager(
    private val therapyRepository: TherapyRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) {
    private val mutex = Mutex()

    val currentTherapySettingsFlow: Flow<CurrentTherapySettings> = therapyRepository.observeCurrentTherapySettings()

    suspend fun getActiveTherapySettings(): CurrentTherapySettings = therapyRepository.getCurrentTherapySettings()

    /**
     * Gets the planned basal rate at the given timestamp.
     * Unit: Insulin units.
     */
    suspend fun getBasalPerHour(timestamp: Timestamp): Double {
        val settings = getActiveTherapySettings()
        val profile = settings.insulinProfile
        val baseBasal = profile.basalBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return baseBasal * factor
    }

    /**
     * Gets the carbohydrate to insulin ratio to be used for calculations at the given timestamp.
     * The CR is a measure of how many grams of carbohydrates are covered by one unit of insulin.
     * Unit: Grams of carbs.
     */
    suspend fun getCrFactor(timestamp: Timestamp): Double {
        val settings = getActiveTherapySettings()
        val profile = settings.insulinProfile
        val baseCr = profile.crBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return baseCr / factor
    }

    /**
     * Gets the insulin sensitivity factor to be used for calculations for the given timestamp.
     * The ISF (sometimes also called the correction factor, CF) is a measure of how much a single
     * unit of insulin lowers blood glucose levels.
     * Unit: Blood glucose delta.
     */
    suspend fun getIsfFactor(timestamp: Timestamp): BgDelta {
        val settings = getActiveTherapySettings()
        val profile = settings.insulinProfile
        val amount = profile.isfBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.insulinAdjustmentPercentage) / 100.0
        return BgDelta.fromMgDl((amount / factor).toInt())
    }

    suspend fun getBgSettings(): Pair<BgValue, BgValue> {
        val settings = getActiveTherapySettings()
        val defaultBg = settings.defaultBgBlocks.getBgForMinute(Timestamp.now().minutesSinceMidnight())
        return Pair(
            settings.targetBgOverride ?: defaultBg.first,
            settings.lowThresholdOverride ?: defaultBg.second
        )
    }

    suspend fun updateDefaultBgBlocks(blocks: List<BgBlock>) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                defaultBgBlocks = blocks
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun getPumpInsulinType(): InsulinType {
        val currentSettings = getActiveTherapySettings()
        return currentSettings.insulinProfile.insulinType
    }

    suspend fun getAllInsulinProfiles() = therapyRepository.getAllInsulinProfiles()

    fun observeAllInsulinProfiles() = therapyRepository.observeAllInsulinProfiles()

    /**
     * Updates the current therapy settings based on a selected profile.
     * This will create a copy of the profile's therapy data as the active configuration.
     */
    suspend fun selectInsulinProfile(profile: InsulinProfile) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            
            val newSettings = currentSettings.copy(
                insulinProfile = profile
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setTherapyAdjustment(percentage: Int, targetBg: BgValue?, lowThreshold: BgValue?, adjustmentHint: String?) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                insulinAdjustmentPercentage = percentage,
                targetBgOverride = targetBg,
                lowThresholdOverride = lowThreshold,
                adjustmentHint = adjustmentHint
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setInsulinAdjustmentPercentage(percentage: Int) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                insulinAdjustmentPercentage = percentage
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setTargetBgOverride(target: BgValue?) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                targetBgOverride = target
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setLowThresholdOverride(threshold: BgValue?) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = currentSettings.copy(
                lowThresholdOverride = threshold
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }
}