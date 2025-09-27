package com.hereliesaz.click

import android.content.SharedPreferences
import kotlin.math.abs

class CameraTriggerHandler(
    private val prefsProvider: () -> SharedPreferences,
    private val clock: Clock
) {

    private var proximityCoverTime = 0L
    private var isProximityCovered = false
    private var lastShakeTime = -1L
    private var lastFingerprintTime = -1L

    companion object {
        private const val PROXIMITY_TAP_THRESHOLD_MS = 500L
        private const val VIBRATION_BASE_THRESHOLD = 10.0
        private const val VIBRATION_MAX_THRESHOLD = 60.0
        private const val VIBRATION_COOLDOWN_MS = 500L
        private const val FINGERPRINT_COOLDOWN_MS = 500L
    }

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