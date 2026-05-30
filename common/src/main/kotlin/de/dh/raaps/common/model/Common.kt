package de.dh.raaps.common.model

const val ID_UNDEFINED = 0L

const val MINUTES_PER_HOUR = 60
const val HOURS_PER_DAY = 24
const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY

const val DEFAULT_TARGET_LOW = 80
const val DEFAULT_TARGET_HIGH = 120
val DEFAULT_INSULIN_TYPE = InsulinTypes.ASPART.name

const val BASAL_MIN = 0.1
const val BASAL_MAX = 10.0

const val ISF_MIN = 10.0
const val ISF_MAX = 300.0

const val IC_MIN = 1.0
const val IC_MAX = 100.0

const val TARGET_MIN = 70
const val TARGET_MAX = 180

const val DEFAULT_BASAL_BLOCK_UNITS_PER_HOUR = 0.5
const val DEFAULT_ISF_MGDL_PER_UNIT = 100.0
const val DEFAULT_IC_GRAM_PER_UNIT = 10.0
const val DEFAULT_TARGET_LOW_MGDL: Short = 70
const val DEFAULT_TARGET_HIGH_MGDL: Short = 120