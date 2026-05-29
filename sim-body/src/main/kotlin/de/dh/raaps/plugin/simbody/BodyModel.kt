package de.dh.raaps.plugin.simbody

import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.data.BgValue

class BodyModel {
    val meals: List<MealEntry> = ArrayList()
    val insulinApplications: List<InsulinApplication> = ArrayList()

    var bloodGlucose: BgValue = BgValue(100)
}