package de.dh.raaps.model

import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Tick

class ApsAlgorithmImpl(
    val core: Core
): ApsAlgorithm {
    override fun isRecalculationNecessary(
        newBgReading: BgReading,
        tick: Tick
    ): Boolean {
        val tickState = core.rollingHistory.tryGetApsTickState(tick)
        return tickState?.expectedBgToleranceRange?.let {
            toleranceRange -> newBgReading.value.mgdl in toleranceRange.first.mgdl..toleranceRange.second.mgdl
        } ?: false
    }

    override fun recalculate(fromCurrentTick: Tick) {
        // TODO
        // IOB, COB, BG kommen aus der Vergangenheit
        // BG Predictions berechnen für verschiedene Szenarien
        // Bewerten aufgrund von Aggressivitätseinstellungen

        // Actions: Insulin. Aber wie mitteilen? Wir können eine optimale Kurve berechnen, das kann die Pumpe aber ggf nicht.
        // Einstellung des Benutzers:
        // - SMB nutzen? Vorteil: Bessere Steuerung. Nachteil: APS muss immer da sein, höhere Rechenlast.
        // - Pumpen-EB bzw. Dual-Bolus nutzen: Es passiert mehr in der Pumpe. Hierzu benötigen wir Zugriff auf die Pumpen-Fähigkeiten
        // Ausgabe dann: Insulinplan. Das Pumpenplugin muss das umsetzen.
    }
}