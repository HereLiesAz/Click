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
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ScrollView

class ClickAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ScrollView? = null

    private var isCameraAppActive = false

    companion object {
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
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun createOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null) as ScrollView

        // We listen for any touch, assuming a fingerprint scroll gesture will manifest as a move event.
        overlayView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE) {
                takePicture()
            }
            // Return false so we don't consume the event, allowing the view to scroll naturally.
            false
        }
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val isNowCamera = CAMERA_PACKAGES.contains(packageName)

            if (isNowCamera && !isCameraAppActive) {
                // Camera app just became active
                isCameraAppActive = true
                showOverlay()
            } else if (!isNowCamera && isCameraAppActive) {
                // Camera app is no longer active
                isCameraAppActive = false
                hideOverlay()
            }
        }
    }

    private fun showOverlay() {
        if (overlayView?.windowToken == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.TOP
            }
            windowManager.addView(overlayView, params)
        }
    }

    private fun hideOverlay() {
        if (overlayView?.windowToken != null) {
            windowManager.removeView(overlayView)
        }
    }


    /**
     * Dispatches a minimal gesture to the system. This is a robust
     * workaround for restricted key event injection.
     */
    private fun takePicture() {
        val p = Path()
        p.moveTo(1f, 1f) // A minimal path; coordinates are not critical.
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(p, 0, 1))
        dispatchGesture(gestureBuilder.build(), null, null)
    }


    override fun onInterrupt() {
        // Not used.
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
}

