package de.dh.raaps.plugin.simbody

import android.app.Application
import de.dh.raaps.common.model.GlucoseSource
import de.dh.raaps.common.model.InsulinPump
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager
import de.dh.raaps.common.model.data.BgReading
import de.dh.raaps.common.model.data.Tick
import de.dh.raaps.common.model.data.TickHandler
import de.dh.raaps.common.model.data.TickPriority
import de.dh.raaps.common.model.data.TimeService
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.plugin.simbody.repository.db.SimBodyDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A plugin which provides a glucose source and a pump instance which are connected to a
 * "virtual human body", simulating the influence of meals and insulin.
 */
class SimBodyPlugin(
    val application: Application,
    val wakeService: SystemWakeService,
    val timeService: TimeService
) : Plugin, TickHandler {
    private val database = SimBodyDatabase.getInstance(application)
    val bodyModel = BodyModel(DEFAULT_SIM_BODY_PROFILE, database.impactDao())
    val pumpDevice = SimBodyPumpDevice(bodyModel, DEFAULT_SIM_INSULIN_PROFILE, database.pumpDao())

    private val _glucoseReadings = MutableSharedFlow<BgReading>(
        replay = 1,
        extraBufferCapacity = 1
    )

    private val heartbeat = SimBodyHeartbeat(
        wakeService = wakeService,
        bodyModel = bodyModel,
        onBgReading = { _glucoseReadings.tryEmit(it) }
    )

    override val name: String = "Sim Body Plugin"
    override val neededPermissions: Collection<String> = emptyList()

    override fun initialize(pluginManager: PluginManager) {
        bodyModel.loadState()
        pumpDevice.loadState()
        timeService.registerTickHandler(TickPriority.PRE_CORE, this, "SimBody")
        heartbeat.start()
    }

    override suspend fun onTick(tick: Tick) {
        val timestamp = timeService.timeline.timestamp(tick)

        wakeService.acquireBusyState(WAKE_TAG)
        try {
            pumpDevice.advanceToTick(timestamp)
            bodyModel.advanceToTick(timestamp)
        } finally {
            wakeService.releaseBusyState(WAKE_TAG)
        }
    }

    fun getGlucoseSource(): GlucoseSource = SimBodyCgmSource(_glucoseReadings.asSharedFlow())
    fun getInsulinPump(): InsulinPump = SimBodyInsulinPump(pumpDevice)

    companion object {
        private const val WAKE_TAG = "SIM_BODY"
    }
}