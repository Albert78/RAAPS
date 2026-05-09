package de.dh.raaps.plugin.glucose.receiver

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import de.dh.raaps.common.model.GlucosePlugin
import de.dh.raaps.common.model.data.BgReading
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Glucose plugin which receives glucose values from other Android apps via a BroadcastReceiver.
 * The receiver will dynamically be registered on [start] and [stop] calls.
 */
class ReceiverGlucosePlugin(
    val application: Application,
    val externalSourceType: ExternalSourceType
) : GlucosePlugin {
    override val name: String = "Receiver Glucose Plugin"
    override val readingsInterval = externalSourceType.readingsInterval
    override val readingsTimeDelay = externalSourceType.readingsTimeDelay
    override val dataProviderType: String = "Glucose $readingsInterval"

    val dataReceiver: DataReceiver = DataReceiver()

    override fun start() {
        if (instance != null) {
            throw IllegalStateException("Plugin ${ReceiverGlucosePlugin::class.simpleName} is already started")
        }
        instance = this
        // The receiver is registered via AndroidManifest.xml. This is necessary for other apps
        // to find our receiver via the packet manager (used by Juggluco).
        // Drawback: Receivers registered via Manifest don't receive broadcasts with implicit intents,
        // what does xDrip?
        //registerReceiver()
    }

    override fun stop() {
        instance = null
        //unregisterReceiver()
    }

    private fun registerReceiver() {
        val filter = IntentFilter("com.eveningoutpost.dexdrip.BgEstimate")
        application.registerReceiver(dataReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun unregisterReceiver() {
        application.unregisterReceiver(dataReceiver)
    }

    override fun getSensorTypeName() = "External Receiver"

    private val _readings = MutableSharedFlow<BgReading>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Values received by our BroadcastReceiver will go here.
     */
    fun injectReading(value: BgReading): Boolean {
        // tryEmit will always succeed because DROP_OLDEST ensures there is always room
        Log.d(TAG, "New glucose reading: $value")
        return _readings.tryEmit(value)
    }

    override fun getValues(): Flow<BgReading> {
        return _readings.asSharedFlow()
    }

    companion object {
        val TAG = ReceiverGlucosePlugin::class.simpleName
        var instance: ReceiverGlucosePlugin? = null
    }
}