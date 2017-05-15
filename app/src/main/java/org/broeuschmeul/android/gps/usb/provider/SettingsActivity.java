package org.broeuschmeul.android.gps.usb.provider;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;

public class SettingsActivity extends UsbGpsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        showSettingsFragment(R.id.settings_content);
    }
}
