package de.dh.raaps.model.test

import de.dh.raaps.common.model.data.BgValue
import kotlin.math.max

class NeededForPast(
    // 4 Ticks in der Vergangenheit für 3 Slopes, vergleich jeweils mit den BGIs für avgDeviation
    // Einige Stunden in die Vergangenheit für Anzeige
    var bg: BgValue,

    // 3 Ticks in der Vergangenheit für avgDeviation; So viele in der Zukunft bis eventualTime
    var bgi: BgDelta
)

data class TargetBlock(
    val low: BgValue,
    val high: BgValue
)

weiter: Das in den neuen core einbauen
fun newBgPresent(bg) {
    // Dann noch aktuelle Abweichung des prognostizierten BGI von der tatsächlichen Steigung als "deviation" berechnen. Um Fehler rauszurechnen, Mittelwert der Steigung der letzten 3 Ticks bilden.
    val avgCurrentDeviation = calcDeviation(timespan = 30.min) // Jeweils Slope mit BGI vergleichen -> Deviation. Davon den Mittelwert.
    // Deviation = was wir aktuell an Fehler zur BGI-Vorhersage nach dem statischen Modell haben
    // Annahme: Diese Deviation haben wir auch die nächsten Ticks, schleicht sich aus

    val predictionTime = prediction.maxPredictionTime

    val targetBg: TargetBlock = ...

    prediction.updateAllBgPredictions(currentBG, avgCurrentDeviation)

    // 1. Ziel: Aus aktuellem oder anstehenden Low rauskommen durch frühzeitiges Absenken von Basal-Wert
    // Nächste Unterschreitung des Min-Wertes finden mit aktuellen Werten; Minimum finden mit Zeit
    val nextMinStart: PredictionPoint = prediction.findNext { it.bg < targetBg.low }
    val nextMin: PredictionPoint = prediction.findNextBgMin(startAt = nextMinStart.timestamp)
    // Korrektur durch Temp-Basal absenken von Minimum zurückgerechnet, um Error weg zu bekommen
    val startLowTemp = TODO: vom Minimum zurückgerechnet
    // TODO: Plane Low temp ein
    prediction.setLowTemp(from = startLowTemp, to = nextMin)
    prediction.updateBGIPredictions(startAt = startLowTemp)

    // 2. Ziel: Nächstes, anstehendes Hoch korrigieren durch frühzeitige Insulingabe, ohne danach Low abzurutschen
    // Nächstes Hoch finden mit Zeit, dahinter nächstes Min finden mit Zeit
    val nextMaximum: PredictionPoint = prediction.findNextBgMax()
    val minAfterMax: PredictionPoint = prediction.findNextBgMin(startAt = nextHigh.timestamp)
    val tryCorrect = nextMaximum.bg - targetBg.target
    if (tryCorrect > 0) {
        // Versuche, Blutzucker um tryCorrect zu reduzieren

        // Versuche, bestmögliche Insulinmengen und -Zeitpunkte zur Korrektur finden mit begrenztem Rechenaufwand
        // Prinzipielle Heuristik: Wir versuchen, das Insulin immer möglichst früh zu geben, um den BZ tief zu halten.
        // Kann im schlimmsten Fall zu größeren Steigungen führen.

        // Insulin-Korrekturmenge: Korrektur um tryCorrect, beschränkt durch lowBuffer, damit wir nicht danach durch unser IOB nach unten abfallen
        val lowBuffer = minAfterMax.bg - targetBg.low // Wir haben so viel Puffer für die Korrektur
        val maxCorrection = min(tryCorrect, lowBuffer)
        val maxInsulinAmount = maxCorrection / isf
        var insulinAmount = maxInsulinAmount
        var insulinTime

        // Insulin-Korrekturzeitpunkt(e) hängen von voraussichtlicher Steigung des BZ ab
        val firstHighPoint: PredictionPoint = prediction.findNext { it.bg > targetBg.high } // Punkt, wo Zielbereich überschritten wird
        if (nextMaximum.timestamp - firstHighPoint.timestamp > insulinPeakTime) {
            // Langsamer BZ-Anstieg, teile Insulingabe in mehrere Zeitpunkte auf
            // Versuche, einen Teil des Insulins möglichs früh zu geben und zwar so, dass der Peak bei firstHighPoint liegt und die Wirkung uns nicht unter targetBg.low bringt
            insulinTime = max(firstHighPoint - insulinPeakTime, now)
            Simulation: Starte bei insulinTime, gehe bis nextMaximum:
            - Berechne für jeden Tick iob(insulinPart, timestamp=firstHighPoint - insulinPeak). Falls iob < low, setze insulinAmount auf den Anteil, so dass iob = low
        } else {
            // Insulingabe mit Insulin-Peakzeit vor hoch
            insulinTime = max(nextMaximum - insulinPeakTime, now)
        }
        // TODO: Plane Insulingabe ein: insulinAmount bei insulinTime
        prediction.updateBGIPredictions(startAt = insulinTime)
    }
}