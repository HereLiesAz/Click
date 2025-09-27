package com.hereliesaz.click

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ScrollView

class ClickAccessibilityService : AccessibilityService(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private var overlayView: ScrollView? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private lateinit var triggerHandler: CameraTriggerHandler

    private var isCameraAppActive = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    companion object {
        private val CAMERA_PACKAGES = setOf(
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.samsung.android.camera", "com.oneplus.camera", "com.motorola.cameraone",
            "com.sonyericsson.android.camera", "org.codeaurora.snapcam"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        triggerHandler = CameraTriggerHandler(
            prefsProvider = { getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE) },
            clock = SystemClockImpl()
        )
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createOverlayView()
    }

    private fun registerSensors() {
        proximitySensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    private fun createOverlayView() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null) as ScrollView
        overlayView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE && triggerHandler.handleFingerprintEvent()) {
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
        if (overlayView?.windowToken == null && Settings.canDrawOverlays(this)) {
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
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: distance
                if (triggerHandler.handleProximityEvent(distance, maxRange)) {
                    takePicture()
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                if (triggerHandler.handleAccelerometerEvent(x, y, z, lastX, lastY, lastZ)) {
                    takePicture()
                }
                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun takePicture() {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        val p = Path()
        p.moveTo(centerX, centerY)
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