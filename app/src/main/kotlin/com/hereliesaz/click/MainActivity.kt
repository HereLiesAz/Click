package com.hereliesaz.click;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView serviceStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceStatusText = findViewById(R.id.service_status_text);
        Button enableServiceButton = findViewById(R.id.enable_service_button);

        enableServiceButton.setOnClickListener(v -> {
            // Open the accessibility settings screen
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
    }

    private void updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, ClickAccessibilityService.class)) {
            serviceStatusText.setText("Service Status: Enabled");
            serviceStatusText.setBackgroundColor(0xFFC8E6C9); // A pleasant green
        } else {
            serviceStatusText.setText("Service Status: Disabled");
            serviceStatusText.setBackgroundColor(0xFFFFCDD2); // A cautionary red
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (settingValue != null) {
            colonSplitter.setString(settingValue);
            while (colonSplitter.hasNext()) {
                String componentName = colonSplitter.next();
                if (componentName.equalsIgnoreCase(context.getPackageName() + "/" + accessibilityService.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
