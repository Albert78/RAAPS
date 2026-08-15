package de.dh.raaps.ui.screens.mealbolus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.DEFAULT_CR_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.convertToInsulinAmountFromBgDelta
import de.dh.raaps.common.model.convertToInsulinAmountFromCarbs
import de.dh.raaps.common.model.data.BgDelta
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.DeferredBolus
import de.dh.raaps.core.aps.LockResult
import de.dh.raaps.core.aps.TreatmentLock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.time.Duration.Companion.seconds

data class PlannedInsulin(
    val amount: InsulinAmount,
    val timestamp: Timestamp,
    val offsetMinutes: Int = 0,
    val description: String = "",
    val isUserModified: Boolean = false
)

data class MealBolusUiState(
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val originalMealTimestamp: Timestamp? = null,
    val originalMealId: Long = ID_UNDEFINED,
    val mealTimestamp: Timestamp = Timestamp.now(),
    val seaMinutes: Int = 0,
    val carbsKe: Double = 0.0,
    val mealTypes: List<MealType> = emptyList(),
    val selectedMealType: MealType? = null,
    val currentBg: Int? = null,
    val targetBg: Int = 100,
    val lowThreshold: Int = 70,
    val isf: Int = 50,
    val cr: Double = 10.0,
    val iob: InsulinAmount = InsulinAmount.ZERO,
    val cob: Double = 0.0,
    val mealPart: InsulinAmount = InsulinAmount.ZERO,
    val correctionPart: InsulinAmount = InsulinAmount.ZERO,
    val iobPart: InsulinAmount = InsulinAmount.ZERO,
    val cobPart: InsulinAmount = InsulinAmount.ZERO,
    val proposedBolus: InsulinAmount = InsulinAmount.ZERO,
    val manualBolus: InsulinAmount = InsulinAmount.ZERO,
    val insulinPlan: List<PlannedInsulin> = emptyList(),
    val isInsulinPlanExpanded: Boolean = false,
    val isAutomaticMode: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLockAcquired: Boolean = false,
    val isBusy: Boolean = false,
    val lockBusyOwner: String? = null,
    val lockError: Boolean = false,
)

