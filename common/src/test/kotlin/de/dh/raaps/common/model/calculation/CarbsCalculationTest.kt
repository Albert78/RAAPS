package de.dh.raaps.common.model.calculation

import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbsCalculationTest {

    @Test
    fun testCobRemainsPositive() {
        val intervalSize = Minutes(5)
        val cache = SampledCarbsCalculationCache(intervalSize)
        
        val mealType = MealType(
            name = "Test Fast",
            components = listOf(
                CarbCurveComponentData(weight = 100, peakMinutes = Minutes(25))
            ),
            cat = Minutes(90)
        )
        
        val carbGrams = 26.0
        
        // After 2 intervals (10 minutes)
        val cob = cache.remainingCarbs(carbGrams, mealType, 2)
        
        println("COB after 10 mins: $cob g")
        
        assertTrue("COB should be positive, but was $cob", cob > 0)
        assertTrue("COB should be less than or equal to initial carbs, but was $cob", cob <= carbGrams)
    }
}