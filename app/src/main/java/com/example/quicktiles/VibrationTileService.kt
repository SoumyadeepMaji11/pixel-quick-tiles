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
 *
 * Uses stopWithTask="false" and ACTIVE_TILE meta-data to survive app closure.
 */
class VibrationTileService : TileService() {

    private var isObserverRegistered = false

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            try {
                updateTileState()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating tile from observer", e)
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Vibration tile added to Quick Settings")
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        registerObservers()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterObservers()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        unregisterObservers()
        Log.d(TAG, "Vibration tile removed from Quick Settings")
    }

    override fun onClick() {
        super.onClick()

        try {
            if (!VibrationHelper.canModifySystemSettings(this)) {
                showToast("Please allow 'Modify system settings' to toggle vibration")
                promptWriteSettingsPermission()
                return
            }

            val currentState = VibrationHelper.isVibrationEnabled(this)
            val newState = !currentState
            val success = VibrationHelper.setVibrationEnabled(this, newState)

            Log.d(TAG, "onClick: was=$currentState, target=$newState, success=$success")

            if (!success) {
                showToast("Could not update vibration settings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onClick", e)
            showToast("Error toggling vibration")
        }

        updateTileState()
    }

    /**
     * Updates the Quick Settings Tile appearance.
     */
    private fun updateTileState() {
        val tile = qsTile ?: return

        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error updating tile state", e)
        }
    }

    private fun registerObservers() {
        if (!isObserverRegistered) {
            try {
                val resolver = contentResolver
                resolver.registerContentObserver(
                    Settings.System.getUriFor(VibrationHelper.SETTING_VIBRATE_ON),
                    false, settingsObserver
                )
                resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_RINGING),
                    false, settingsObserver
                )
                resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED),
                    false, settingsObserver
                )
                isObserverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Error registering ContentObservers", e)
            }
        }
    }

    private fun unregisterObservers() {
        if (isObserverRegistered) {
            try {
                contentResolver.unregisterContentObserver(settingsObserver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering ContentObserver", e)
            }
            isObserverRegistered = false
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun promptWriteSettingsPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            openActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening write settings", e)
        }
    }

    private fun openActivity(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
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
