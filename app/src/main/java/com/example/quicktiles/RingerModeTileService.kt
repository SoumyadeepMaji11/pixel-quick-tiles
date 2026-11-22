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
 * Clean 2-way toggle between Normal (sound on) and Vibration (vibrate on).
 * Silent mode is completely removed.
 *
 * Fully functional when app is closed or cleared from recent apps.
 */
class RingerModeTileService : TileService() {

    private var isReceiverRegistered = false

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                updateTileUI(audio.ringerMode)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating tile from receiver", e)
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        updateTileUI(audio.ringerMode)
    }

    override fun onStartListening() {
        super.onStartListening()
        registerRingerReceiver()
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        updateTileUI(audio.ringerMode)
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterRingerReceiver()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        unregisterRingerReceiver()
    }

    override fun onClick() {
        super.onClick()

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notifMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android requires Notification Policy Access to switch out of vibrate or change ringer mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notifMgr.isNotificationPolicyAccessGranted) {
            showToast("Please allow Notification Policy Access to toggle ringer")
            promptDndPermission()
            updateTileUI(audio.ringerMode)
            return
        }

        val currentMode = audio.ringerMode
        val nextMode = if (currentMode == AudioManager.RINGER_MODE_VIBRATE) {
            AudioManager.RINGER_MODE_NORMAL
        } else {
            AudioManager.RINGER_MODE_VIBRATE
        }

        try {
            if (nextMode == AudioManager.RINGER_MODE_NORMAL) {
                // Clear any lingering interruption filter if set
                try {
                    notifMgr.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                } catch (_: Exception) {}

                // Switch ringer to Normal
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

                // Ensure ring & notification streams are unmuted
                try {
                    audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                    audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                } catch (_: Exception) {}

                // Ensure ring volume is audible
                try {
                    val currentVol = audio.getStreamVolume(AudioManager.STREAM_RING)
                    if (currentVol == 0) {
                        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                        audio.setStreamVolume(AudioManager.STREAM_RING, (maxVol / 2).coerceAtLeast(1), 0)
                    }
                } catch (_: Exception) {}
            } else {
                audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying ringer mode: $nextMode", e)
            showToast("Could not change ringer mode")
        }

        // Update tile UI with confirmed hardware state
        updateTileUI(audio.ringerMode)

        // Haptic feedback
        VibrationHelper.triggerPixelHaptic(this)
    }

    /**
     * Updates the Quick Settings Tile appearance.
     */
    private fun updateTileUI(mode: Int) {
        val tile = qsTile ?: return

        try {
            when (mode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = getString(R.string.tile_ringer_name)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = getString(R.string.ringer_mode_normal)
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_normal)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.contentDescription = "Ringer mode: Normal"
                    }
                }
                else -> { // RINGER_MODE_VIBRATE (or any non-normal mode)
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = getString(R.string.tile_ringer_name)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = getString(R.string.ringer_mode_vibrate)
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_vibrate)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.contentDescription = "Ringer mode: Vibration"
                    }
                }
            }
            tile.updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating tile UI", e)
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
                Log.e(TAG, "Error unregistering ringer receiver", e)
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
        private const val TAG = "RingerModeTileService"
    }
}
