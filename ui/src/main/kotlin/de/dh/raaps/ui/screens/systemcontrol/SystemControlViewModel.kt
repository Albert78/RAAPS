package de.dh.raaps.ui.screens.systemcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.dh.raaps.common.model.InsulinPumpStatus
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.BgReadingsInterval
import de.dh.raaps.common.model.data.GlucoseUnit
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.SystemRegistry
import de.dh.raaps.core.aps.CoreInsight
import de.dh.raaps.core.pump.PumpCommand
import de.dh.raaps.core.pump.PumpJob
import de.dh.raaps.glucoseUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class SystemControlUiState(
    val coreInsights: List<CoreInsight> = emptyList(),
    val glucoseSourceName: String? = null,
    val sensorTypeName: String? = null,
    val readingsInterval: BgReadingsInterval? = null,
    val lastBgReading: BgReading? = null,
    val nextPredictedTimestamp: Timestamp = Timestamp.INVALID,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,

    val cgmPluginUiProvider: CgmPluginUiProvider? = null,
    val pumpPluginUiProvider: PumpPluginUiProvider? = null,

    // Pump Subsystem
    val pumpConnected: Boolean = false,
    val pumpModel: String? = null,
    val pumpStatus: InsulinPumpStatus? = null,
    val lastPumpConnection: Timestamp = Timestamp.INVALID,
    val pendingPumpJobs: List<PumpJob> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SystemControlViewModel(
    systemRegistry: SystemRegistry
) : ViewModel() {
    private val systemMetricsRepository = systemRegistry.systemMetricsRepository
    private val glucoseRepository = systemRegistry.glucoseRepository
    private val glucoseSourceManager = systemRegistry.glucoseSourceManager
    private val appPreferencesRepository = systemRegistry.appPreferencesRepository
    private val pumpManager = systemRegistry.pumpManager

    private val glucoseInfo = combine(
        glucoseSourceManager.activeGlucoseSource,
        glucoseRepository.currentBg,
        appPreferencesRepository.cachedPreferences
    ) { source, currentBg, preferences ->
        GlucoseUiData(
            sourceName = source?.name,
            sensorTypeName = source?.getSensorTypeName(),
            readingsInterval = source?.readingsInterval,
            lastBgReading = currentBg,
            nextPredictedTimestamp = glucoseSourceManager.predictNextValueTimestamp(),
            glucoseUnit = preferences.glucoseUnit,
            pluginUiProvider = source as? CgmPluginUiProvider
        )
    }

    private val pumpInfo = pumpManager.activeInsulinPump.flatMapLatest { pump ->
        if (pump == null) {
            flowOf(PumpUiData())
        } else {
            val coordinator = pumpManager.pumpCoordinator
            combine(
                pump.isConnected,
                pump.hardwareInformation,
                pump.pumpStatus,
                coordinator?.pendingJobs ?: flowOf(emptyList()),
                coordinator?.lastConnectionTime ?: flowOf(Timestamp.INVALID)
            ) { connected, hardware, status, jobs, lastConn ->
                PumpUiData(connected, hardware?.model, status, lastConn, jobs, pump as? PumpPluginUiProvider)
            }
        }
    }

    val uiState: StateFlow<SystemControlUiState> = combine(
        systemMetricsRepository.observeInsights(),
        glucoseInfo,
        pumpInfo
    ) { insights, gInfo, pInfo ->
        SystemControlUiState(
            coreInsights = insights,
            glucoseSourceName = gInfo.sourceName,
            sensorTypeName = gInfo.sensorTypeName,
            readingsInterval = gInfo.readingsInterval,
            lastBgReading = gInfo.lastBgReading,
            nextPredictedTimestamp = gInfo.nextPredictedTimestamp,
            glucoseUnit = gInfo.glucoseUnit,
            cgmPluginUiProvider = gInfo.pluginUiProvider,
            pumpPluginUiProvider = pInfo.pluginUiProvider,
            pumpConnected = pInfo.connected,
            pumpModel = pInfo.model,
            pumpStatus = pInfo.status,
            lastPumpConnection = pInfo.lastConnection,
            pendingPumpJobs = pInfo.jobs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SystemControlUiState()
    )

    fun cancelPumpJob(jobId: String) {
        pumpManager.cancelJobs { it.id == jobId }
    }

    fun refreshPumpStatus() {
        pumpManager.issueCommand(PumpCommand.RefreshStatus)
    }

    private data class GlucoseUiData(
        val sourceName: String?,
        val sensorTypeName: String?,
        val readingsInterval: BgReadingsInterval?,
        val lastBgReading: BgReading?,
        val nextPredictedTimestamp: Timestamp,
        val glucoseUnit: GlucoseUnit,
        val pluginUiProvider: CgmPluginUiProvider?
    )

    private data class PumpUiData(
        val connected: Boolean = false,
        val model: String? = null,
        val status: InsulinPumpStatus? = null,
        val lastConnection: Timestamp = Timestamp.INVALID,
        val jobs: List<PumpJob> = emptyList(),
        val pluginUiProvider: PumpPluginUiProvider? = null
    )

    companion object {
        class Factory(private val registry: SystemRegistry) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return SystemControlViewModel(registry) as T
            }
        }
    }
}