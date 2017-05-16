package org.broeuschmeul.android.gps.usb.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Switch;

import org.broeuschmeul.android.gps.usb.USBGpsApplication;
import org.broeuschmeul.android.gps.usb.provider.R;
import org.broeuschmeul.android.gps.usb.provider.USBGpsProviderService;

/**
 * Preferences activity was deprecated and so now we make a preferences
 * fragment and make an activity to display that fragment.
 * <p>
 * All this activity does is display the fragment, the fragment handles
 * everything else.
 * <p>
 * Created by Oliver Bell 5/12/15
 */

public class GpsInfoActivity extends USBGpsBaseActivity implements
        USBGpsApplication.ServiceDataListener {

    private SharedPreferences sharedPreferences;
    private static final String TAG = GpsInfoActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void setupSwitch() {
        Switch serviceSwitch = (Switch) findViewById(R.id.service_start_switch);
    }

    private void updateData() {

    }

    @Override
    public void onResume() {
        ((USBGpsApplication) getApplication()).registerServiceDataListener(this);
        super.onResume();
    }

    @Override
    public void onPause() {
        ((USBGpsApplication) getApplication()).unregisterServiceDataListener(this);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNewSentence(String sentence) {
        Log.v(TAG, sentence);
    }

    @Override
    public void onLocationNotified(Location location) {

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);

        if (key.equals(USBGpsProviderService.PREF_START_GPS_PROVIDER)) {

        }
    }
}
