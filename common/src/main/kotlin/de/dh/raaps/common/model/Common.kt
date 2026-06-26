package de.dh.raaps.common.model

const val ID_UNDEFINED = 0L

const val MINUTES_PER_HOUR = 60
const val HOURS_PER_DAY = 24
const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY
const val MS_PER_HOUR = MINUTES_PER_HOUR * 60 * 1000

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

const val IC_MIN = 1.0
const val IC_MAX = 100.0

const val TARGET_MIN = 70
const val TARGET_MAX = 180

const val DEFAULT_BASAL_UNITS_PER_HOUR = 0.5
const val DEFAULT_ISF_MGDL_PER_UNIT = 100.0
const val DEFAULT_IC_GRAM_PER_UNIT = 10.0
const val DEFAULT_TARGET_LOW_MGDL: Short = 70
const val DEFAULT_TARGET_HIGH_MGDL: Short = 120