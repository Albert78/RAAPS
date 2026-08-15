package de.dh.raaps.common.model.calculation

import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class BolusCalculatorTest {

    @Test
    fun testCalculateSuggestedSea() {
        assertEquals(0, BolusCalculator.calculateSuggestedSea(100, 100))
        assertEquals(0, BolusCalculator.calculateSuggestedSea(80, 100))
        assertEquals(5, BolusCalculator.calculateSuggestedSea(120, 100))
        assertEquals(25, BolusCalculator.calculateSuggestedSea(200, 100))
        assertEquals(45, BolusCalculator.calculateSuggestedSea(350, 100))
        assertEquals(0, BolusCalculator.calculateSuggestedSea(null, 100))
    }

    @Test
    fun testCalculateBolusParts_TargetBg() {
        val result = BolusCalculator.calculateBolusParts(
            carbsKe = 5.0, // 50g
            cr = 10.0,     // 10g/U
            isf = 50,      // 50mg/dL/U
            currentBg = 100,
            targetBg = 100,
            lowThreshold = 70,
            iob = InsulinAmount.ZERO,
            cob = 0.0
        )

        assertEquals(5.0, result.mealPart.iu, 0.01)
        assertEquals(0.0, result.correctionPart.iu, 0.01)
        assertEquals(5.0, result.totalProposed.iu, 0.01)
    }

    @Test
    fun testCalculateBolusParts_HighBg() {
        val result = BolusCalculator.calculateBolusParts(
            carbsKe = 0.0,
            cr = 10.0,
            isf = 50,
            currentBg = 200, // 100 above target
            targetBg = 100,
            lowThreshold = 70,
            iob = InsulinAmount.ZERO,
            cob = 0.0
        )

        assertEquals(0.0, result.mealPart.iu, 0.01)
        assertEquals(2.0, result.correctionPart.iu, 0.01)
        assertEquals(2.0, result.totalProposed.iu, 0.01)
    }

    @Test
    fun testCalculateBolusParts_LowBg() {
        val result = BolusCalculator.calculateBolusParts(
            carbsKe = 5.0,
            cr = 10.0,
            isf = 50,
            currentBg = 65, // Below low threshold
            targetBg = 100,
            lowThreshold = 70,
            iob = InsulinAmount.ZERO,
            cob = 0.0
        )

        assertEquals(0.0, result.totalProposed.iu, 0.01)
        assertEquals(5.0, result.mealPart.iu, 0.01)
        // Correction part is negative ( (65-100)/50 = -0.7 )
        assertEquals(-0.7, result.correctionPart.iu, 0.01)
    }

    @Test
    fun testDistributeInsulinPlan_SingleComponent() {
        val mealType = MealType(
            name = "Fast",
            components = listOf(CarbCurveComponentData(weight = 100, peakMinutes = Minutes(25))),
            cat = Minutes(90)
        )
        val now = Timestamp.now()
        val plan = BolusCalculator.distributeInsulinPlan(
            manualBolus = InsulinAmount(5.0),
            correctionPart = InsulinAmount.ZERO,
            selectedMealType = mealType,
            currentBg = 100,
            lowThreshold = 70,
            now = now
        )

        assertEquals(1, plan.size)
        assertEquals(5.0, plan[0].amount.iu, 0.01)
        assertEquals(now.ms, plan[0].timestamp.ms)
    }

    @Test
    fun testDistributeInsulinPlan_MultiComponentWithCorrection() {
        val mealType = MealType(
            name = "Mixed",
            components = listOf(
                CarbCurveComponentData(weight = 60, peakMinutes = Minutes(25)),
                CarbCurveComponentData(weight = 40, peakMinutes = Minutes(120))
            ),
            cat = Minutes(240)
        )
        val now = Timestamp.now()
        // 5.0 units total, 1.0 is correction
        // Meal part is 4.0.
        // Component 1 (60%): 4.0 * 0.6 + 1.0 = 3.4
        // Component 2 (40%): 4.0 * 0.4 = 1.6
        val plan = BolusCalculator.distributeInsulinPlan(
            manualBolus = InsulinAmount(5.0),
            correctionPart = InsulinAmount(1.0),
            selectedMealType = mealType,
            currentBg = 100,
            lowThreshold = 70,
            now = now
        )

        assertEquals(2, plan.size)
        assertEquals(3.4, plan[0].amount.iu, 0.01)
        assertEquals(1.6, plan[1].amount.iu, 0.01)
        assertEquals(now.ms, plan[0].timestamp.ms)
        assertEquals(now.ms + 120 * 60000L, plan[1].timestamp.ms)
    }

    @Test
    fun testDistributeInsulinPlan_NegativeMealPartRebalancing() {
        val mealType = MealType(
            name = "Mixed",
            components = listOf(
                CarbCurveComponentData(weight = 50, peakMinutes = Minutes(25)),
                CarbCurveComponentData(weight = 50, peakMinutes = Minutes(120))
            ),
            cat = Minutes(240)
        )
        // 1.0 total, but -2.0 correction (very low BG)
        // Rest to distribute = 1.0 - (-2.0) = 3.0? No, that's not how it works.
        // totalAmount = 1.0
        // correction = -2.0
        // restToDistribute = 1.0 - (-2.0) = 3.0
        // raw[0] = 3.0 * 0.5 + (-2.0) = 1.5 - 2.0 = -0.5
        // raw[1] = 3.0 * 0.5 = 1.5
        // Re-balance raw[0] forward: raw[1] becomes 1.5 + (-0.5) = 1.0, raw[0] becomes 0

        val plan = BolusCalculator.distributeInsulinPlan(
            manualBolus = InsulinAmount(1.0),
            correctionPart = InsulinAmount(-2.0),
            selectedMealType = mealType,
            currentBg = 60,
            lowThreshold = 70,
            now = Timestamp.now()
        )

        assertEquals(1, plan.size)
        assertEquals(1.0, plan[0].amount.iu, 0.01)
        // Since it's Component 2, it should have the offset 120 + 15 (hypo) = 135
        assertEquals(135, plan[0].offsetMinutes)
    }
}
