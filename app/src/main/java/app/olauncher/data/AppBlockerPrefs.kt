package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class BlockedApp(
    val packageName: String,
    val appName: String,
    val startTime: String = "09:00", // HH:mm
    val endTime: String = "17:00",   // HH:mm
    val isEnabled: Boolean = true
)

class AppBlockerPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_blocker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var blockedApps: List<BlockedApp>
        get() {
            val json = prefs.getString("blocked_apps", null) ?: return emptyList()
            val type = object : TypeToken<List<BlockedApp>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString("blocked_apps", json).apply()
        }

    fun addOrUpdateBlockedApp(app: BlockedApp) {
        val currentList = blockedApps.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == app.packageName }
        if (index != -1) {
            currentList[index] = app
        } else {
            currentList.add(app)
        }
        blockedApps = currentList
    }

    fun removeBlockedApp(packageName: String) {
        val currentList = blockedApps.toMutableList()
        currentList.removeAll { it.packageName == packageName }
        blockedApps = currentList
    }

    fun isAppBlocked(packageName: String): Boolean {
        // Simple check if it's in the list and enabled.
        // Time checking will happen in the service or a helper method.
        return blockedApps.any { it.packageName == packageName && it.isEnabled }
    }
}
