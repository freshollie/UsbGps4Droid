package org.broeuschmeul.android.gps.usb.provider;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

/**
 * Created by freshollie on 15/05/17.
 *
 * Any activity in this app extends this activity.
 *
 * This Activity will show the stop dialogs and take care of permissions.
 *
 * It will also show the settings in a given layout ID and handle
 * the nested settings.
 */

public abstract class UsbGpsActivity extends AppCompatActivity implements
        USBGpsSettingsFragment.PreferenceScreenListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences sharedPreferences;
    private String TAG_NESTED = "NESTED_PREFERENCE_SCREEN";

    private boolean shouldInitialise = true;

    private int resSettingsHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        if (savedInstanceState != null) {
            shouldInitialise = false;
        }

        super.onCreate(savedInstanceState);
    }

    /**
     * @param whereId where the fragment needs to be shown in.
     */
    public void showSettingsFragment(int whereId) {
        resSettingsHolder = whereId;
        // Opens the root fragment if its the first time opening
        if (shouldInitialise) {
            getFragmentManager().beginTransaction()
                    .add(whereId, new USBGpsSettingsFragment())
                    .commit();
        }
    }

    private void clearStopNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.string.service_closed_because_connection_problem_notification_title);
    }

    private void showStopDialog() {
        int reason = sharedPreferences.getInt(getString(R.string.pref_disable_reason_key), 0);

        if (reason > 0) {
            if (reason == R.string.msg_mock_location_disabled) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.service_closed_because_connection_problem_notification_title)
                        .setMessage(
                                getString(
                                        R.string.service_closed_because_connection_problem_notification,
                                        getString(R.string.msg_mock_location_disabled)
                                )
                        )
                        .setPositiveButton("Open mock location settings",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        clearStopNotification();
                                        startActivity(
                                                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        );
                                    }
                                })
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.service_closed_because_connection_problem_notification_title)
                        .setMessage(
                                getString(
                                        R.string.service_closed_because_connection_problem_notification,
                                        getString(reason)
                                )
                        )
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                clearStopNotification();
                            }
                        })
                        .show();
            }
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case USBGpsProviderService.PREF_START_GPS_PROVIDER: {
                if (!sharedPreferences
                        .getBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false)) {
                    showStopDialog();
                }
            }
        }
    }

    /**
     * Called when a nested preference screen is clicked by the root preference screen
     *
     * Makes that fragment the now visible fragment
     */
    @Override
    public void onNestedScreenClicked(PreferenceFragment preferenceFragment) {
        getFragmentManager().beginTransaction()
                .replace(resSettingsHolder, preferenceFragment, TAG_NESTED)
                .addToBackStack(TAG_NESTED)
                .commit();
    }


    @Override
    public void onBackPressed() {
        // this if statement is necessary to navigate through nested and main fragments
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            super.onBackPressed();
        } else {
            getFragmentManager().popBackStack();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    public void onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
