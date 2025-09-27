package com.hereliesaz.click

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var serviceStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serviceStatusText = findViewById(R.id.service_status_text)
        val enableServiceButton: Button = findViewById(R.id.enable_service_button)

        enableServiceButton.setOnClickListener {
            // Open the accessibility settings screen
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, ClickAccessibilityService::class.java)) {
            serviceStatusText.text = "Service Status: Enabled"
            serviceStatusText.setBackgroundColor(0xFFC8E6C9.toInt()) // A pleasant green
        } else {
            serviceStatusText.text = "Service Status: Disabled"
            serviceStatusText.setBackgroundColor(0xFFFFCDD2.toInt()) // A cautionary red
        }
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            val settingValue = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

            settingValue?.let {
                colonSplitter.setString(it)
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.equals(context.packageName + "/" + accessibilityService.name, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
