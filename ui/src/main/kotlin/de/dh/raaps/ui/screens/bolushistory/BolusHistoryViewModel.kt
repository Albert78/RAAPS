package de.dh.raaps.ui.screens.bolushistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MEAL_EDIT_THRESHOLD_HOURS
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BolusHistoryUiState(
    val bolusEntries: List<InsulinApplication> = emptyList(),
    val insulinTypes: List<InsulinType> = emptyList(),
    val defaultInsulinType: InsulinType? = null,
    val editThresholdHours: Int = MEAL_EDIT_THRESHOLD_HOURS
)

class BolusHistoryViewModel(
    private val registry: SystemRegistry
) : ViewModel() {

    private val treatmentRepository = registry.treatmentRepository
    private val therapyManager = registry.therapyManager

    private val _insulinTypes = MutableStateFlow<List<InsulinType>>(emptyList())
    private val _defaultInsulinType = MutableStateFlow<InsulinType?>(null)

    init {
        viewModelScope.launch {
            _insulinTypes.value = treatmentRepository.getAllInsulinTypes()
            _defaultInsulinType.value = therapyManager.getPumpInsulinType()
        }
    }

    val uiState: StateFlow<BolusHistoryUiState> = combine(
        treatmentRepository.observeInsulinApplications(),
        _insulinTypes,
        _defaultInsulinType
    ) { entries, types, defaultType ->
        BolusHistoryUiState(
            bolusEntries = entries.sortedByDescending { it.timestamp },
            insulinTypes = types,
            defaultInsulinType = defaultType,
            editThresholdHours = MEAL_EDIT_THRESHOLD_HOURS
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BolusHistoryUiState()
        )

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BolusHistoryViewModel(registry) as T
            }
        }
    }

    fun addManualBolus(amount: Double, insulinType: InsulinType) {
        viewModelScope.launch {
            val application = InsulinApplication(
                timestamp = Timestamp.now(),
                amount = amount,
                insulinType = insulinType,
                origin = InsulinOrigin.Manual,
                provisional = false
            )
            treatmentRepository.addInsulinApplication(application)
        }
    }

    fun updateManualBolus(application: InsulinApplication, newAmount: Double, newType: InsulinType) {
        viewModelScope.launch {
            val updated = application.copy(
                amount = newAmount,
                insulinType = newType
            )
            treatmentRepository.updateInsulinApplication(updated)
        }
    }

    fun deleteBolus(application: InsulinApplication) {
        viewModelScope.launch {
            treatmentRepository.removeInsulinApplication(application)
        }
    }
}