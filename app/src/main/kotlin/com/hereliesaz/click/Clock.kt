package com.hereliesaz.click

import android.os.SystemClock

/**
 * An interface to abstract away the system clock, allowing for easier testing of time-dependent code.
 */
interface Clock {
    fun uptimeMillis(): Long
}

/**
 * The default implementation of the Clock interface, which uses the Android SystemClock.
 */
class SystemClockImpl : Clock {
    override fun uptimeMillis(): Long {
        return SystemClock.uptimeMillis()
    }
}