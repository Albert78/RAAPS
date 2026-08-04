package de.dh.raaps.common.model

const val ID_UNDEFINED = 0L

const val SECONDS_PER_MINUTE = 60
const val MINUTES_PER_HOUR = 60
const val HOURS_PER_DAY = 24
const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY

const val MS_PER_MINUTE = SECONDS_PER_MINUTE * 1000L
const val MS_PER_HOUR = MINUTES_PER_HOUR * MS_PER_MINUTE
const val MS_PER_DAY = MS_PER_HOUR * HOURS_PER_DAY

const val ID_INSULIN_ASPART = "9d860e7e-8c88-466d-a7f4-3e91851e3c88"
const val ID_INSULIN_FIASP = "4e0e9803-0c48-433b-8f7d-2b4f2c96791a"

const val ID_MEAL_FAST = "b13c3b03-4f9e-4e4b-8e1e-1f8d4c96791a"
const val ID_MEAL_STANDARD = "f2a7a403-4f9e-4e4b-8e1e-2f8d4c96791a"
const val ID_MEAL_HIGH_FAT = "a3b8b503-4f9e-4e4b-8e1e-3f8d4c96791a"
const val ID_MEAL_SLOW = "d4c9c603-4f9e-4e4b-8e1e-4f8d4c96791a"

const val INSULIN_EPSILON = 0.01

const val BASAL_MIN = 0.1
const val BASAL_MAX = 10.0

const val ISF_MIN = 10.0
const val ISF_MAX = 300.0

const val CR_MIN = 1.0
const val CR_MAX = 100.0

const val TARGET_MIN = 70
const val TARGET_MAX = 180

const val LOW_THRESHOLD_MIN = 50
const val LOW_THRESHOLD_MAX = 150

const val ADJUSTMENT_PERCENTAGE_MIN = -100
const val ADJUSTMENT_PERCENTAGE_MAX = 200

const val CARBS_KE_MIN = 0.0
const val CARBS_KE_MAX = 30.0

const val BOLUS_MIN = 0.0
const val BOLUS_MAX = 50.0

const val DEFAULT_BASAL_UNITS_PER_HOUR = 0.5
const val DEFAULT_ISF_MGDL_PER_UNIT = 50.0
const val DEFAULT_CR_GRAM_PER_UNIT = 10.0
const val DEFAULT_BG_TARGET_MGDL: Short = 100
const val DEFAULT_BG_LOW_THRESHOLD_MGDL: Short = 70

const val DEFAULT_DIA_MINUTES = 300
const val DEFAULT_PEAK_MINUTES = 75

const val MEAL_EDIT_THRESHOLD_HOURS = 6