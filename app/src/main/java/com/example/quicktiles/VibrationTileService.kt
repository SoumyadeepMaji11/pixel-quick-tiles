package com.example.quicktiles

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

/**
 * Quick Settings Tile tailored for Google Pixel devices.
 * Toggles the master vibration switch and individual haptics.
 */
class VibrationTileService : TileService() {

    private var isObserverRegistered = false

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            updateTileState()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!isObserverRegistered) {
            try {
                val resolver = contentResolver

                // Pixel master switch observer
                resolver.registerContentObserver(
                    Settings.System.getUriFor(VibrationHelper.SETTING_VIBRATE_ON),
                    false,
                    settingsObserver
                )

                // Call vibration observer
                resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_RINGING),
                    false,
                    settingsObserver
                )

                // Touch & gesture haptics observer
                resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED),
                    false,
                    settingsObserver
                )
                isObserverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Error registering ContentObserver on Pixel", e)
            }
        }
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (isObserverRegistered) {
            try {
                contentResolver.unregisterContentObserver(settingsObserver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering ContentObserver", e)
            }
            isObserverRegistered = false
        }
    }

    override fun onClick() {
        super.onClick()

        if (!VibrationHelper.canModifySystemSettings(this)) {
            Toast.makeText(
                this,
                "Please allow 'Modify system settings' to toggle vibration",
                Toast.LENGTH_LONG
            ).show()
            promptWriteSettingsPermission()
            return
        }

        val currentState = VibrationHelper.isVibrationEnabled(this)
        val newState = !currentState
        val success = VibrationHelper.setVibrationEnabled(this, newState)

        if (!success) {
            Toast.makeText(
                this,
                "Could not update vibration settings",
                Toast.LENGTH_SHORT
            ).show()
        }

        updateTileState()
    }

    /**
     * Updates the Quick Settings Tile appearance.
     * Styled specifically for Pixel's Material You Quick Settings pills.
     */
    private fun updateTileState() {
        val tile = qsTile ?: return

        val isEnabled = VibrationHelper.isVibrationEnabled(this)

        if (isEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Vibration"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "On"
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_vibration_on)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Vibration"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Off"
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_vibration_off)
        }

        tile.updateTile()
    }

    private fun promptWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        openActivity(intent)
    }

    /**
     * Safely opens an activity from a TileService (compatible with Pixel on Android 14 & 15).
     */
    private fun openActivity(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val TAG = "VibrationTileService"
    }
}
