package com.hereliesaz.click

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class GestureCaptureActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    companion object {
        const val KEY_SHUTTER_X = "shutter_x"
        const val KEY_SHUTTER_Y = "shutter_y"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture_capture)
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.rawX
            val y = event.rawY

            prefs.edit()
                .putFloat(KEY_SHUTTER_X, x)
                .putFloat(KEY_SHUTTER_Y, y)
                .apply()

            Toast.makeText(this, "Shutter location saved!", Toast.LENGTH_SHORT).show()
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }
}