package com.hereliesaz.click

import android.content.SharedPreferences
import kotlin.math.abs

/**
 * A framework-agnostic class that contains the core business logic for detecting
 * various camera trigger events (proximity, vibration, fingerprint). This class is
 * designed to be easily unit-testable.
 *
 * @param prefsProvider A function that provides the current [SharedPreferences] instance
 *                      to access user settings.
 * @param clock A [Clock] implementation to allow for time-dependent logic to be tested.
 */
class CameraTriggerHandler(
    private val prefsProvider: () -> SharedPreferences,
    private val clock: Clock
) {

    private var proximityCoverTime = 0L
    private var isProximityCovered = false
    private var lastShakeTime = -1L
    private var lastFingerprintTime = -1L
    private var lastTapTime = 0L
    private var tapCount = 0

    companion object {
        private const val PROXIMITY_TAP_THRESHOLD_MS = 500L
        private const val VIBRATION_BASE_THRESHOLD = 10.0
        private const val VIBRATION_MAX_THRESHOLD = 60.0
        private const val VIBRATION_COOLDOWN_MS = 500L
        private const val FINGERPRINT_COOLDOWN_MS = 500L
        private const val BACK_TAP_THRESHOLD = 25.0
        private const val BACK_TAP_WINDOW_MS = 500L
        private const val BACK_TAP_COOLDOWN_MS = 1000L
    }

    /**
     * Processes a proximity sensor event to determine if a "tap" gesture occurred.
     * A tap is defined as a brief period where the sensor is covered and then uncovered.
     *
     * @param distance The distance value from the proximity sensor event.
     * @param maxRange The maximum range of the proximity sensor.
     * @return `true` if a tap gesture should trigger a picture, `false` otherwise.
     */
    fun handleProximityEvent(distance: Float, maxRange: Float): Boolean {
        val lensTapProximityEnabled = prefsProvider().getBoolean(MainActivity.KEY_LENS_TAP_PROXIMITY_ENABLED, false)
        if (!lensTapProximityEnabled) return false

        if (distance < maxRange) {
            if (!isProximityCovered) {
                isProximityCovered = true
                proximityCoverTime = clock.uptimeMillis()
            }
        } else {
            if (isProximityCovered) {
                val duration = clock.uptimeMillis() - proximityCoverTime
                isProximityCovered = false
                if (duration < PROXIMITY_TAP_THRESHOLD_MS) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Processes an accelerometer event to determine if a "vibration" or "tap" gesture occurred.
     * This is detected by checking for a sudden, significant change in acceleration, filtered
     * by a user-defined sensitivity setting.
     *
     * @param x The current acceleration force along the x-axis.
     * @param y The current acceleration force along the y-axis.
     * @param z The current acceleration force along the z-axis.
     * @param lastX The previous acceleration force along the x-axis.
     * @param lastY The previous acceleration force along the y-axis.
     * @param lastZ The previous acceleration force along the z-axis.
     * @return `true` if a vibration gesture should trigger a picture, `false` otherwise.
     */
    fun handleAccelerometerEvent(x: Float, y: Float, z: Float, lastX: Float, lastY: Float, lastZ: Float): Boolean {
        val lensTapVibrationEnabled = prefsProvider().getBoolean(MainActivity.KEY_LENS_TAP_VIBRATION_ENABLED, false)
        if (!lensTapVibrationEnabled) return false

        val currentTime = clock.uptimeMillis()
        if (lastShakeTime != -1L && (currentTime - lastShakeTime) < VIBRATION_COOLDOWN_MS) {
            return false
        }

        val sensitivity = prefsProvider().getInt(MainActivity.KEY_VIBRATION_SENSITIVITY, 50)
        val progress = sensitivity / 100.0
        val dynamicThreshold = VIBRATION_MAX_THRESHOLD - (progress * (VIBRATION_MAX_THRESHOLD - VIBRATION_BASE_THRESHOLD))

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)

        if (deltaX > dynamicThreshold || deltaY > dynamicThreshold || deltaZ > dynamicThreshold) {
            lastShakeTime = currentTime
            return true
        }
        return false
    }

    fun handleBackTapEvent(z: Float, lastZ: Float): Boolean {
        val prefs = prefsProvider()
        val backTapEnabled = prefs.getBoolean(MainActivity.KEY_BACK_TAP_ENABLED, false)
        if (!backTapEnabled) return false

        val calibratedThreshold = prefs.getFloat(MainActivity.KEY_BACK_TAP_SENSITIVITY, BACK_TAP_THRESHOLD.toFloat())

        val currentTime = clock.uptimeMillis()
        val deltaZ = abs(z - lastZ)

        if (deltaZ > calibratedThreshold) {
            if (currentTime - lastTapTime > BACK_TAP_WINDOW_MS) {
                tapCount = 1
            } else {
                tapCount++
            }
            lastTapTime = currentTime

            if (tapCount == 2) {
                tapCount = 0
                lastTapTime = currentTime + BACK_TAP_COOLDOWN_MS // Enforce cooldown
                return true
            }
        }

        // Reset tap count if the window expires
        if (currentTime - lastTapTime > BACK_TAP_WINDOW_MS) {
            tapCount = 0
        }

        return false
    }

    /**
     * Processes a fingerprint swipe event. This method uses a simple cooldown
     * to prevent a single swipe from triggering multiple pictures.
     *
     * @return `true` if a fingerprint gesture should trigger a picture, `false` otherwise.
     */
    fun handleFingerprintEvent(): Boolean {
        val fingerprintEnabled = prefsProvider().getBoolean(MainActivity.KEY_FINGERPRINT_ENABLED, false)
        if (!fingerprintEnabled) return false

        val currentTime = clock.uptimeMillis()
        if (lastFingerprintTime == -1L || currentTime - lastFingerprintTime > FINGERPRINT_COOLDOWN_MS) {
            lastFingerprintTime = currentTime
            return true
        }
        return false
    }
}