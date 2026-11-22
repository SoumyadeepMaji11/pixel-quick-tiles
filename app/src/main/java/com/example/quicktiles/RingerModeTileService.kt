package com.example.quicktiles

import android.content.Context
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Quick Settings Tile tailored for Google Pixel devices.
 * Cycles strictly in order: Normal -> Vibration -> Silent -> Normal.
 *
 * Persists and operates reliably even when the main app is closed or cleared from recent apps.
 */
class RingerModeTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        updateTileUI(getCurrentMode(audio))
    }

    override fun onStartListening() {
        super.onStartListening()
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        updateTileUI(getCurrentMode(audio))
    }

    override fun onClick() {
        super.onClick()

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Cycle strictly: Normal -> Vibration -> Silent -> Normal
        val currentMode = getCurrentMode(audio)
        val nextMode = when (currentMode) {
            MODE_NORMAL -> MODE_VIBRATION
            MODE_VIBRATION -> MODE_SILENT
            MODE_SILENT -> MODE_NORMAL
            else -> MODE_NORMAL
        }

        // Apply mode immediately to hardware
        applyMode(audio, nextMode)

        // Update tile UI immediately on screen
        updateTileUI(nextMode)

        // Tactile feedback
        VibrationHelper.triggerPixelHaptic(this)
    }

    /**
     * Accurately determines current ringer mode.
     * MODE_NORMAL = 2, MODE_VIBRATION = 1, MODE_SILENT = 0
     */
    private fun getCurrentMode(audio: AudioManager): Int {
        return try {
            when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> MODE_SILENT
                AudioManager.RINGER_MODE_VIBRATE -> MODE_VIBRATION
                AudioManager.RINGER_MODE_NORMAL -> {
                    val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audio.isStreamMute(AudioManager.STREAM_RING)
                    } else false

                    if (isMuted && getSavedMode() == MODE_SILENT) {
                        MODE_SILENT
                    } else {
                        MODE_NORMAL
                    }
                }
                else -> MODE_NORMAL
            }
        } catch (_: Exception) {
            MODE_NORMAL
        }
    }

    /**
     * Applies the target ringer mode without triggering DND or getting stuck.
     */
    private fun applyMode(audio: AudioManager, mode: Int) {
        saveMode(mode)
        try {
            when (mode) {
                MODE_NORMAL -> {
                    // Unmute streams
                    try {
                        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: Exception) {}

                    audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

                    // Ensure ring volume is audible
                    try {
                        val currentVol = audio.getStreamVolume(AudioManager.STREAM_RING)
                        if (currentVol == 0) {
                            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                            audio.setStreamVolume(AudioManager.STREAM_RING, (maxVol / 2).coerceAtLeast(1), 0)
                        }
                    } catch (_: Exception) {}
                }
                MODE_VIBRATION -> {
                    // Unmute streams so vibration takes over
                    try {
                        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: Exception) {}

                    audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                MODE_SILENT -> {
                    // Set to Silent mode
                    try {
                        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (_: Exception) {
                        // Fallback: Mute ring & notification streams directly
                        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying ringer mode: $mode", e)
        }
    }

    /**
     * Updates the Quick Settings Tile appearance.
     */
    private fun updateTileUI(mode: Int) {
        val tile = qsTile ?: return

        try {
            when (mode) {
                MODE_NORMAL -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Normal"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_normal)
                }
                MODE_VIBRATION -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Vibration"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_vibrate)
                }
                MODE_SILENT -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Silent"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_silent)
                }
            }
            tile.updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating tile UI", e)
        }
    }

    private fun getSavedMode(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SAVED_MODE, MODE_NORMAL)
    }

    private fun saveMode(mode: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SAVED_MODE, mode).apply()
    }

    companion object {
        private const val TAG = "RingerModeTileService"
        private const val PREFS_NAME = "ringer_tile_prefs"
        private const val KEY_SAVED_MODE = "saved_ringer_mode"

        const val MODE_SILENT = 0
        const val MODE_VIBRATION = 1
        const val MODE_NORMAL = 2
    }
}
