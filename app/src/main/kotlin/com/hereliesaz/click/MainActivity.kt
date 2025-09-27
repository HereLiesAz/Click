package com.hereliesaz.click

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * The main entry point of the application.
 * This activity provides the user interface for:
 * 1. Guiding the user to enable the required Accessibility Service and Overlay permissions.
 * 2. Allowing the user to enable or disable the different camera trigger methods.
 * 3. Displaying the current status of the required permissions.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var serviceStatusText: TextView
    private lateinit var overlayStatusText: TextView
    private lateinit var fingerprintSwitch: SwitchCompat
    private lateinit var lensTapProximitySwitch: SwitchCompat
    private lateinit var lensTapVibrationSwitch: SwitchCompat
    private lateinit var vibrationSensitivitySeekbar: SeekBar
    private lateinit var prefs: SharedPreferences

    companion object {
        /** SharedPreferences file name for storing user settings. */
        const val PREFS_NAME = "ClickPrefs"
        /** SharedPreferences key for the fingerprint scroll option. */
        const val KEY_FINGERPRINT_ENABLED = "fingerprintEnabled"
        /** SharedPreferences key for the proximity sensor option. */
        const val KEY_LENS_TAP_PROXIMITY_ENABLED = "lensTapProximityEnabled"
        /** SharedPreferences key for the accelerometer option. */
        const val KEY_LENS_TAP_VIBRATION_ENABLED = "lensTapVibrationEnabled"
        /** SharedPreferences key for the vibration sensitivity setting. */
        const val KEY_VIBRATION_SENSITIVITY = "vibrationSensitivity"

        /**
         * Checks if the specified Accessibility Service is currently enabled in the system settings.
         * @param context The application context.
         * @param accessibilityService The class of the service to check.
         * @return `true` if the service is enabled, `false` otherwise.
         */
        fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            val settingValue = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

            settingValue?.let {
                colonSplitter.setString(it)
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.equals(context.packageName + "/" + accessibilityService.name, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }
    }

    /**
     * Initializes the activity, sets up the UI, and configures listeners for user interactions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize UI components
        serviceStatusText = findViewById(R.id.service_status_text)
        overlayStatusText = findViewById(R.id.overlay_permission_status_text)
        fingerprintSwitch = findViewById(R.id.fingerprint_scroll_switch)
        lensTapProximitySwitch = findViewById(R.id.lens_tap_proximity_switch)
        lensTapVibrationSwitch = findViewById(R.id.lens_tap_vibration_switch)
        vibrationSensitivitySeekbar = findViewById(R.id.vibration_sensitivity_seekbar)


        val enableServiceButton: Button = findViewById(R.id.enable_service_button)
        val enableOverlayButton: Button = findViewById(R.id.enable_overlay_permission_button)

        // Set up button listeners to open system settings
        enableServiceButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        enableOverlayButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // Set up switch listeners to save preferences
        fingerprintSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, isChecked).apply()
        }

        lensTapProximitySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_LENS_TAP_PROXIMITY_ENABLED, isChecked).apply()
        }

        lensTapVibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            vibrationSensitivitySeekbar.isEnabled = isChecked
            prefs.edit().putBoolean(KEY_LENS_TAP_VIBRATION_ENABLED, isChecked).apply()
        }

        vibrationSensitivitySeekbar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // We save it in onStopTrackingTouch to avoid excessive writes
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt(KEY_VIBRATION_SENSITIVITY, seekBar?.progress ?: 50).apply()
            }
        })
    }

    /**
     * Called when the activity is resumed. Updates the UI to reflect the current
     * state of permissions and user settings.
     */
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateOverlayPermissionStatus()
        loadPreferences()
    }

    /**
     * Loads the saved preferences and sets the state of the UI switches accordingly.
     */
    private fun loadPreferences() {
        fingerprintSwitch.isChecked = prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)
        lensTapProximitySwitch.isChecked = prefs.getBoolean(KEY_LENS_TAP_PROXIMITY_ENABLED, false)
        lensTapVibrationSwitch.isChecked = prefs.getBoolean(KEY_LENS_TAP_VIBRATION_ENABLED, false)
        vibrationSensitivitySeekbar.progress = prefs.getInt(KEY_VIBRATION_SENSITIVITY, 50)
        vibrationSensitivitySeekbar.isEnabled = lensTapVibrationSwitch.isChecked
    }

    /**
     * Updates the text and background color of the service status TextView based
     * on whether the Accessibility Service is enabled.
     */
    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, ClickAccessibilityService::class.java)) {
            serviceStatusText.text = "Service Status: Enabled"
            serviceStatusText.setBackgroundColor(0xFFC8E6C9.toInt()) // Green
        } else {
            serviceStatusText.text = "Service Status: Disabled"
            serviceStatusText.setBackgroundColor(0xFFFFCDD2.toInt()) // Red
        }
    }

    /**
     * Updates the text and background color of the overlay permission status TextView
     * based on whether the permission has been granted.
     */
    private fun updateOverlayPermissionStatus() {
        if (Settings.canDrawOverlays(this)) {
            overlayStatusText.text = "Overlay Permission: Granted"
            overlayStatusText.setBackgroundColor(0xFFC8E6C9.toInt())
        } else {
            overlayStatusText.text = "Overlay Permission: Denied"
            overlayStatusText.setBackgroundColor(0xFFFFCDD2.toInt())
        }
    }
}
