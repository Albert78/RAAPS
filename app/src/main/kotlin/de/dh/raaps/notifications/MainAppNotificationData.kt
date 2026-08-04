package de.dh.raaps.notifications

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.core.aps.GlucoseSourceManager

data class MainAppNotificationData(
    val lastBgSample: BgReading?,
    val secondToLastBgSample: BgReading?
) {
    fun getBgDelta(): BgValue? {
        if (lastBgSample == null || lastBgSample.sampleKind != BgSampleKind.Value
            || secondToLastBgSample == null || secondToLastBgSample.sampleKind != BgSampleKind.Value
        ) {
            return null
        }
        return BgValue.fromMgDl(lastBgSample.value.mgdl - secondToLastBgSample.value.mgdl)
    }

    companion object {
        fun create(glucoseSourceManager: GlucoseSourceManager): MainAppNotificationData {
            val lastBg = glucoseSourceManager.currentBg.value
            val secondToLastBg = glucoseSourceManager.lastBg
            return MainAppNotificationData(lastBg, secondToLastBg)
        }
    }
}