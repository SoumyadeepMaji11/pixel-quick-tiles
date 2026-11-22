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
 */
class RingerModeTileService : TileService() {

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var isReceiverRegistered = false

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTileState()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
            ContextCompat.registerReceiver(
                this,
                ringerReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        }
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(ringerReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }
    }

    override fun onClick() {
        super.onClick()

        val currentMode = audioManager.ringerMode
        val hasDndPermission = notificationManager.isNotificationPolicyAccessGranted

        val targetMode = when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> {
                if (hasDndPermission) {
                    AudioManager.RINGER_MODE_SILENT
                } else {
                    Toast.makeText(
                        this,
                        "Silent mode requires Do Not Disturb access on Pixel",
                        Toast.LENGTH_SHORT
                    ).show()
                    promptDndPermission()
                    AudioManager.RINGER_MODE_NORMAL
                }
            }
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }

        try {
            audioManager.ringerMode = targetMode
            // Give Pixel users tactile click feedback
            VibrationHelper.triggerPixelHaptic(this)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException changing ringer mode on Pixel", e)
            Toast.makeText(
                this,
                "Please grant Do Not Disturb access in settings",
                Toast.LENGTH_LONG
            ).show()
            promptDndPermission()
        }

        updateTileState()
    }

    /**
     * Updates the Quick Settings Tile appearance.
     * Styled cleanly for Pixel's pill-style Material You Quick Settings.
     */
    private fun updateTileState() {
        val tile = qsTile ?: return

        when (audioManager.ringerMode) {
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
    }

    private fun promptDndPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
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
        private const val TAG = "RingerModeTileService"
    }
}
