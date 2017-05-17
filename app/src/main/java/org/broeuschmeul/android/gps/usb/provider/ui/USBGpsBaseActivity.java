package org.broeuschmeul.android.gps.usb.provider.ui;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import org.broeuschmeul.android.gps.usb.provider.USBGpsApplication;
import org.broeuschmeul.android.gps.usb.provider.R;
import org.broeuschmeul.android.gps.usb.provider.driver.USBGpsProviderService;

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

public abstract class USBGpsBaseActivity extends AppCompatActivity implements
        USBGpsSettingsFragment.PreferenceScreenListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences sharedPreferences;
    private String TAG_NESTED = "NESTED_PREFERENCE_SCREEN";

    private boolean shouldInitialise = true;

    private int resSettingsHolder;
    private boolean tryingToStart;

    private static final int LOCATION_REQUEST = 238472383;
    private static final int STORAGE_REQUEST = 8972842;

    private boolean homeAsUp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (savedInstanceState != null) {
            shouldInitialise = false;
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !USBGpsApplication.wasLocationAsked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                USBGpsApplication.setLocationAsked();
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_REQUEST);
            }
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        // Basically check the service is really running
        if (!isServiceRunning()) {
            sharedPreferences
                    .edit()
                    .putBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false)
                    .apply();
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * @param whereId where the fragment needs to be shown in.
     */
    public void showSettingsFragment(int whereId, boolean homeAsUp) {
        resSettingsHolder = whereId;
        // Opens the root fragment if its the first time opening
        if (shouldInitialise) {
            getFragmentManager().beginTransaction()
                    .add(whereId, new USBGpsSettingsFragment())
                    .commit();
        }

        this.homeAsUp = homeAsUp;

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
                                        try {
                                            startActivity(
                                                    new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            );
                                        } catch (ActivityNotFoundException e) {
                                            new AlertDialog.Builder(USBGpsBaseActivity.this)
                                                    .setMessage(R.string.warning_no_developer_options)
                                                    .setPositiveButton(android.R.string.ok, null)
                                                    .show();
                                        }
                                    }
                                })
                        .show();
            } else {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
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
    }

    /**
     * Checks if the applications has the given runtime permission
     * @param perm
     * @return
     */
    private boolean hasPermission(String perm) {
        return (
                PackageManager.PERMISSION_GRANTED ==
                        ContextCompat.checkSelfPermission(this, perm)
        );
    }

    /**
     * Android 6.0 requires permissions to be accepted at runtime
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_REQUEST) {
            if (hasPermission(permissions[0])) {
                if (tryingToStart) {
                    tryingToStart = false;

                    Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                    serviceIntent.setAction(USBGpsProviderService.ACTION_START_GPS_PROVIDER);
                    startService(serviceIntent);
                }

            } else {
                tryingToStart = false;

                sharedPreferences.edit()
                        .putBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false)
                        .apply();

                new AlertDialog.Builder(this)
                        .setMessage("Location permission is required for UsbGps to function")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();

            }

        } else if (requestCode == STORAGE_REQUEST) {
            if (hasPermission(permissions[0])) {
                Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                serviceIntent.setAction(USBGpsProviderService.ACTION_START_TRACK_RECORDING);
                startService(serviceIntent);

            } else {
                sharedPreferences
                        .edit()
                        .putBoolean(USBGpsProviderService.PREF_TRACK_RECORDING, false)
                        .apply();

                new AlertDialog.Builder(this)
                        .setMessage(
                                "In order to write a track file, the app need storage permission"
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show();

            }
        }
    }

    /**
     * If the service is killed then the shared preference for the service is never updated.
     * This checks if the service is running from the running preferences list
     */
    public boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (USBGpsProviderService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles service attributes changing and requesting permissions
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case USBGpsProviderService.PREF_START_GPS_PROVIDER: {
                boolean val = sharedPreferences.getBoolean(key, false);

                if (val) {

                    // If we have location permission then we can start the service
                    if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        if (!isServiceRunning()) {
                            Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                            serviceIntent.setAction(USBGpsProviderService.ACTION_START_GPS_PROVIDER);
                            startService(serviceIntent);
                        }


                    } else {
                        // Other wise we need to request for the permission
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            tryingToStart = true;
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    LOCATION_REQUEST);
                        }
                    }

                } else {
                    // Will show a stop dialog if needed
                    showStopDialog();

                    if (isServiceRunning()) {
                        Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                        serviceIntent.setAction(USBGpsProviderService.ACTION_STOP_GPS_PROVIDER);
                        startService(serviceIntent);
                    }
                }

                break;
            }
            case USBGpsProviderService.PREF_TRACK_RECORDING: {
                boolean val = sharedPreferences.getBoolean(key, false);

                if (val) {
                    if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    STORAGE_REQUEST);
                        }
                    } else {
                        if (sharedPreferences.getBoolean(
                                USBGpsProviderService.PREF_START_GPS_PROVIDER, false)) {
                            Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                            serviceIntent.setAction(USBGpsProviderService.ACTION_START_TRACK_RECORDING);
                            startService(serviceIntent);
                        }
                    }

                } else {
                    if (sharedPreferences.getBoolean(
                            USBGpsProviderService.PREF_START_GPS_PROVIDER, false)) {
                        Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                        serviceIntent.setAction(USBGpsProviderService.ACTION_STOP_TRACK_RECORDING);
                        startService(serviceIntent);
                    }
                }

                break;
            }
            case USBGpsProviderService.PREF_SIRF_GPS: {
                if (sharedPreferences.getBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false)) {
                    if (sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_GPS, false)) {
                        Intent configIntent = new Intent(this, USBGpsProviderService.class);
                        configIntent.setAction(USBGpsProviderService.ACTION_CONFIGURE_SIRF_GPS);
                        startService(configIntent);
                    }
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
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
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(homeAsUp);
            }
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
