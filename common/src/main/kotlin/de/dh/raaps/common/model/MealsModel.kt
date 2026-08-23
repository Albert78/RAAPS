package de.dh.raaps.common.model

import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import java.util.UUID
import kotlin.math.abs

/**
 * Curve parameters for a carbs component activity curve.
 * For calculations, we model the carbs activity of a meal as the sum of multiple
 * component activity curves.
 */
data class CarbCurveComponentData(
    /**
     * Weight of this component, the sum of all weights for a meal must be 1.
     */
    val weight: Int,

    /**
     * The peak of the carb activity for this component.
     */
    val peakMinutes: Minutes,
) {
    init {
        require(weight >= 0.0) { "weight must be >= 0" }
    }
}

/**
 * Represents a meal absorption profile.
 */
data class MealType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val components: List<CarbCurveComponentData>,

    /**
     * Carbs absorption time; maximum duration when the carbs are completely absorbed.
     */
    val cat: Minutes
) {
    init {
        require(components.isNotEmpty()) {
            "MealType must contain at least one component"
        }

        val sum = components.sumOf { it.weight }
        require(abs(sum - 100.0) < 1e-6) {
            "Component weights must sum to 100%"
        }
    }
}

/**
 * Represents a meal event, which can be either in the past or planned for the future.
 *
 * We intentionally use a single model and do not strictly distinguish between historical
 * and future meals for several reasons:
 * 1) A meal spans a duration of time (consumption and absorption), making a hard split between
 * "past" and "future" ambiguous.
 * 2) In multi-dose scenarios, some insulin applications for a meal might already be administered
 * while others remain in a scheduled state.
 * 3) It provides a unified experience, allowing users to edit both recently consumed and upcoming
 * meals seamlessly.
 */
data class MealEntry(
    var id: Long = ID_UNDEFINED,
    val timestamp: Timestamp,
    val carbGrams: Double,
    val mealType: MealType,
    val description: String = ""
)