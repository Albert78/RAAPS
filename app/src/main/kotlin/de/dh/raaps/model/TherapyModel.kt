package de.dh.raaps.model

import android.util.Range
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.data.DataRepository

class TherapyModel(
    val dataRepository: DataRepository
) {
    /**
     * Gets the planned basal rate at the given timestamp.
     * Unit: Insulin units.
     */
    fun getBasalPerHour(timestamp: Timestamp): Double {
        weiter
    }

    /**
     * Gets the insulin to carbs ratio to be used for calculations at the given timestamp.
     * The ICR is a measure of how many grams of carbohydrates are covered by one unit of insulin.
     * Unit: Grams of carbs.
     */
    fun getIcFactor(timestamp: Timestamp): Double {
        weiter
    }

    /**
     * Gets the insulin sensitivity factor to be used for calculations for the given timestamp.
     * The ISF (sometimes also called the correction factor, CF) is a measure of how much a single
     * unit of insulin lowers blood glucose levels.
     * Unit: Blood glucose delta.
     */
    fun getIsfFactor(timestamp: Timestamp): BgDelta {
        weiter
    }

    fun getTarget(): Range<BgValue> {
        weiter
    }
}