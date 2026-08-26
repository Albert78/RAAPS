package de.dh.raaps.common.model

import android.content.Context
import de.dh.raaps.common.R
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes

val FAST_KE_DEFAULT_PEAK = Minutes(25)

fun getDefaultInsulinTypes(context: Context): List<InsulinType> = listOf(
    InsulinType(
        id = ID_INSULIN_ASPART,
        name = context.getString(R.string.insulin_type_aspart_name),
        dia = Minutes.ofHours(5),
        peak = Minutes(75),
        defaultConcentration = InsulinConcentration.U100
    ),
    InsulinType(
        id = ID_INSULIN_FIASP,
        name = context.getString(R.string.insulin_type_fiasp_name),
        dia = Minutes.ofHours(4),
        peak = Minutes(55),
        defaultConcentration = InsulinConcentration.U100
    )
)

fun getDefaultStandardMealType(context: Context): MealType = MealType(
    id = ID_MEAL_STANDARD,
    name = context.getString(R.string.meal_type_standard_meal_name),
    components = listOf(
        CarbCurveComponentData(weight = 70, peakMinutes = Minutes(75)),
        CarbCurveComponentData(weight = 30, peakMinutes = Minutes(150))
    ),
    cat = Minutes.ofHours(4)
)

fun getDefaultMealTypes(context: Context): List<MealType> = listOf(
    MealType(
        id = ID_MEAL_FAST,
        name = context.getString(R.string.meal_type_fast_carbs_name),
        components = listOf(
            CarbCurveComponentData(weight = 100, peakMinutes = FAST_KE_DEFAULT_PEAK)
        ),
        cat = Minutes(90)
    ),
    getDefaultStandardMealType(context),
    MealType(
        id = ID_MEAL_HIGH_FAT,
        name = context.getString(R.string.meal_type_high_fat_meal_name),
        components = listOf(
            CarbCurveComponentData(weight = 35, peakMinutes = Minutes(60)),
            CarbCurveComponentData(weight = 65, peakMinutes = Minutes(240))
        ),
        cat = Minutes.ofHours(6)
    ),
    MealType(
        id = ID_MEAL_SLOW,
        name = context.getString(R.string.meal_type_slow_meal_name),
        components = listOf(
            CarbCurveComponentData(weight = 40, peakMinutes = Minutes(120)),
            CarbCurveComponentData(weight = 60, peakMinutes = Minutes(300))
        ),
        cat = Minutes.ofHours(8)
    )
)

fun getDefaultInsulinProfile(context: Context, insulinType: InsulinType): InsulinProfile = InsulinProfile(
    name = context.getString(R.string.profile_default_normal_name),
    basalBlocks = listOf(
        Block(
            Minutes.ofHours(24),
            DEFAULT_BASAL_UNITS_PER_HOUR
        )
    ),
    isfBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_ISF_MGDL_PER_UNIT)),
    crBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_CR_GRAM_PER_UNIT)),
    insulinType = insulinType,
    insulinConcentration = insulinType.defaultConcentration,
    dia = insulinType.dia,
    peak = insulinType.peak
)