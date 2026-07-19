package de.dh.raaps.core.aps

import de.dh.raaps.common.model.BasalHistoryPoint
import de.dh.raaps.common.model.BolusHistoryPoint
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.INSULIN_EPSILON
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.model.data.getAmountForMinute
import de.dh.raaps.core.repository.TreatmentRepository
import kotlin.math.abs


class InsulinHistoryHelper {
    companion object {
        /**
        * Fills the scheduled basal history from the most recent entry up to the current time.
        * This method is intended to be called when a profile changes to fill in missing values
        * from the old profile up to the current time.
        *
        * All ticks before the current one will use the scheduled rate from [oldTherapyData].
        * For the current tick, the rate is mixed between the old and the new basal value, based on the change time.
        */
        suspend fun updateScheduledBasal(
            oldTherapyData: TherapyData,
            newTherapyData: TherapyData,
            insulinType: InsulinType,
            treatmentRepository: TreatmentRepository
        ) {
            val now = Timestamp.now()
            val currentTick = treatmentRepository.tickForTimestamp(now)
            val minutesPerTick = 60 / TreatmentRepository.BASAL_TICKS_PER_HOUR
            val tickDurationMs = minutesPerTick * 60 * 1000L
            val midTickMs = currentTick.toLong() * tickDurationMs + (tickDurationMs / 2)

            val lastEntry = treatmentRepository.getLastBasalHistoryEntry()
            val historyStartTick = treatmentRepository.tickForTimestamp(now.minus(treatmentRepository.historySize))
            val startTick = if (lastEntry != null) maxOf(lastEntry.startTick + 1, historyStartTick) else historyStartTick

            for (tick in startTick..currentTick) {
                val tickTimestamp = Timestamp(tick.toLong() * tickDurationMs)
                val minutesSinceMidnight = tickTimestamp.minutesSinceMidnight()

                val rate = if (tick < currentTick) {
                    oldTherapyData.basalBlocks.getAmountForMinute(minutesSinceMidnight)
                } else {
                    if (now.ms < midTickMs) {
                        oldTherapyData.basalBlocks.getAmountForMinute(minutesSinceMidnight)
                    } else {
                        newTherapyData.basalBlocks.getAmountForMinute(minutesSinceMidnight)
                    }
                }
                treatmentRepository.updateInsulinApplication(
                    InsulinApplication(
                        timestamp = tickTimestamp,
                        amount = rate,
                        scheduledAmount = rate,
                        insulinType = insulinType,
                        origin = InsulinOrigin.Pump,
                        reason = "Basal"
                    )
                )
            }
        }

        /**
        * Updates the historical basal rates using a list of delivered basal points from the pump.
        * This method supplements historical values with current values from the pump,
        * using [therapyData] of the current profile for scheduled rates.
        *
        * 1. Fills any gaps up to the first history point with scheduled rates from [therapyData].
        * 2. For each tick interval covered by the history, it calculates the average delivered rate (actualRate)
        *    by time-weighted integration of the delivered points.
        * 3. Updates or creates history entries for these intervals.
        */
        suspend fun updateHistoricalBasal(
            history: List<BasalHistoryPoint>,
            therapyData: TherapyData,
            insulinType: InsulinType,
            treatmentRepository: TreatmentRepository
        ) {
            if (history.isEmpty()) return
            val sortedHistory = history.sortedBy { it.timestamp }
            val now = Timestamp.now()
            val currentTick = treatmentRepository.tickForTimestamp(now)

            val firstHistoryTime = Timestamp(sortedHistory.first().timestamp)
            val firstHistoryTick = treatmentRepository.tickForTimestamp(firstHistoryTime)

            val minutesPerTick = 60 / TreatmentRepository.BASAL_TICKS_PER_HOUR
            val tickDurationMs = minutesPerTick * 60 * 1000L

            // 1. Fill gaps up to the first history point using the current profile
            val lastEntry = treatmentRepository.getLastBasalHistoryEntry()
            val historyStartTick = treatmentRepository.tickForTimestamp(now.minus(treatmentRepository.historySize))
            val fillStartTick = if (lastEntry != null) maxOf(lastEntry.startTick + 1, historyStartTick) else historyStartTick

            if (fillStartTick < firstHistoryTick) {
                for (tick in fillStartTick until firstHistoryTick) {
                    val tickTimestamp = Timestamp(tick.toLong() * tickDurationMs)
                    val rate = therapyData.basalBlocks.getAmountForMinute(tickTimestamp.minutesSinceMidnight())
                    treatmentRepository.updateInsulinApplication(
                        InsulinApplication(
                            timestamp = tickTimestamp,
                            amount = rate,
                            scheduledAmount = rate,
                            insulinType = insulinType,
                            origin = InsulinOrigin.Pump,
                            reason = "Basal"
                        )
                    )
                }
            }

            // 2. Process each tick covered by the history and update actual rates
            for (tick in firstHistoryTick..currentTick) {
                val tickStartMs = tick.toLong() * tickDurationMs
                val tickEndMs = tickStartMs + tickDurationMs
                val tickTimestamp = Timestamp(tickStartMs)
                val scheduledRate = therapyData.basalBlocks.getAmountForMinute(tickTimestamp.minutesSinceMidnight())

                // Determine actual rate through integration
                var actualAmount = 0.0
                var currentCursor = tickStartMs
                var activeRate = sortedHistory.lastOrNull { it.timestamp <= tickStartMs }?.unitsPerHour ?: scheduledRate

                val pointsInTick = sortedHistory.filter { it.timestamp > tickStartMs && it.timestamp < tickEndMs }
                for (point in pointsInTick) {
                    val durationHours = (point.timestamp - currentCursor) / 3600000.0
                    actualAmount += activeRate * durationHours
                    activeRate = point.unitsPerHour
                    currentCursor = point.timestamp
                }
                val finalDurationHours = (tickEndMs - currentCursor) / 3600000.0
                actualAmount += activeRate * finalDurationHours
                val averageActualRate = actualAmount / (tickDurationMs / 3600000.0)

                val existing = treatmentRepository.getBasalHistoryEntry(tick)
                if (existing != null) {
                    treatmentRepository.updateInsulinApplication(
                        existing.copy(
                            scheduledAmount = scheduledRate,
                            amount = averageActualRate
                        )
                    )
                } else {
                    treatmentRepository.updateInsulinApplication(
                        InsulinApplication(
                            timestamp = tickTimestamp,
                            amount = averageActualRate,
                            scheduledAmount = scheduledRate,
                            insulinType = insulinType,
                            origin = InsulinOrigin.Pump,
                            reason = "Basal"
                        )
                    )
                }
            }
        }

        suspend fun updatePumpBolusHistory(
            bolusHistory: List<BolusHistoryPoint>,
            insulinType: InsulinType,
            treatmentRepository: TreatmentRepository
        ) {
            treatmentRepository.clearInsulinApplicationsByOrigin(InsulinOrigin.Pump)
            bolusHistory.forEach { point ->
                treatmentRepository.addInsulinApplication(
                    InsulinApplication(
                        id = ID_UNDEFINED,
                        timestamp = Timestamp(point.timestamp),
                        amount = point.amount,
                        insulinType = insulinType,
                        origin = InsulinOrigin.Pump,
                        reason = "Meal-Bolus"
                    )
                )
            }
        }

        suspend fun calculateBolusesAndBasalDeviations(treatmentRepository: TreatmentRepository): List<InsulinApplication> {
            val boluses = treatmentRepository.getInsulinApplications()
                .filter { it.reason != "Basal" }
            val basalDeviations = treatmentRepository.getBasalHistory()
                .map { historyEntry ->
                    val deviationUph = historyEntry.amount - historyEntry.scheduledAmount
                    val amount = deviationUph / TreatmentRepository.BASAL_TICKS_PER_HOUR
                    InsulinApplication(
                        id = ID_UNDEFINED,
                        timestamp = historyEntry.timestamp,
                        amount = amount,
                        scheduledAmount = amount,
                        insulinType = historyEntry.insulinType,
                        origin = InsulinOrigin.Pump,
                        reason = "Basal"
                    )
                }
                .filter { abs(it.amount) > INSULIN_EPSILON }
            return boluses + basalDeviations
        }
    }
}