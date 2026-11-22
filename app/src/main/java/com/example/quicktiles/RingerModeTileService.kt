package com.example.quicktiles

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Quick Settings Tile tailored for Google Pixel devices.
 * Cycles through: Ring (Normal) -> Vibrate -> Silent -> Ring.
 *
 * Uses stopWithTask="false" and ACTIVE_TILE meta-data to survive app closure.
 */
class RingerModeTileService : TileService() {

    private var isReceiverRegistered = false

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                updateTileState()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating tile from receiver", e)
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Ringer tile added to Quick Settings")
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        registerRingerReceiver()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterRingerReceiver()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        unregisterRingerReceiver()
        Log.d(TAG, "Ringer tile removed from Quick Settings")
    }

    override fun onClick() {
        super.onClick()

        try {
            // Get fresh instances every click (survives process restart)
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notifMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val currentMode = audio.ringerMode
            val hasDndPermission = notifMgr.isNotificationPolicyAccessGranted

            Log.d(TAG, "onClick: current=$currentMode, hasDND=$hasDndPermission")

            val targetMode = when (currentMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> {
                    if (hasDndPermission) {
                        AudioManager.RINGER_MODE_SILENT
                    } else {
                        showToast("Silent mode requires Do Not Disturb access")
                        promptDndPermission()
                        AudioManager.RINGER_MODE_NORMAL
                    }
                }
                AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
                else -> AudioManager.RINGER_MODE_NORMAL
            }

            audio.ringerMode = targetMode
            Log.d(TAG, "Ringer mode changed to: $targetMode")

            // Tactile click feedback
            VibrationHelper.triggerPixelHaptic(this)

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException changing ringer mode", e)
            showToast("Please grant Do Not Disturb access in settings")
            promptDndPermission()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onClick", e)
            showToast("Could not change ringer mode")
        }

        updateTileState()
    }

    /**
     * Updates the Quick Settings Tile appearance.
     * Gets a fresh AudioManager reference each time to handle process restarts.
     */
    private fun updateTileState() {
        val tile = qsTile ?: return

        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            when (audio.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Ring"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_normal)
                }
                AudioManager.RINGER_MODE_VIBRATE -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Vibrate"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_vibrate)
                }
                AudioManager.RINGER_MODE_SILENT -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Silent"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_silent)
                }
                else -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Unknown"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_normal)
                }
            }

            tile.updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating tile state", e)
        }
    }

    private fun registerRingerReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
                ContextCompat.registerReceiver(
                    this,
                    ringerReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Error registering ringer receiver", e)
            }
        }
    }

    private fun unregisterRingerReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(ringerReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun promptDndPermission() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            openActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening DND settings", e)
        }
    }

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
        private const val TAG = "RingerModeTileService"
    }
}
