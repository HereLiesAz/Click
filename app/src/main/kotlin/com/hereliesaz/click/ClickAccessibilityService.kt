package com.hereliesaz.click

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ScrollView
import kotlin.math.abs

class ClickAccessibilityService : AccessibilityService(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var overlayView: ScrollView? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null

    // Proximity sensor variables
    private var proximityCoverTime = 0L
    private var isProximityCovered = false
    private var isCameraAppActive = false

    // Accelerometer variables
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastShakeTime = 0L

    companion object {
        private const val PROXIMITY_TAP_THRESHOLD_MS = 500L
        private const val VIBRATION_TAP_THRESHOLD = 25.0 // m/s^2
        private const val VIBRATION_COOLDOWN_MS = 500L

        private val CAMERA_PACKAGES = setOf(
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.samsung.android.camera", "com.oneplus.camera", "com.motorola.cameraone",
            "com.sonyericsson.android.camera", "org.codeaurora.snapcam"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun createOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null) as ScrollView

        overlayView?.setOnTouchListener { _, event ->
            val fingerprintEnabled = prefs.getBoolean(MainActivity.KEY_FINGERPRINT_ENABLED, false)
            if (fingerprintEnabled && event.action == MotionEvent.ACTION_MOVE) {
                takePicture()
            }
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val isNowCamera = CAMERA_PACKAGES.contains(packageName)

            if (isNowCamera && !isCameraAppActive) {
                isCameraAppActive = true
                showOverlay()
            } else if (!isNowCamera && isCameraAppActive) {
                isCameraAppActive = false
                hideOverlay()
            }
        }
    }

    private fun showOverlay() {
        if (overlayView?.windowToken == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.END or Gravity.TOP }
            windowManager.addView(overlayView, params)
        }
    }

    private fun hideOverlay() {
        if (overlayView?.windowToken != null) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isCameraAppActive) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> handleProximityEvent(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometerEvent(event)
        }
    }

    private fun handleProximityEvent(event: SensorEvent) {
        val lensTapProximityEnabled = prefs.getBoolean(MainActivity.KEY_LENS_TAP_PROXIMITY_ENABLED, false)
        if (!lensTapProximityEnabled) return

        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: distance

        if (distance < maxRange) {
            if (!isProximityCovered) {
                isProximityCovered = true
                proximityCoverTime = SystemClock.uptimeMillis()
            }
        } else {
            if (isProximityCovered) {
                val duration = SystemClock.uptimeMillis() - proximityCoverTime
                if (duration in 51..PROXIMITY_TAP_THRESHOLD_MS) {
                    takePicture()
                }
                isProximityCovered = false
            }
        }
    }

    private fun handleAccelerometerEvent(event: SensorEvent) {
        val lensTapVibrationEnabled = prefs.getBoolean(MainActivity.KEY_LENS_TAP_VIBRATION_ENABLED, false)
        if (!lensTapVibrationEnabled) return

        val currentTime = SystemClock.uptimeMillis()
        if ((currentTime - lastShakeTime) < VIBRATION_COOLDOWN_MS) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)

        if (deltaX > VIBRATION_TAP_THRESHOLD || deltaY > VIBRATION_TAP_THRESHOLD || deltaZ > VIBRATION_TAP_THRESHOLD) {
            lastShakeTime = currentTime
            takePicture()
        }

        lastX = x
        lastY = y
        lastZ = z
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun takePicture() {
        val p = Path()
        p.moveTo(1f, 1f)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(p, 0, 1))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        sensorManager?.unregisterListener(this)
    }
}
