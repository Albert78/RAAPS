package de.dh.raaps.core.aps

import android.util.Range
import de.dh.raaps.AppPreferencesRepository
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.InsulinTypes
import de.dh.raaps.common.model.ToDo
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.DataRepository

class TherapyModel(
    val dataRepository: DataRepository,
    val appPreferencesRepository: AppPreferencesRepository
) {
    /**
     * Gets the planned basal rate at the given timestamp.
     * Unit: Insulin units.
     */
    suspend fun getBasalPerHour(timestamp: Timestamp): Double {
        ToDo.toBeImplemented("getBasalPerHour")
        return 0.5
    }

    /**
     * Gets the insulin to carbs ratio to be used for calculations at the given timestamp.
     * The ICR is a measure of how many grams of carbohydrates are covered by one unit of insulin.
     * Unit: Grams of carbs.
     */
    suspend fun getIcFactor(timestamp: Timestamp): Double {
        ToDo.toBeImplemented("getIcFactor")
        return 10.0
    }

    /**
     * Gets the insulin sensitivity factor to be used for calculations for the given timestamp.
     * The ISF (sometimes also called the correction factor, CF) is a measure of how much a single
     * unit of insulin lowers blood glucose levels.
     * Unit: Blood glucose delta.
     */
    suspend fun getIsfFactor(timestamp: Timestamp): BgDelta {
        ToDo.toBeImplemented("getIsfFactor")
        return BgDelta(100)
    }

    suspend fun getTarget(): Range<BgValue> {
        ToDo.toBeImplemented("getTarget")
        return Range(BgValue(80), BgValue(120))
    }

    suspend fun getPumpInsulinType(): InsulinType {
        ToDo.toBeImplemented("getPumpInsulinType")
        var insulinType = dataRepository.getInsulinTypeByName(InsulinTypes.ASPART.name)
        if (insulinType == null) {
            insulinType = InsulinTypes.ASPART
            dataRepository.insertInsulinType(insulinType)
        }
        return insulinType
    }
}