class MealBolusViewModel(
    private val systemRegistry: SystemRegistry,
    private val mealId: Long? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealBolusUiState())
    val uiState: StateFlow<MealBolusUiState> = _uiState.asStateFlow()

    private val therapyManager = systemRegistry.therapyManager
    private val glucoseRepository = systemRegistry.glucoseRepository
    private val treatmentRepository = systemRegistry.treatmentRepository
    private val calculationModel = systemRegistry.carbsInsulinCalculationModel

    private var treatmentLock: TreatmentLock? = null

    init {
        acquireLock()
        viewModelScope.launch {
            val now = Timestamp.now()
            val therapySettings = therapyManager.getCurrentTherapySettings()
            val isf = therapyManager.getIsfFactor(now).mgdl.toInt()
            val cr = therapyManager.getCrFactor(now)
            val bgSettings = therapyManager.getBgSettings()
            val mealTypes = treatmentRepository.getAllMealTypes()

            val lastReading = glucoseRepository.loadBgReadings(now - Minutes(30)).lastOrNull()
            val currentBg = lastReading?.value?.mgdl?.toInt()

            val historyLimit = now.minusHours(25)
            val insulinHistory = treatmentRepository.getInsulinApplications(from = historyLimit)
            val mealsHistory = treatmentRepository.getMeals(from = historyLimit)

            val iob = calculationModel.iob(insulinHistory, now, therapySettings.insulinProfile.dia, therapySettings.insulinProfile.peak)
            val cob = calculationModel.cob(mealsHistory, now)

            val existingMeal = mealId?.let { treatmentRepository.getMeal(it) }
            val initialMealTimestamp = existingMeal?.timestamp ?: now
            val suggestedSea = calculateSuggestedSea(currentBg, bgSettings.first.mgdl.toInt())
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditMode = existingMeal != null,
                    originalMealTimestamp = existingMeal?.timestamp,
                    originalMealId = existingMeal?.id ?: ID_UNDEFINED,
                    mealTimestamp = if (existingMeal == null) now + Minutes(suggestedSea.toShort()) else initialMealTimestamp,
                    seaMinutes = if (existingMeal == null) suggestedSea else 0,
                    carbsKe = existingMeal?.let { meal -> meal.carbGrams / 10.0 } ?: 0.0,
                    mealTypes = mealTypes,
                    selectedMealType = existingMeal?.mealType,
                    currentBg = currentBg,
                    targetBg = bgSettings.first.mgdl.toInt(),
                    lowThreshold = bgSettings.second.mgdl.toInt(),
                    isf = if (isf == 0) DEFAULT_ISF_MGDL_PER_UNIT.toInt() else isf,
                    cr = if (cr == 0.0) DEFAULT_CR_GRAM_PER_UNIT else cr,
                    iob = iob,
                    cob = cob
                )
            }
            calculateBolus()
            startTicker()
        }

        viewModelScope.launch {
            systemRegistry.systemManager.apsMode.collect { mode ->
                _uiState.update { it.copy(isAutomaticMode = mode == ApsMode.AutoCorrection) }
                calculateBolus()
            }
        }
    }

    private fun acquireLock() {
        viewModelScope.launch {
            var retryAttempt = 0
            while (retryAttempt < 2) {
                val result = therapyManager.tryAcquire("MealBolusScreen") { treatmentLock ->
                    this@MealBolusViewModel.treatmentLock = treatmentLock
                    _uiState.update { it.copy(isLockAcquired = true, isBusy = false) }
                    try {
                        awaitCancellation()
                    } finally {
                        this@MealBolusViewModel.treatmentLock = null
                    }
                }

                if (result is LockResult.Busy) {
                    if (retryAttempt == 0) {
                        _uiState.update { it.copy(isBusy = true, lockBusyOwner = result.owner) }
                        delay(3.seconds)
                        retryAttempt++
                    } else {
                        _uiState.update { it.copy(isBusy = false, lockError = true) }
                        break
                    }
                } else {
                    break
                }
            }
        }
    }

    fun onCarbsChange(ke: Double) {
        _uiState.update { it.copy(carbsKe = max(0.0, ke)) }
        calculateBolus()
    }

    fun onMealTimeChange(timestamp: Timestamp) {
        val now = Timestamp.now()
        val offset = kotlin.math.round((timestamp.ms - now.ms) / 60000.0)
        _uiState.update { it.copy(mealTimestamp = timestamp, seaMinutes = offset.toInt()) }
        calculateBolus()
    }

    fun onMealTypeChange(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
        calculateBolus()
    }

    fun onManualBolusChange(amount: Double) {
        if (_uiState.value.isEditMode) return // Prevent bolus change in edit mode
        _uiState.update { it.copy(manualBolus = InsulinAmount(amount).coerceAtLeast(InsulinAmount.ZERO)) }
        updateInsulinPlanTimes() // Re-calculate distribution if manual amount changes
    }

    fun onPlannedInsulinTimeChange(index: Int, newTimestamp: Timestamp) {
        val now = Timestamp.now()
        val offset = kotlin.math.round((newTimestamp.ms - now.ms) / 60000.0)
        _uiState.update { state ->
            val newPlan = state.insulinPlan.toMutableList()
            if (index in newPlan.indices) {
                newPlan[index] = newPlan[index].copy(
                    timestamp = newTimestamp,
                    offsetMinutes = offset.toInt(),
                    isUserModified = true
                )
            }
            state.copy(insulinPlan = newPlan)
        }
    }

    fun toggleInsulinPlanExpanded() {
        _uiState.update { it.copy(isInsulinPlanExpanded = !it.isInsulinPlanExpanded) }
    }

    private fun calculateSuggestedSea(currentBg: Int?, targetBg: Int): Int {
        if (currentBg == null) return 0
        val diff = currentBg - targetBg
        if (diff <= 0) return 0
        
        // Simple rule: 5 minutes per 20 mg/dL above target, max 45 min
        val suggested = (diff / 20) * 5
        return suggested.coerceIn(0, 45)
    }

    private fun calculateBolus() {
        val state = _uiState.value
        val carbsGrams = state.carbsKe * 10.0
        val mealPart = convertToInsulinAmountFromCarbs(carbsGrams, state.cr)

        val currentBgValue = state.currentBg ?: state.targetBg
        val bgDiff = currentBgValue - state.targetBg

        // Can be positive or negative
        val correctionPart = convertToInsulinAmountFromBgDelta(BgDelta(bgDiff.toShort()), BgDelta(state.isf.toShort()))

        val iobPart = state.iob
        val cobPart = convertToInsulinAmountFromCarbs(state.cob, state.cr)

        val total = (mealPart + correctionPart - iobPart + cobPart).coerceAtLeast(InsulinAmount.ZERO)

        // Round to 2 decimal places
        val roundedTotal = round(total.iu * 100.0) / 100.0
        var bolusAmount = InsulinAmount(roundedTotal)

        // If BG is unknown or below low threshold, no insulin is proposed
        if (state.currentBg == null || state.currentBg <= state.lowThreshold) {
            bolusAmount = InsulinAmount.ZERO
        }

        _uiState.update {
            it.copy(
                mealPart = mealPart,
                correctionPart = correctionPart,
                iobPart = iobPart,
                cobPart = cobPart,
                proposedBolus = bolusAmount,
                manualBolus = if (state.isEditMode) InsulinAmount.ZERO else bolusAmount
            )
        }
        updateInsulinPlanTimes()
    }

    private fun updateInsulinPlanTimes() {
        val state = _uiState.value
        if (state.isEditMode || state.manualBolus <= InsulinAmount.ZERO) {
            _uiState.update { it.copy(insulinPlan = emptyList()) }
            return
        }

        val totalAmount = state.manualBolus.iu
        val correction = state.correctionPart.iu
        val restToDistribute = totalAmount - correction
        val mealType = state.selectedMealType ?: return
        val now = Timestamp.now()

        val rawAmounts = DoubleArray(mealType.components.size) { i ->
            val weight = mealType.components[i].weight / 100.0
            val share = restToDistribute * weight
            if (i == 0) share + correction else share
        }

        // Re-balance negatives forward (drain deficit to next part)
        for (i in 0 until rawAmounts.size - 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i + 1] += rawAmounts[i]
                rawAmounts[i] = 0.0
            }
        }
        // Re-balance negatives backward (safety check)
        for (i in rawAmounts.size - 1 downTo 1) {
            if (rawAmounts[i] < 0) {
                rawAmounts[i - 1] += rawAmounts[i]
                rawAmounts[i] = 0.0
            }
        }

        val roundedAmounts = rawAmounts.map { round(max(0.0, it) * 100.0) / 100.0 }.toDoubleArray()

        // Final adjustment to ensure sum matches exactly the totalAmount
        val currentSum = roundedAmounts.sum()
        val targetSum = round(totalAmount * 100.0) / 100.0
        val diff = round((targetSum - currentSum) * 100.0) / 100.0

        if (abs(diff) >= 0.01) {
            val indexToAdjust = roundedAmounts.indices.maxByOrNull { roundedAmounts[it] } ?: 0
            roundedAmounts[indexToAdjust] = round((roundedAmounts[indexToAdjust] + diff) * 100.0) / 100.0
        }

        val isLowBg = state.currentBg != null && state.currentBg <= state.lowThreshold

        val newPlan = mealType.components.mapIndexed { index, component ->
            val amount = InsulinAmount(roundedAmounts[index])

            // Suggested offset:
            val suggestedOffset = if (isLowBg) 15 else 0
            val delayFromBase = if (index == 0) 0 else component.peakMinutes.value.toInt()
            val finalOffset = suggestedOffset + delayFromBase

            // Preserve user modification if possible
            val existing = state.insulinPlan.getOrNull(index)
            val offsetToUse = if (existing?.isUserModified == true) existing.offsetMinutes else finalOffset
            val finalTimestamp = now + Minutes(offsetToUse.toShort())

            PlannedInsulin(
                amount = amount,
                timestamp = finalTimestamp,
                offsetMinutes = offsetToUse,
                description = if (mealType.components.size > 1) "Teil ${index + 1} (${component.weight}%)" else "Bolus",
                isUserModified = existing?.isUserModified ?: false
            )
        }.filter { it.amount > InsulinAmount.ZERO }

        _uiState.update { it.copy(insulinPlan = newPlan) }
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val lock = treatmentLock ?: throw IllegalStateException("Action attempted without holding the lock")

                // 1. Record/Update Meal
                if (state.carbsKe > 0 && state.selectedMealType != null) {
                    val mealEntry = MealEntry(
                        id = if (state.isEditMode) state.originalMealId else ID_UNDEFINED,
                        timestamp = state.mealTimestamp,
                        carbGrams = state.carbsKe * 10.0,
                        mealType = state.selectedMealType
                    )
                    treatmentRepository.addMealEntry(mealEntry)
                    
                    // Schedule reminder
                    if (!state.isEditMode) {
                        therapyManager.scheduleMealReminder(lock, state.mealTimestamp)
                    }
                }

                // 2. Deliver Insulin Plan (only if NOT editing)
                if (!state.isEditMode && state.insulinPlan.isNotEmpty()) {
                    val now = Timestamp.now()
                    val immediateBolus = state.insulinPlan.filter { it.timestamp <= now + Minutes(1) }
                        .fold(InsulinAmount.ZERO) { acc, next -> acc + next.amount }
                    
                    val deferredBoluses = state.insulinPlan.filter { it.timestamp > now + Minutes(1) }
                        .map { DeferredBolus(id = ID_UNDEFINED, amount = it.amount, timestamp = it.timestamp) }

                    if (immediateBolus > InsulinAmount.ZERO) {
                        therapyManager.issueBolus(lock, immediateBolus)
                    }
                    
                    deferredBoluses.forEach { 
                        therapyManager.addDeferredBolus(lock, it)
                    }
                }

                _uiState.update { it.copy(isSubmitting = false) }
                onSuccess()
            } catch (_: Exception) {
                // TODO: Error handling
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                delay(5.seconds)
                val now = Timestamp.now()
                _uiState.update { state ->
                    if (state.isEditMode || state.isSubmitting) return@update state
                    
                    val updatedMealTime = now + Minutes(state.seaMinutes.toShort())
                    val updatedPlan = state.insulinPlan.map { planItem ->
                        planItem.copy(timestamp = now + Minutes(planItem.offsetMinutes.toShort()))
                    }
                    
                    state.copy(
                        mealTimestamp = updatedMealTime,
                        insulinPlan = updatedPlan
                    )
                }
            }
        }
    }

    companion object {
        class Factory(private val registry: SystemRegistry, private val mealId: Long? = null) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MealBolusViewModel(registry, mealId) as T
            }
        }
    }
}
