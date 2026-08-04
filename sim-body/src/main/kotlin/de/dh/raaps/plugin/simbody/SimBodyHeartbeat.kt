package de.dh.raaps.plugin.simbody

import android.content.Intent
import android.util.Log
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.system.SystemWakeService
import de.dh.raaps.core.system.WakeupHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Handles the periodic wakeup of the SimBody simulation using the central [SystemWakeService].
 * This ensures the simulation progresses even if the app is in the background or the device is sleeping.
 */
class SimBodyHeartbeat(
    private val wakeService: SystemWakeService,
    private val bodyModel: BodyModel,
    private val pumpDevice: SimBodyPumpDevice
) : WakeupHandler {

    private val _ticks = MutableSharedFlow<Timestamp>(extraBufferCapacity = 1)
    /**
     * Flow of timestamps when a simulation tick occurred.
     */
    val ticks: SharedFlow<Timestamp> = _ticks.asSharedFlow()

    private var started = false

    init {
        wakeService.registerHandler(WAKE_TAG, this)
    }

    /**
     * Starts the heartbeat. If already started, this does nothing.
     */
    fun start() {
        if (started) return
        started = true
        Log.d(TAG, "SimBody Heartbeat started")
        scheduleNext()
    }

    /**
     * Stops the heartbeat.
     */
    fun stop() {
        started = false
        Log.d(TAG, "SimBody Heartbeat stopped")
    }

    override fun onWakeup(wakeupId: UInt?, intent: Intent?) {
        if (!started) return
        if (wakeupId == WAKEUP_ID_TICK) {
            performTick()
            scheduleNext()
        }
    }

    private fun performTick() {
        val now = Timestamp.now()
        Log.d(TAG, "SimBody Tick at $now")
        
        // Ensure the system stays awake during simulation calculations
        wakeService.acquireBusyState(WAKE_TAG)
        try {
            pumpDevice.advanceToTick(now)
            bodyModel.advanceToTick(now)
            _ticks.tryEmit(now)
        } finally {
            wakeService.releaseBusyState(WAKE_TAG)
        }
    }

    private fun scheduleNext() {
        if (!started) return
        val nextTick = Timestamp.now().plusMinutes(TICK_INTERVAL_MINUTES)
        wakeService.scheduleWakeup(WAKE_TAG, WAKEUP_ID_TICK, nextTick)
    }

    companion object {
        private const val TAG = "SimBodyHeartbeat"
        
        /**
         * Tag for the [SystemWakeService] to identify SimBody wakeups.
         */
        const val WAKE_TAG = "SIM_BODY"
        
        private val WAKEUP_ID_TICK = 1u
        private const val TICK_INTERVAL_MINUTES = 5
    }
}