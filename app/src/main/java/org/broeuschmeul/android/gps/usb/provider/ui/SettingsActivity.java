package org.broeuschmeul.android.gps.usb.provider.ui;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import org.broeuschmeul.android.gps.usb.provider.R;

public class SettingsActivity extends USBGpsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        showSettingsFragment(R.id.settings_content, true);
    }
}
