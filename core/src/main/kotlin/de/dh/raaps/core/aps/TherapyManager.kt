package de.dh.raaps.core.aps

import android.util.Range
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.DEFAULT_BASAL_UNITS_PER_HOUR
import de.dh.raaps.common.model.DEFAULT_IC_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_BG_TARGET_MGDL
import de.dh.raaps.common.model.DEFAULT_BG_LOW_THRESHOLD_MGDL
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Profile
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

    val currentTherapySettingsFlow: Flow<CurrentTherapySettings?> = therapyRepository.observeCurrentTherapySettings()

    suspend fun getActiveTherapySettings(): CurrentTherapySettings? = therapyRepository.getCurrentTherapySettings()

    /**
     * Gets the planned basal rate at the given timestamp.
     * Unit: Insulin units.
     */
    suspend fun getBasalPerHour(timestamp: Timestamp): Double {
        val settings = getActiveTherapySettings() ?: return DEFAULT_BASAL_UNITS_PER_HOUR
        val data = settings.profile.therapyData
        val baseBasal = data.basalBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.adjustmentPercentage) / 100.0
        return baseBasal * factor
    }

    /**
     * Gets the insulin to carbs ratio to be used for calculations at the given timestamp.
     * The ICR is a measure of how many grams of carbohydrates are covered by one unit of insulin.
     * Unit: Grams of carbs.
     */
    suspend fun getIcFactor(timestamp: Timestamp): Double {
        val settings = getActiveTherapySettings() ?: return DEFAULT_IC_GRAM_PER_UNIT
        val data = settings.profile.therapyData
        val baseIc = data.icBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.adjustmentPercentage) / 100.0
        return baseIc / factor
    }

    /**
     * Gets the insulin sensitivity factor to be used for calculations for the given timestamp.
     * The ISF (sometimes also called the correction factor, CF) is a measure of how much a single
     * unit of insulin lowers blood glucose levels.
     * Unit: Blood glucose delta.
     */
    suspend fun getIsfFactor(timestamp: Timestamp): BgDelta {
        val settings = getActiveTherapySettings() ?: return BgDelta(
            DEFAULT_ISF_MGDL_PER_UNIT.toInt().toShort()
        )
        val data = settings.profile.therapyData
        val amount = data.isfBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        val factor = (100.0 + settings.adjustmentPercentage) / 100.0
        return BgDelta.fromMgDl((amount / factor).toInt())
    }

    suspend fun getBgSettings(): Pair<BgValue, BgValue> {
        val data = getActiveTherapySettings()?.profile?.therapyData
            ?: return Pair(
                BgValue.fromMgDl(DEFAULT_BG_TARGET_MGDL),
                BgValue.fromMgDl(DEFAULT_BG_LOW_THRESHOLD_MGDL)
            )

        return data.bgBlocks.getBgForMinute(Timestamp.now().minutesSinceMidnight())
    }

    suspend fun getPumpInsulinType(): InsulinType {
        val currentSettings = getActiveTherapySettings()
        if (currentSettings != null) {
            return currentSettings.insulinType
        }

        return therapyRepository.getAllInsulinTypes().firstOrNull()
            ?: throw IllegalStateException("No insulin type configured for insulin pump")
    }

    suspend fun getAllProfiles() = therapyRepository.getAllProfiles()

    fun observeAllProfiles() = therapyRepository.observeAllProfiles()

    /**
     * Updates the current therapy settings based on a selected profile.
     * This will create a copy of the profile's therapy data as the active configuration.
     */
    suspend fun selectProfile(profile: Profile) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
            val newSettings = (currentSettings ?: CurrentTherapySettings(
                profile = profile,
                insulinType = therapyRepository.getAllInsulinTypes().firstOrNull()
                    ?: throw IllegalStateException("No insulin type configured for insulin pump")
            )).copy(
                profile = profile
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }

    suspend fun setAdjustmentPercentage(percentage: Int) {
        mutex.withLock {
            val currentSettings = getActiveTherapySettings()
                ?: throw IllegalStateException("No active therapy settings found")
            val newSettings = currentSettings.copy(
                adjustmentPercentage = percentage
            )
            therapyRepository.updateCurrentTherapySettings(newSettings)
        }
    }
}