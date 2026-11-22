package com.example.quicktiles

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors

/**
 * Companion activity tailored for Google Pixel devices.
 * Enables Material You dynamic theming and provides one-tap permission management.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvRingerStatus: TextView
    private lateinit var ivRingerStatus: ImageView
    private lateinit var tvVibrationStatus: TextView
    private lateinit var ivVibrationStatus: ImageView

    private lateinit var tvDndStatus: TextView
    private lateinit var btnGrantDnd: Button
    private lateinit var tvWriteSettingsStatus: TextView
    private lateinit var btnGrantWriteSettings: Button

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // Dynamic receiver for Pixel ringer state changes
    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateLiveStatus()
        }
    }

    // Dynamic observer for Pixel vibration state changes
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            updateLiveStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Pixel's Material You dynamic colors based on wallpaper
        DynamicColors.applyToActivityIfAvailable(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
        updateLiveStatus()

        // Register listeners for real-time changes
        try {
            ContextCompat.registerReceiver(
                this,
                ringerReceiver,
                IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )

            val resolver = contentResolver
            // Observe Pixel master switch
            resolver.registerContentObserver(
                Settings.System.getUriFor(VibrationHelper.SETTING_VIBRATE_ON),
                false,
                settingsObserver
            )
            // Observe Call vibration
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_RINGING),
                false,
                settingsObserver
            )
            // Observe Haptic feedback
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED),
                false,
                settingsObserver
            )
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(ringerReceiver)
        } catch (_: Exception) {}

        try {
            contentResolver.unregisterContentObserver(settingsObserver)
        } catch (_: Exception) {}
    }

    private fun initViews() {
        tvRingerStatus = findViewById(R.id.tvRingerStatus)
        ivRingerStatus = findViewById(R.id.ivRingerStatus)
        tvVibrationStatus = findViewById(R.id.tvVibrationStatus)
        ivVibrationStatus = findViewById(R.id.ivVibrationStatus)

        tvDndStatus = findViewById(R.id.tvDndStatus)
        btnGrantDnd = findViewById(R.id.btnGrantDnd)
        tvWriteSettingsStatus = findViewById(R.id.tvWriteSettingsStatus)
        btnGrantWriteSettings = findViewById(R.id.btnGrantWriteSettings)
    }

    private fun setupListeners() {
        btnGrantDnd.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }

        btnGrantWriteSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun updateLiveStatus() {
        // 1. Ringer status on Pixel (2-way: Normal <-> Vibration)
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                tvRingerStatus.text = getString(R.string.ringer_mode_normal)
                tvRingerStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
                ivRingerStatus.setImageResource(R.drawable.ic_sound_normal)
            }
            else -> { // Vibration mode (or any non-normal mode)
                tvRingerStatus.text = getString(R.string.ringer_mode_vibrate)
                tvRingerStatus.setTextColor(ContextCompat.getColor(this, R.color.status_pending))
                ivRingerStatus.setImageResource(R.drawable.ic_sound_vibrate)
            }
        }

        // 2. Global Vibration status on Pixel
        val isVibrationOn = VibrationHelper.isVibrationEnabled(this)
        if (isVibrationOn) {
            tvVibrationStatus.text = "Enabled"
            tvVibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.status_granted))
            ivVibrationStatus.setImageResource(R.drawable.ic_vibration_on)
        } else {
            tvVibrationStatus.text = "Disabled"
            tvVibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            ivVibrationStatus.setImageResource(R.drawable.ic_vibration_off)
        }
    }

    private fun updatePermissionsStatus() {
        // DND / Notification Policy Access (for Ringer Toggle)
        val hasDnd = notificationManager.isNotificationPolicyAccessGranted
        if (hasDnd) {
            tvDndStatus.text = getString(R.string.status_granted)
            tvDndStatus.setTextColor(ContextCompat.getColor(this, R.color.status_granted))
            btnGrantDnd.visibility = View.GONE
        } else {
            tvDndStatus.text = getString(R.string.status_not_granted)
            tvDndStatus.setTextColor(ContextCompat.getColor(this, R.color.status_pending))
            btnGrantDnd.visibility = View.VISIBLE
        }

        // Modify System Settings Permission (for Global Vibration Toggle)
        val hasWriteSettings = VibrationHelper.canModifySystemSettings(this)
        if (hasWriteSettings) {
            tvWriteSettingsStatus.text = getString(R.string.status_granted)
            tvWriteSettingsStatus.setTextColor(ContextCompat.getColor(this, R.color.status_granted))
            btnGrantWriteSettings.visibility = View.GONE
        } else {
            tvWriteSettingsStatus.text = getString(R.string.status_not_granted)
            tvWriteSettingsStatus.setTextColor(ContextCompat.getColor(this, R.color.status_pending))
            btnGrantWriteSettings.visibility = View.VISIBLE
        }
    }
}
