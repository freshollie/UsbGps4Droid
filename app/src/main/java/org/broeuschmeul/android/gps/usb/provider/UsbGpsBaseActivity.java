package org.broeuschmeul.android.gps.usb.provider;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
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

public abstract class UsbGpsBaseActivity extends AppCompatActivity implements
        USBGpsSettingsFragment.PreferenceScreenListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences sharedPreferences;
    private String TAG_NESTED = "NESTED_PREFERENCE_SCREEN";

    private boolean shouldInitialise = true;

    private int resSettingsHolder;
    private boolean tryingToStart;

    private static final int LOCATION_REQUEST = 238472383;
    private static final int STORAGE_REQUEST = 8972842;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        if (savedInstanceState != null) {
            shouldInitialise = false;
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_REQUEST);
            }
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
                sharedPreferences.edit().putBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false)
                        .apply();
                new AlertDialog.Builder(this)
                        .setMessage("Location access needs to be enabled for this app to function")
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

                new AlertDialog.Builder(this).setMessage(
                        "In order to write a track file, the app need storage permission")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();

            }
        }
    }


    /**
     * Handles service attributes changing and requesting permissions
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case USBGpsProviderService.PREF_START_GPS_PROVIDER: {
                boolean val = sharedPreferences.getBoolean(key, false);

                if (!sharedPreferences
                        .getBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false)) {
                    showStopDialog();
                }

                if (val) {
                    if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                        serviceIntent.setAction(USBGpsProviderService.ACTION_START_GPS_PROVIDER);
                        startService(serviceIntent);


                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            tryingToStart = true;
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    LOCATION_REQUEST);
                        }
                    }

                } else {
                    Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                    serviceIntent.setAction(USBGpsProviderService.ACTION_STOP_GPS_PROVIDER);
                    startService(serviceIntent);
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
                        Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                        serviceIntent.setAction(USBGpsProviderService.ACTION_START_TRACK_RECORDING);
                        startService(serviceIntent);
                    }

                } else {
                    Intent serviceIntent = new Intent(this, USBGpsProviderService.class);
                    serviceIntent.setAction(USBGpsProviderService.ACTION_STOP_TRACK_RECORDING);
                    startService(serviceIntent);
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
