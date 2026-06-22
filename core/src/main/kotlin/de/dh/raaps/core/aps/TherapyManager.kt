package de.dh.raaps.core.aps

import android.util.Range
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.DEFAULT_BASAL_UNITS_PER_HOUR
import de.dh.raaps.common.model.DEFAULT_IC_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_TARGET_HIGH_MGDL
import de.dh.raaps.common.model.DEFAULT_TARGET_LOW_MGDL
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.CurrentTherapyData
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import de.dh.raaps.common.model.data.getTargetForMinute
import de.dh.raaps.core.repository.TherapyRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TherapyManager(
    private val therapyRepository: TherapyRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) {
    private val mutex = Mutex()
    private var cachedTherapyData: CurrentTherapyData? = null

    /**
     * Ensures the therapy data is loaded from the repository.
     */
    suspend fun load() {
        getActiveTherapyData()
    }

    private suspend fun getActiveTherapyDataInternal(): CurrentTherapyData? {
        if (cachedTherapyData == null) {
            cachedTherapyData = therapyRepository.getCurrentTherapyData()
        }
        return cachedTherapyData
    }

    suspend fun getActiveTherapyData(): CurrentTherapyData? = mutex.withLock {
        getActiveTherapyDataInternal()
    }

    /**
     * Gets the planned basal rate at the given timestamp.
     * Unit: Insulin units.
     */
    suspend fun getBasalPerHour(timestamp: Timestamp): Double {
        val data = getActiveTherapyData()?.therapyData ?: return DEFAULT_BASAL_UNITS_PER_HOUR
        return data.basalBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
    }

    /**
     * Gets the insulin to carbs ratio to be used for calculations at the given timestamp.
     * The ICR is a measure of how many grams of carbohydrates are covered by one unit of insulin.
     * Unit: Grams of carbs.
     */
    suspend fun getIcFactor(timestamp: Timestamp): Double {
        val data = getActiveTherapyData()?.therapyData ?: return DEFAULT_IC_GRAM_PER_UNIT
        return data.icBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
    }

    /**
     * Gets the insulin sensitivity factor to be used for calculations for the given timestamp.
     * The ISF (sometimes also called the correction factor, CF) is a measure of how much a single
     * unit of insulin lowers blood glucose levels.
     * Unit: Blood glucose delta.
     */
    suspend fun getIsfFactor(timestamp: Timestamp): BgDelta {
        val data = getActiveTherapyData()?.therapyData ?: return BgDelta(
            DEFAULT_ISF_MGDL_PER_UNIT.toInt().toShort()
        )
        val amount = data.isfBlocks.getAmountForMinute(timestamp.minutesSinceMidnight())
        return BgDelta.fromMgDl(amount.toInt())
    }

    suspend fun getTarget(): Range<BgValue> {
        val data = getActiveTherapyData()?.therapyData
            ?: return Range(
                BgValue.fromMgDl(DEFAULT_TARGET_LOW_MGDL),
                BgValue.fromMgDl(DEFAULT_TARGET_HIGH_MGDL)
            )

        val target = data.targetBlocks.getTargetForMinute(Timestamp.now().minutesSinceMidnight())
        return Range(target.first, target.second)
    }

    suspend fun getPumpInsulinType(): InsulinType {
        val currentData = getActiveTherapyData()
        if (currentData != null) {
            return currentData.insulinType
        }

        return therapyRepository.getAllInsulinTypes().firstOrNull()
            ?: throw IllegalStateException("No insulin type configured for insulin pump")
    }

    suspend fun getAllProfiles() = therapyRepository.getAllProfiles()

    suspend fun getCurrentTherapyData() = getActiveTherapyData()

    /**
     * Updates the current therapy settings based on a selected profile.
     * This will create a copy of the profile's therapy data as the active configuration.
     */
    suspend fun selectProfile(profile: Profile) {
        mutex.withLock {
            val currentData = getActiveTherapyDataInternal()
            val newData = (currentData ?: CurrentTherapyData(
                profileId = profile.id,
                therapyData = profile.therapyData,
                insulinType = therapyRepository.getAllInsulinTypes().firstOrNull()
                    ?: throw IllegalStateException("No insulin type configured for insulin pump")
            )).copy(
                profileId = profile.id,
                therapyData = profile.therapyData.copy(id = ID_UNDEFINED)
            )
            therapyRepository.updateCurrentTherapyData(newData)
            cachedTherapyData = newData
        }
    }
}
