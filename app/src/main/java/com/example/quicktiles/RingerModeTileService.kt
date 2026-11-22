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
 * Cycles strictly in order: Silent -> Vibration -> Normal -> Silent.
 *
 * Designed to execute cleanly even when the app is completely cleared from recent history.
 */
class RingerModeTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        updateTileUI(getCurrentMode(audio))
    }

    override fun onStartListening() {
        super.onStartListening()
        // Fresh audio manager query whenever the shade is pulled down
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        updateTileUI(getCurrentMode(audio))
    }

    override fun onClick() {
        super.onClick()

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Cycle strictly: Silent -> Vibration -> Normal -> Silent
        val currentMode = getCurrentMode(audio)
        val nextMode = when (currentMode) {
            MODE_SILENT -> MODE_VIBRATION
            MODE_VIBRATION -> MODE_NORMAL
            MODE_NORMAL -> MODE_SILENT
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
     * Determines current ringer mode.
     * 0 = Silent (Muted), 1 = Vibration, 2 = Normal
     */
    private fun getCurrentMode(audio: AudioManager): Int {
        return try {
            when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> MODE_SILENT
                AudioManager.RINGER_MODE_VIBRATE -> MODE_VIBRATION
                AudioManager.RINGER_MODE_NORMAL -> {
                    val ringVol = audio.getStreamVolume(AudioManager.STREAM_RING)
                    if (ringVol == 0) MODE_SILENT else MODE_NORMAL
                }
                else -> MODE_NORMAL
            }
        } catch (_: Exception) {
            MODE_NORMAL
        }
    }

    /**
     * Applies the target ringer mode without requiring DND or interrupting notifications.
     */
    private fun applyMode(audio: AudioManager, mode: Int) {
        try {
            when (mode) {
                MODE_SILENT -> {
                    try {
                        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (_: Exception) {
                        // Fallback: Mute the ring & notification streams directly
                        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    }
                }
                MODE_VIBRATION -> {
                    try {
                        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: Exception) {}
                    audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                MODE_NORMAL -> {
                    try {
                        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: Exception) {}
                    audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
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
                MODE_SILENT -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Silent"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_silent)
                }
                MODE_VIBRATION -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Vibration"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_vibrate)
                }
                MODE_NORMAL -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Ringer"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Normal"
                    }
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_sound_normal)
                }
            }
            tile.updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating tile UI", e)
        }
    }

    companion object {
        private const val TAG = "RingerModeTileService"
        const val MODE_SILENT = 0
        const val MODE_VIBRATION = 1
        const val MODE_NORMAL = 2
    }
}
