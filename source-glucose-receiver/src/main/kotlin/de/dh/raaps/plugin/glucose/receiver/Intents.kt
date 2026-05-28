package de.dh.raaps.plugin.glucose.receiver

class Intents {
    companion object {
        // BG reading from xDrip via BroadcastReceiver
        const val XDRIP_ACTION_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"
        const val XDRIP_EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val XDRIP_EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val XDRIP_EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        const val XDRIP_EXTRA_SENSOR_BATTERY = "com.eveningoutpost.dexdrip.Extras.SensorBattery"
        const val XDRIP_EXTRA_SENSOR_STARTED_AT = "com.eveningoutpost.dexdrip.Extras.SensorStartedAt"
        const val XDRIP_EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"
        const val XDRIP_EXTRA_RAW = "com.eveningoutpost.dexdrip.Extras.Raw"
        const val XDRIP_DATA_SOURCE = "com.eveningoutpost.dexdrip.Extras.SourceInfo"
        const val XDRIP_DATA_SOURCE_DESCRIPTION = "com.eveningoutpost.dexdrip.Extras.SourceDesc"
    }
}