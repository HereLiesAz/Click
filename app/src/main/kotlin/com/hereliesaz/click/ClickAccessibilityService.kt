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

/**
 * An Accessibility Service that detects when a camera app is in the foreground and
 * provides alternative methods to trigger the camera shutter using device sensors.
 *
 * This service listens for:
 * - `TYPE_WINDOW_STATE_CHANGED` events to determine if a known camera app is active.
 * - `Sensor.TYPE_PROXIMITY` events to detect a "tap" near the earpiece.
 * - `Sensor.TYPE_ACCELEROMETER` events to detect a physical tap on the device.
 * - `MotionEvent` on a transparent overlay to detect fingerprint scroll gestures.
 */
class ClickAccessibilityService : AccessibilityService(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var overlayView: ScrollView? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null

    // Proximity sensor state variables
    private var proximityCoverTime = 0L
    private var isProximityCovered = false
    private var isCameraAppActive = false

    // Accelerometer state variables
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastShakeTime = 0L

    companion object {
        /** The maximum duration in milliseconds for a proximity sensor occlusion to be considered a "tap". */
        private const val PROXIMITY_TAP_THRESHOLD_MS = 500L
        /** The base acceleration threshold for the vibration tap. This is the value for 100% sensitivity. */
        private const val VIBRATION_BASE_THRESHOLD = 10.0
        /** The maximum acceleration threshold for the vibration tap. This is the value for 0% sensitivity. */
        private const val VIBRATION_MAX_THRESHOLD = 60.0
        /** The cooldown period in milliseconds to prevent multiple triggers from a single vibration event. */
        private const val VIBRATION_COOLDOWN_MS = 500L

        /** A set of package names for common camera applications. */
        private val CAMERA_PACKAGES = setOf(
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.samsung.android.camera", "com.oneplus.camera", "com.motorola.cameraone",
            "com.sonyericsson.android.camera", "org.codeaurora.snapcam"
        )
    }

    /**
     * Called when the service is connected. Initializes SharedPreferences, system services, and sensors.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    /**
     * Registers the sensor listeners to start detecting events.
     * This is called only when a camera app becomes active.
     */
    private fun registerSensors() {
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    /**
     * Unregisters the sensor listeners to save battery.
     * This is called when a camera app is no longer active.
     */
    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Inflates and configures the transparent overlay view used for the fingerprint scroll trigger.
     */
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

    /**
     * Handles `TYPE_WINDOW_STATE_CHANGED` events to detect when a camera app is active or inactive,
     * controlling the visibility of the overlay and the registration of sensor listeners.
     * @param event The accessibility event.
     */
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

    /**
     * Adds the overlay view to the window manager if it's not already shown.
     */
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

    /**
     * Removes the overlay view from the window manager if it is currently shown.
     */
    private fun hideOverlay() {
        if (overlayView?.windowToken != null) {
            windowManager.removeView(overlayView)
        }
    }

    /**
     * Routes sensor events to the appropriate handler based on the sensor type.
     * @param event The sensor event.
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (!isCameraAppActive) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> handleProximityEvent(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometerEvent(event)
        }
    }

    /**
     * Handles proximity sensor logic for the 'tap' feature.
     * @param event The proximity sensor event.
     */
    private fun handleProximityEvent(event: SensorEvent) {
        val lensTapProximityEnabled = prefs.getBoolean(MainActivity.KEY_LENS_TAP_PROXIMITY_ENABLED, false)
        if (!lensTapProximityEnabled) return

        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: distance

        if (distance < maxRange) { // Sensor is covered
            if (!isProximityCovered) {
                isProximityCovered = true
                proximityCoverTime = SystemClock.uptimeMillis()
            }
        } else { // Sensor is uncovered
            if (isProximityCovered) {
                val duration = SystemClock.uptimeMillis() - proximityCoverTime
                if (duration in 51..PROXIMITY_TAP_THRESHOLD_MS) {
                    takePicture()
                }
                isProximityCovered = false
            }
        }
    }

    /**
     * Handles accelerometer logic for the 'vibration' feature, using a dynamic threshold
     * based on the user's sensitivity setting.
     * @param event The accelerometer event.
     */
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
        // We map the seekbar progress [0, 100] to our desired threshold range.
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

    /** Required by SensorEventListener, but not used here. */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Dispatches a generic gesture (a single point tap) to the system, which most
     * camera apps interpret as a command to take a picture.
     */
    private fun takePicture() {
        val p = Path()
        p.moveTo(1f, 1f) // A minimal path
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(p, 0, 1))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * Called when the service is interrupted. Cleans up resources to prevent leaks.
     */
    override fun onInterrupt() {
        isCameraAppActive = false
        hideOverlay()
        unregisterSensors()
    }

    /**
     * Called when the service is being destroyed. Cleans up resources.
     */
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        unregisterSensors()
    }
}
