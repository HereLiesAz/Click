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
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ScrollView

/**
 * An Accessibility Service that detects when a camera app is in the foreground and
 * provides alternative methods to trigger the camera shutter using device sensors.
 *
 * This service is responsible for:
 * - Interacting with the Android Framework (WindowManager, SensorManager).
 * - Listening for `TYPE_WINDOW_STATE_CHANGED` events to detect when a camera app is active.
 * - Delegating the actual trigger logic to a [CameraTriggerHandler].
 */
class ClickAccessibilityService : AccessibilityService(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private var overlayView: ScrollView? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private lateinit var triggerHandler: CameraTriggerHandler

    // State variables for the service
    private var isCameraAppActive = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    companion object {
        /** A set of package names for common camera applications. */
        private val CAMERA_PACKAGES = setOf(
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.samsung.android.camera", "com.oneplus.camera", "com.motorola.cameraone",
            "com.sonyericsson.android.camera", "org.codeaurora.snapcam"
        )
    }

    /**
     * Called when the service is connected. Initializes system services and the trigger handler.
     */
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

    /**
     * Registers the sensor listeners to start detecting events.
     * This is called only when a camera app becomes active.
     */
    private fun registerSensors() {
        proximitySensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
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
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null) as ScrollView
        overlayView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE && triggerHandler.handleFingerprintEvent()) {
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
     * Adds the overlay view to the window manager if it's not already shown and if the
     * app has the necessary permission to draw overlays.
     */
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

    /**
     * Removes the overlay view from the window manager if it is currently shown.
     */
    private fun hideOverlay() {
        if (overlayView?.windowToken != null) {
            windowManager.removeView(overlayView)
        }
    }

    /**
     * Called when sensor values have changed. This method routes the event to the
     * [CameraTriggerHandler] to process the logic.
     * @param event The sensor event.
     */
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
                if (triggerHandler.handleBackTapEvent(z, lastZ)) {
                    takePicture()
                }
                else if (triggerHandler.handleAccelerometerEvent(x, y, z, lastX, lastY, lastZ)) {
                    takePicture()
                }
                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (triggerHandler.handleVolumeKeyEvent()) {
                        takePicture()
                    }
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    /** Required by SensorEventListener, but not used here. */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (triggerHandler.handleVolumeKeyEvent()) {
                takePicture()
                // The event is handled, so prevent it from propagating further (e.g., changing the volume).
                return true
            }
        }
        // For all other keys, let the system handle them as usual.
        return super.onKeyEvent(event)
    }

    /**
     * Dispatches a tap gesture to take a picture.
     * If custom shutter coordinates are saved, it uses them. Otherwise, it defaults
     * to tapping the center of the screen.
     */
    private fun takePicture() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val x = prefs.getFloat(MainActivity.KEY_SHUTTER_X, -1f)
        val y = prefs.getFloat(MainActivity.KEY_SHUTTER_Y, -1f)

        val tapPath = Path()

        if (x != -1f && y != -1f) {
            // Use the saved coordinates if they exist
            tapPath.moveTo(x, y)
        } else {
            // Fallback to the center of the screen as a default
            val displayMetrics = resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2f
            val centerY = displayMetrics.heightPixels / 2f
            tapPath.moveTo(centerX, centerY)
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(tapPath, 0, 1))
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