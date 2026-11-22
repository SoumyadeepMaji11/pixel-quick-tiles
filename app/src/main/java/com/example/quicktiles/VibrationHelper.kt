package com.example.quicktiles

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

object VibrationHelper {
    private const val TAG = "VibrationHelper"

    // Key used by Pixel and AOSP for "Use vibration & haptics" master switch
    const val SETTING_VIBRATE_ON = "vibrate_on"

    /**
     * Checks if vibration / haptics are currently enabled on Pixel / Android.
     * On Google Pixel, "vibrate_on" is the master switch in Settings > Sound & vibration > Vibration & haptics.
     */
    fun isVibrationEnabled(context: Context): Boolean {
        return try {
            val resolver = context.contentResolver

            // 1. Check Pixel's master switch: "Use vibration & haptics"
            // If "vibrate_on" is set to 0, all vibrations across the Pixel are killed.
            val masterVibrateOn = Settings.System.getInt(resolver, SETTING_VIBRATE_ON, 1) == 1
            if (!masterVibrateOn) {
                return false
            }

            // 2. Check call vibration and haptic feedback
            val vibrateWhenRinging = Settings.System.getInt(
                resolver,
                Settings.System.VIBRATE_WHEN_RINGING,
                0
            ) == 1

            val hapticFeedback = Settings.System.getInt(
                resolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                0
            ) == 1

            // Enabled if master is ON and either ringing or haptics are active
            vibrateWhenRinging || hapticFeedback
        } catch (e: Exception) {
            Log.e(TAG, "Error checking vibration state", e)
            false
        }
    }

    /**
     * Toggles global vibration and haptic feedback for Pixel devices.
     * Returns true if successful, false if WRITE_SETTINGS permission is missing.
     */
    fun setVibrationEnabled(context: Context, enable: Boolean): Boolean {
        if (!canModifySystemSettings(context)) {
            Log.w(TAG, "Cannot modify system settings: permission not granted")
            return false
        }

        val resolver = context.contentResolver
        val flagValue = if (enable) 1 else 0

        try {
            // 1. Toggle Pixel's master "Use vibration & haptics" switch
            try {
                Settings.System.putInt(resolver, SETTING_VIBRATE_ON, flagValue)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set vibrate_on in System settings", e)
            }

            // 2. Toggle incoming call vibration
            Settings.System.putInt(
                resolver,
                Settings.System.VIBRATE_WHEN_RINGING,
                flagValue
            )

            // 3. Toggle touch & gesture haptic feedback
            Settings.System.putInt(
                resolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                flagValue
            )

            // 4. Update intensity keys if present (Pixel Android 12+)
            val intensity = if (enable) 2 else 0 // 0=Off, 2=Medium
            try {
                Settings.System.putInt(resolver, "ring_vibration_intensity", intensity)
                Settings.System.putInt(resolver, "haptic_feedback_intensity", intensity)
                Settings.System.putInt(resolver, "notification_vibration_intensity", intensity)
                Settings.System.putInt(resolver, "media_vibration_intensity", intensity)
            } catch (_: Exception) {}

            // 5. If WRITE_SECURE_SETTINGS is granted (e.g. via ADB), also update secure table
            if (hasSecureSettingsPermission(context)) {
                try {
                    Settings.Secure.putInt(resolver, SETTING_VIBRATE_ON, flagValue)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update secure vibration settings", e)
                }
            }

            // Provide Pixel haptic buzz when enabling
            if (enable) {
                triggerPixelHaptic(context)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle vibration settings", e)
            return false
        }
    }

    /**
     * Checks if the app has permission to modify system settings.
     */
    fun canModifySystemSettings(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    /**
     * Checks if the app was granted WRITE_SECURE_SETTINGS via ADB.
     */
    fun hasSecureSettingsPermission(context: Context): Boolean {
        return context.checkCallingOrSelfPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Triggers a crisp Pixel haptic click using modern VibrationEffect constants.
     */
    fun triggerPixelHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Exception) {}
    }
}
