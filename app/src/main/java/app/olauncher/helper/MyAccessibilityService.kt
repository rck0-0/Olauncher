package app.olauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import app.olauncher.R
import app.olauncher.data.AppBlockerPrefs
import app.olauncher.data.BlockedApp
import app.olauncher.data.Prefs
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    private lateinit var blockerPrefs: AppBlockerPrefs
    private lateinit var cachedBlockedApps: List<BlockedApp>

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "blocked_apps") {
            updateBlockedAppsCache()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        Prefs(applicationContext).lockModeOn = true
        blockerPrefs = AppBlockerPrefs(applicationContext)

        // Register listener
        getSharedPreferences("app_blocker_prefs", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)

        updateBlockedAppsCache()
        super.onServiceConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("app_blocker_prefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun updateBlockedAppsCache() {
        cachedBlockedApps = blockerPrefs.blockedApps
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Double tap to lock logic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
             try {
                val source: AccessibilityNodeInfo? = event.source
                if (source != null && (source.className == "android.widget.FrameLayout") and
                    (source.contentDescription == getString(R.string.lock_layout_description))
                ) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // App Blocking Logic
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Avoid blocking ourselves
            if (packageName == applicationContext.packageName) return

            if (shouldBlock(packageName)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                Toast.makeText(this, "App blocked by Olauncher", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shouldBlock(packageName: String): Boolean {
        if (!::cachedBlockedApps.isInitialized) {
            updateBlockedAppsCache()
        }

        val blockedApp = cachedBlockedApps.find { it.packageName == packageName } ?: return false

        if (!blockedApp.isEnabled) return false

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTime = currentHour * 60 + currentMinute

        // Parse start and end times
        val (startHour, startMinute) = parseTime(blockedApp.startTime)
        val (endHour, endMinute) = parseTime(blockedApp.endTime)
        val startTime = startHour * 60 + startMinute
        val endTime = endHour * 60 + endMinute

        return if (startTime <= endTime) {
             currentTime in startTime..endTime
        } else {
            // Crosses midnight (e.g. 22:00 to 06:00)
            currentTime >= startTime || currentTime <= endTime
        }
    }

    private fun parseTime(timeString: String): Pair<Int, Int> {
        return try {
            val parts = timeString.split(":")
            parts[0].toInt() to parts[1].toInt()
        } catch (e: Exception) {
            0 to 0
        }
    }

    override fun onInterrupt() {

    }
}
