package com.example.quicktiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
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
        val currentMode = audio.ringerMode

        // 2-way toggle: if currently Vibration -> Normal; otherwise -> Vibration
        val nextMode = if (currentMode == AudioManager.RINGER_MODE_VIBRATE) {
            AudioManager.RINGER_MODE_NORMAL
        } else {
            AudioManager.RINGER_MODE_VIBRATE
        }

        try {
            audio.ringerMode = nextMode

            // When switching to Normal, ensure ring volume is audible if it was muted
            if (nextMode == AudioManager.RINGER_MODE_NORMAL) {
                try {
                    val currentVol = audio.getStreamVolume(AudioManager.STREAM_RING)
                    if (currentVol == 0) {
                        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                        audio.setStreamVolume(AudioManager.STREAM_RING, (maxVol / 2).coerceAtLeast(1), 0)
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying ringer mode: $nextMode", e)
        }

        // Immediately update tile UI on screen
        updateTileUI(nextMode)

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
                else -> { // RINGER_MODE_VIBRATE (or any other mode)
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

    companion object {
        private const val TAG = "RingerModeTileService"
    }
}
