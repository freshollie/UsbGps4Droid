package org.broeuschmeul.android.gps.usb.provider.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import org.broeuschmeul.android.gps.usb.provider.R;

public class SettingsActivity extends USBGpsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isDoublePanelAvailable()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        showSettingsFragment(R.id.settings_content, true);
    }

    private boolean isDoublePanelAvailable() {
        return (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE &&
                getResources()
                        .getConfiguration()
                        .orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
}
