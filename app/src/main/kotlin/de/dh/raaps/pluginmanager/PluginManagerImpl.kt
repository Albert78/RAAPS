package de.dh.raaps.pluginmanager

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import de.dh.raaps.common.model.Plugin
import de.dh.raaps.common.model.PluginManager

class PluginManagerImpl(
    val context: Context
): PluginManager {
    private val plugins: MutableList<Plugin> = ArrayList()

    override fun addPlugin(plugin: Plugin) {
        plugins.add(plugin)
        plugin.initialize(this)
    }

    override fun getPlugins(): List<Plugin> {
        return plugins
    }

    override fun checkSelfPermissions(androidPermissions: Collection<String>): Collection<String> {
        val ownedPermissions = mutableListOf<String>()
        for (permissionStr in androidPermissions) {
            if (ActivityCompat.checkSelfPermission(context, permissionStr) == PackageManager.PERMISSION_GRANTED) {
                ownedPermissions.add(permissionStr)
            }
        }
        return ownedPermissions
    }

    override fun triggerUpdatesAfterPermissionsChange() {
        // TODO: Update plugins
    }
}