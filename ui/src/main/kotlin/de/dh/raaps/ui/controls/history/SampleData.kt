package de.dh.raaps.ui.controls.history

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgSampleKind
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Timestamp
import kotlin.math.sin
import kotlin.random.Random

fun generatedBg(minsInterval: Short, index: Int, startTs: Timestamp): BgReading {
    val base = 170.0
    val curve = 100.0 * sin(index * minsInterval / 50.0) + 15.0 * sin(index * minsInterval / 60.0)
    val noise = Random.nextDouble(-5.0, 5.0)
    return BgReading(value = BgValue.fromMgDl((base + curve + noise).toInt().coerceIn(40, 400)), sampleKind = BgSampleKind.Value, timestamp = startTs.plusMinutes(index * minsInterval))
}

fun createSampleReadings(size: Int, minsInterval: Short): List<BgReading> {
    val startTs = Timestamp.now().minusMinutes(minsInterval * size + 10)
    return List(size) { index -> generatedBg(minsInterval, index, startTs) }
}