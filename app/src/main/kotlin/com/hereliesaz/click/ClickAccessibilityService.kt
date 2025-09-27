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
        // Base threshold. This will be scaled by the user's sensitivity setting.
        private const val VIBRATION_BASE_THRESHOLD = 10.0
        // Max threshold, so 100% sensitivity is not impossible to trigger
        private const val VIBRATION_MAX_THRESHOLD = 60.0
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
    }

    private fun registerSensors() {
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
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
                registerSensors()
            } else if (!isNowCamera && isCameraAppActive) {
                isCameraAppActive = false
                hideOverlay()
                unregisterSensors()
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

        // Calculate dynamic threshold based on user sensitivity setting (0-100)
        val sensitivity = prefs.getInt(MainActivity.KEY_VIBRATION_SENSITIVITY, 50)
        // A lower sensitivity progress means a higher threshold is needed.
        // We map progress [0, 100] to a threshold range.
        val progress = sensitivity / 100.0
        val dynamicThreshold = VIBRATION_MAX_THRESHOLD - (progress * (VIBRATION_MAX_THRESHOLD - VIBRATION_BASE_THRESHOLD))


        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)

        if (deltaX > dynamicThreshold || deltaY > dynamicThreshold || deltaZ > dynamicThreshold) {
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

    override fun onInterrupt() {
        isCameraAppActive = false
        hideOverlay()
        unregisterSensors()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        unregisterSensors()
    }
}
