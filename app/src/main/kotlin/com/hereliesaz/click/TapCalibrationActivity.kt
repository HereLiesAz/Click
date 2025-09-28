package com.hereliesaz.click

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class TapCalibrationActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var calibrationStatusText: TextView

    private var lastZ = 0f
    private var tapsRecorded = 0
    private val recordedForces = mutableListOf<Float>()
    private var lastTapTimestamp = 0L

    companion object {
        const val KEY_BACK_TAP_SENSITIVITY = "back_tap_sensitivity"
        private const val REQUIRED_TAPS = 3
        private const val TAP_COOLDOWN_MS = 300L // Prevent single shake from counting as multiple taps
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tap_calibration)

        calibrationStatusText = findViewById(R.id.calibration_status)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val z = event.values[2]
            val deltaZ = abs(z - lastZ)
            lastZ = z

            val currentTime = System.currentTimeMillis()
            if (deltaZ > 15 && (currentTime - lastTapTimestamp > TAP_COOLDOWN_MS)) { // Initial high threshold to detect a clear tap
                lastTapTimestamp = currentTime
                tapsRecorded++
                recordedForces.add(deltaZ)
                calibrationStatusText.text = "Taps Recorded: $tapsRecorded of $REQUIRED_TAPS"

                if (tapsRecorded >= REQUIRED_TAPS) {
                    finishCalibration()
                }
            }
        }
    }

    private fun finishCalibration() {
        if (recordedForces.isEmpty()) {
            Toast.makeText(this, "Calibration failed. Please try again.", Toast.LENGTH_SHORT).show()
        } else {
            // Calculate sensitivity: average force minus a margin for reliability
            val averageForce = recordedForces.average()
            val sensitivity = (averageForce * 0.75).toFloat() // Use 75% of the average force as the threshold

            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putFloat(KEY_BACK_TAP_SENSITIVITY, sensitivity).apply()

            Toast.makeText(this, "Calibration complete! Sensitivity set to ${"%.2f".format(sensitivity)}", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}