package com.hereliesaz.click

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class ClickAccessibilityService : AccessibilityService(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    private var proximityCoverTime = 0L
    private var isProximityCovered = false
    private var isCameraAppActive = false

    companion object {
        private const val PROXIMITY_TAP_THRESHOLD_MS = 500L // Max duration for a 'tap'
        private val CAMERA_PACKAGES = setOf(
            "com.google.android.GoogleCamera", // Google Camera
            "com.android.camera",             // Default AOSP Camera
            "com.android.camera2",            // Another AOSP Camera
            "com.samsung.android.camera",     // Samsung Camera
            "com.oneplus.camera",             // OnePlus Camera
            "com.motorola.cameraone",         // Motorola Camera
            "com.sonyericsson.android.camera",// Sony Camera
            "org.codeaurora.snapcam"          // Snap Camera (found on some custom ROMs)
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let {
                isCameraAppActive = CAMERA_PACKAGES.contains(it.toString())
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY && isCameraAppActive) {
            val distance = event.values[0]
            // If proximitySensor is null, maxRange defaults to the current distance, failing the check safely.
            val maxRange = proximitySensor?.maximumRange ?: distance
            
            // Proximity sensor reports distance. A low value means something is very close.
            if (distance < maxRange) {
                // Sensor is covered
                if (!isProximityCovered) {
                    isProximityCovered = true
                    proximityCoverTime = SystemClock.uptimeMillis()
                }
            } else {
                // Sensor is uncovered
                if (isProximityCovered) {
                    val uncoverTime = SystemClock.uptimeMillis()
                    val duration = uncoverTime - proximityCoverTime
                    // If covered and uncovered quickly, it's a "tap"
                    if (duration > 50 && duration < PROXIMITY_TAP_THRESHOLD_MS) {
                        takePicture()
                    }
                    isProximityCovered = false
                }
            }
        }
    }

    /**
     * Dispatches a minimal gesture to the system. On many camera apps, any
     * accessibility gesture can trigger the shutter, similar to a key event.
     * This is a robust workaround for restricted key event injection.
     */
    private fun takePicture() {
        val p = Path()
        p.moveTo(1f, 1f) // A minimal path; coordinates are not critical.
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(p, 0, 1))
        dispatchGesture(gestureBuilder.build(), null, null)
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used, but required by the interface.
    }

    override fun onInterrupt() {
        // Not used.
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
    }
}
