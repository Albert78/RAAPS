package de.dh.raaps.notifications

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.core.aps.APS

data class ApsNotificationData(
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
        fun create(aps: APS): ApsNotificationData {
            val lastBg = aps.getCurrentBg()
            val secondToLastBg = aps.getLastBg()
            return ApsNotificationData(lastBg, secondToLastBg)
        }
    }
}