package de.dh.raaps.ui.common.treatmentlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.LockResult
import de.dh.raaps.core.aps.TreatmentLock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

enum class LockStatus {
    Loading, Busy, Acquired, Error
}

data class TreatmentLockUiState(
    val status: LockStatus = LockStatus.Loading,
    val busyOwner: String? = null,
    val acquiredLock: TreatmentLock? = null
)

class TreatmentLockViewModel(
    private val tag: String,
    private val registry: SystemRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(TreatmentLockUiState())
    val uiState: StateFlow<TreatmentLockUiState> = _uiState.asStateFlow()

    private val therapyManager = registry.therapyManager

    init {
        acquireLock()
    }

    private fun acquireLock() {
        viewModelScope.launch {
            var retryAttempt = 0
            while (retryAttempt < 2) {
                val result = therapyManager.tryAcquire(tag) { treatmentLock ->
                    _uiState.update { 
                        it.copy(
                            status = LockStatus.Acquired,
                            acquiredLock = treatmentLock,
                            busyOwner = null
                        ) 
                    }
                    try {
                        awaitCancellation()
                    } finally {
                        _uiState.update { it.copy(status = LockStatus.Loading, acquiredLock = null) }
                    }
                }

                if (result is LockResult.Busy) {
                    if (retryAttempt == 0) {
                        _uiState.update { it.copy(status = LockStatus.Busy, busyOwner = result.owner) }
                        delay(3.seconds)
                        retryAttempt++
                    } else {
                        _uiState.update { it.copy(status = LockStatus.Error, busyOwner = result.owner) }
                        break
                    }
                } else {
                    break
                }
            }
        }
    }

    companion object {
        class Factory(private val tag: String, private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return TreatmentLockViewModel(tag, registry) as T
            }
        }
    }
}