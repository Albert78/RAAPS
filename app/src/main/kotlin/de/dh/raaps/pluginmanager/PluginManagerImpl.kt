package de.dh.raaps.pluginmanager

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import de.dh.raaps.common.model.PluginManager

class PluginManagerImpl(
    val context: Context
): PluginManager {
    override fun checkSelfPermissions(androidPermissions: Collection<String>): Collection<String> {
        val ownedPermissions = mutableListOf<String>()
        for (permissionStr in androidPermissions) {
            if (ActivityCompat.checkSelfPermission(context, permissionStr) == PackageManager.PERMISSION_GRANTED) {
                ownedPermissions.add(permissionStr)
            }
        }
        return ownedPermissions
    }
}