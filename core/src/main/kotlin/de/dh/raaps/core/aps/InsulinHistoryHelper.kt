package de.dh.raaps.core.aps

import de.dh.raaps.common.model.BasalHistoryPoint
import de.dh.raaps.common.model.BolusHistoryPoint
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.INSULIN_EPSILON
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.TreatmentRepository

class InsulinHistoryHelper {
    companion object {
        /**
        * Updates the historical insulin applications using a list of delivered basal points from the pump.
        *
        * This method records actual deliveries from the pump as [InsulinApplication] entries.
        * Since the algorithm intends to set the pump's basal rate to zero and deliver insulin via boluses,
        * any delivery reported by the pump's basal history is recorded as an absolute insulin application.
        */
        suspend fun updateHistoricalBasal(
            history: List<BasalHistoryPoint>,
            insulinType: InsulinType,
            treatmentRepository: TreatmentRepository
        ) {
            if (history.isEmpty()) return
            val sortedHistory = history.sortedBy { it.timestamp }
            val now = Timestamp.now()

            val firstHistoryTime = Timestamp(sortedHistory.first().timestamp)
            val firstHistoryTick = treatmentRepository.tickForTimestamp(firstHistoryTime)
            val currentTick = treatmentRepository.tickForTimestamp(now)

            val minutesPerTick = 60 / TreatmentRepository.BASAL_TICKS_PER_HOUR
            val tickDurationMs = minutesPerTick * 60 * 1000L

            // Process each tick covered by the history and update actual deliveries
            for (tick in firstHistoryTick..currentTick) {
                val tickStartMs = tick.toLong() * tickDurationMs
                val tickEndMs = tickStartMs + tickDurationMs
                val tickTimestamp = Timestamp(tickStartMs)

                // Determine actual amount delivered in this tick through integration
                var actualAmount = 0.0
                var currentCursor = tickStartMs
                var activeRate = sortedHistory.lastOrNull { it.timestamp <= tickStartMs }?.unitsPerHour ?: 0.0

                val pointsInTick = sortedHistory.filter { it.timestamp > tickStartMs && it.timestamp < tickEndMs }
                for (point in pointsInTick) {
                    val durationHours = (point.timestamp - currentCursor) / 3600000.0
                    actualAmount += activeRate * durationHours
                    activeRate = point.unitsPerHour
                    currentCursor = point.timestamp
                }
                val finalDurationHours = (tickEndMs - currentCursor) / 3600000.0
                actualAmount += activeRate * finalDurationHours

                if (actualAmount > INSULIN_EPSILON) {
                    treatmentRepository.addInsulinApplication(
                        InsulinApplication(
                            timestamp = tickTimestamp,
                            amount = actualAmount,
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
            treatmentRepository.clearBolusesByOrigin(InsulinOrigin.Pump)
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

        /**
         * Returns all insulin applications from the repository.
         */
        fun getAllInsulinApplications(treatmentRepository: TreatmentRepository): List<InsulinApplication> {
            return treatmentRepository.getInsulinApplications()
        }
    }
}