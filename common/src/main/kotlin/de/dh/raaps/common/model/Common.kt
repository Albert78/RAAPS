package de.dh.raaps.common.model

const val ID_UNDEFINED = 0L

const val MINUTES_PER_HOUR = 60
const val HOURS_PER_DAY = 24
const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY

const val DEFAULT_TARGET_LOW = 80
const val DEFAULT_TARGET_HIGH = 120
val DEFAULT_INSULIN_TYPE = InsulinTypes.ASPART.name