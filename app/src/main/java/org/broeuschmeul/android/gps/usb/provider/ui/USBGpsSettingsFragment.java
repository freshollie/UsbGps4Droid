/*
 * Copyright (C) 2016 Oliver Bell
 * Copyright (C) 2010, 2011, 2012 Herbert von Broeuschmeul
 * Copyright (C) 2010, 2011, 2012 BluetoothGPS4Droid Project
 * Copyright (C) 2011, 2012 UsbGPS4Droid Project
 * 
 * This file is part of UsbGPS4Droid.
 *
 * UsbGPS4Droid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * UsbGPS4Droid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with UsbGPS4Droid. If not, see <http://www.gnu.org/licenses/>.
 */

package org.broeuschmeul.android.gps.usb.provider.ui;

import java.util.HashMap;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.gpstest.GpsTestActivity;

import org.broeuschmeul.android.gps.usb.provider.BuildConfig;
import org.broeuschmeul.android.gps.usb.provider.R;
import org.broeuschmeul.android.gps.usb.provider.driver.USBGpsProviderService;
import org.broeuschmeul.android.gps.usb.provider.util.SuperuserManager;

/**
 * A Preference Fragment Class used to configure the provider
 *
 * Starting any services will trigger events in the base usb gps activities
 *
 * @author Herbert von Broeuschmeul
 */
public class USBGpsSettingsFragment extends PreferenceFragmentCompat implements
        OnPreferenceChangeListener, OnSharedPreferenceChangeListener {


    // Checks for usb devices that connect while the screen is active
    private Runnable usbCheckRunnable = new Runnable() {
        @Override
        public void run() {
            int lastNum = usbManager.getDeviceList().values().size();

            while (!Thread.interrupted()) {
                int newNum = usbManager.getDeviceList().values().size();

                if (lastNum != newNum) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateDevicePreferenceSummary();
                            updateDevicesList();
                        }
                    });
                    lastNum = newNum;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }

            log("USB Device Check thread ending");
        }
    };

    private Thread usbCheckThread;

    /**
     * Tag used for log messages
     */
    private static final String TAG = USBGpsSettingsFragment.class.getSimpleName();

    public static int DEFAULT_GPS_PRODUCT_ID = 8963;
    public static int DEFAULT_GPS_VENDOR_ID = 1659;

    private SharedPreferences sharedPreferences;
    private ListPreference devicePreference;
    private ListPreference deviceSpeedPreference;

    private UsbManager usbManager;
    private ActivityManager activityManager;

    private Handler mainHandler;

    private PreferenceScreenListener preferenceScreenParent;

    // Used to allow for nested preference screens in an android fragment
    public interface PreferenceScreenListener {
        void onNestedScreenClicked(PreferenceFragmentCompat preferenceFragment);
    }

    /**
     * Called when the fragment is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.main_prefs);

        devicePreference = (ListPreference) findPreference(USBGpsProviderService.PREF_GPS_DEVICE);
        deviceSpeedPreference = (ListPreference) findPreference(USBGpsProviderService.PREF_GPS_DEVICE_SPEED);
        devicePreference.setOnPreferenceChangeListener(this);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        activityManager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        mainHandler = new Handler(getActivity().getMainLooper());

        setupNestedPreferences();
    }

    private void onDaynightModeChanged(boolean on) {
        AppCompatDelegate.setDefaultNightMode(on ?
                AppCompatDelegate.MODE_NIGHT_AUTO:
                AppCompatDelegate.MODE_NIGHT_YES
        );
        getActivity().recreate();
    }

    private void setupNestedPreferences() {
        findPreference(USBGpsProviderService.PREF_ABOUT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                displayAboutDialog();
                return true;
            }
        });

        findPreference(getString(R.string.pref_gps_location_provider_key))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (preferenceScreenParent != null) {
                            preferenceScreenParent.onNestedScreenClicked(new ProviderPreferences());
                        }
                        return false;
                    }
                });

        findPreference(getString(R.string.pref_sirf_screen_key))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (preferenceScreenParent != null) {
                            preferenceScreenParent.onNestedScreenClicked(new SirfPreferences());
                        }
                        return false;
                    }
                });

        findPreference(getString(R.string.pref_recording_screen_key))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (preferenceScreenParent != null) {
                            preferenceScreenParent.onNestedScreenClicked(new RecordingPreferences());
                        }
                        return false;
                    }
                });

        findPreference("launchGpsTest")
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(
                                new Intent(
                                        getActivity(),
                                        GpsTestActivity.class
                                )
                        );
                        return false;
                    }
                });

        findPreference(getString(R.string.pref_daynight_theme_key))
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        onDaynightModeChanged((boolean) newValue);
                        return true;
                    }
                });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onAttach(Activity context) {
        super.onAttach(context);

        if (context instanceof PreferenceScreenListener) {
            preferenceScreenParent = (PreferenceScreenListener) context;
        } else {
            throw new IllegalStateException("Owner must implement PreferenceScreenListener interface");
        }
    }

    @Override
    public void onResume() {
        usbCheckThread = new Thread(usbCheckRunnable);
        usbCheckThread.start();

        final CheckBoxPreference timePreference =
                (CheckBoxPreference) findPreference(USBGpsProviderService.PREF_SET_TIME);

        if (!SuperuserManager.getInstance().hasPermission() && timePreference.isChecked()) {
            SuperuserManager.getInstance().request(new SuperuserManager.permissionListener() {
                @Override
                public void onGranted() {
                }

                @Override
                public void onDenied() {
                    new Handler(getActivity().getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            timePreference.setChecked(false);
                        }
                    });
                }
            });
        }

        updateDevicePreferenceList();
        updateDevicesList();
        super.onResume();
    }

    @Override
    public void onPause() {
        if (usbCheckThread != null) {
            usbCheckThread.interrupt();
        }

        super.onPause();
    }

    /**
     * If the service is killed then the shared preference for the service is never updated.
     * This checks if the service is running from the running preferences list
     */
    private boolean isServiceRunning() {
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (USBGpsProviderService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates the device summary based on the connected devices.
     */
    private void updateDevicePreferenceSummary() {
        if (devicePreference != null) {
            devicePreference.setValue("current");

            devicePreference.setSummary(getString(R.string.pref_gps_device_summary, getSelectedDeviceSummary()));
            deviceSpeedPreference.setSummary(getString(
                    R.string.pref_gps_device_speed_summary,
                    sharedPreferences.getString(
                            USBGpsProviderService.PREF_GPS_DEVICE_SPEED, getString(R.string.defaultGpsDeviceSpeed))
                    )
            );
        }
    }

    /**
     * Gets a summary of the current select product and vendor ids
     */
    private String getSelectedDeviceSummary() {
        int productId = sharedPreferences.getInt(
                USBGpsProviderService.PREF_GPS_DEVICE_PRODUCT_ID, DEFAULT_GPS_PRODUCT_ID);
        int vendorId = sharedPreferences.getInt(
                USBGpsProviderService.PREF_GPS_DEVICE_VENDOR_ID, DEFAULT_GPS_VENDOR_ID);

        String deviceDisplayedName = "Device not connected - " + vendorId + ": " + productId;

        for (UsbDevice usbDevice: usbManager.getDeviceList().values()) {
            if (usbDevice.getVendorId() == vendorId && usbDevice.getProductId() == productId) {
                deviceDisplayedName =
                        "USB " + usbDevice.getDeviceProtocol() + " " + usbDevice.getDeviceName() +
                                " | " + vendorId + ": " + productId;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    deviceDisplayedName = usbDevice.getManufacturerName() + usbDevice.getProductName() +
                            " | " + vendorId + ": " + productId;
                }

                break;
            }
        }

        return deviceDisplayedName;
    }

    /**
     * Updates the list of available devices in the list preference
     */
    private void updateDevicesList() {
        HashMap<String, UsbDevice> connectedUsbDevices = usbManager.getDeviceList();
        String[] entryValues = new String[connectedUsbDevices.size()];
        String[] entries = new String[connectedUsbDevices.size()];

        int i = 0;
        // Loop through usb devices
        for (UsbDevice device : connectedUsbDevices.values()) {
            // Add the name and address to the ListPreference entities and entyValues

            String entryValue = device.getDeviceName() +
                    " - " + device.getVendorId() + " : " + device.getProductId();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                entryValue = device.getManufacturerName() + " " + device.getProductName() +
                        " - " + device.getVendorId() + " : " + device.getProductId();
            }

            entryValues[i] = device.getDeviceName();
            entries[i] = entryValue;
            i++;
        }

        devicePreference.setEntryValues(entryValues);
        devicePreference.setEntries(entries);
    }

    /**
     * Refreshes all of the preferences on the screen
     */
    private void updateDevicePreferenceList() {
        Preference pref;

        String mockProvider = sharedPreferences.getString(USBGpsProviderService.PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));

        // update usb device summary
        updateDevicePreferenceSummary();

        // update usb device list
        updateDevicesList();

        pref = findPreference(USBGpsProviderService.PREF_GPS_LOCATION_PROVIDER);
        if (sharedPreferences.getBoolean(USBGpsProviderService.PREF_REPLACE_STD_GPS, true)) {
            String s = getString(R.string.pref_gps_location_provider_summary);
            pref.setSummary(s);
            log("loc. provider: " + s);
        } else {
            String s = getString(R.string.pref_mock_gps_name_summary, mockProvider);
            pref.setSummary(s);
            log("loc. provider: " + s);
        }

        //BaseAdapter adapter = (BaseAdapter) getPreferenceScreen().getRootAdapter();
        //adapter.notifyDataSetChanged();
    }

    private void displayAboutDialog() {
        View messageView = getActivity().getLayoutInflater().inflate(R.layout.about, null, false);
        // we need this to enable html links
        TextView textView = (TextView) messageView.findViewById(R.id.about_license);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        // When linking text, force to always use default color. This works
        // around a pressed color state bug.
        int defaultColor = textView.getTextColors().getDefaultColor();
        textView.setTextColor(defaultColor);

        textView = (TextView) messageView.findViewById(R.id.about_sources);
        textView.setTextColor(defaultColor);

        textView = (TextView) messageView.findViewById(R.id.about_version_text);
        textView.setText(
                getString(R.string.about_version, getString(R.string.version_name)));

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.about_title)
                .setIcon(R.drawable.gplv3_icon)
                .setView(messageView)
                .show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference.getKey().equals(USBGpsProviderService.PREF_GPS_DEVICE)) {
            String deviceName = (String) newValue;

            log("Device clicked: " + newValue);

            if (!deviceName.isEmpty() && usbManager.getDeviceList().keySet().contains(deviceName)) {
                UsbDevice device = usbManager.getDeviceList().get(deviceName);

                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putInt(getString(R.string.pref_gps_device_product_id_key),
                        device.getProductId());

                editor.putInt(getString(R.string.pref_gps_device_vendor_id_key),
                        device.getVendorId());

                editor.apply();
            }

            updateDevicePreferenceSummary();
            return true;

        }

        return false;
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        log("Shared preferences changed: " + key);

        switch(key) {
            case USBGpsProviderService.PREF_START_GPS_PROVIDER: {
                boolean val = sharedPreferences.getBoolean(key, false);
                SwitchPreference pref = (SwitchPreference)
                        findPreference(USBGpsProviderService.PREF_START_GPS_PROVIDER);

                if (pref.isChecked() != val) {
                    pref.setChecked(val);
                    return;
                }
                break;
            }

            case USBGpsProviderService.PREF_TRACK_RECORDING: {
                boolean val = sharedPreferences.getBoolean(key, false);
                SwitchPreference pref = (SwitchPreference)
                        findPreference(USBGpsProviderService.PREF_TRACK_RECORDING);

                if (pref.isChecked() != val) {
                    pref.setChecked(val);
                    return;
                }
                break;
            }

            case USBGpsProviderService.PREF_GPS_DEVICE:
                updateDevicePreferenceSummary();
                break;

            case USBGpsProviderService.PREF_GPS_DEVICE_SPEED:
                updateDevicePreferenceSummary();
                break;

            case USBGpsProviderService.PREF_SET_TIME:
                if (sharedPreferences.getBoolean(key, false)) {
                    SuperuserManager suManager = SuperuserManager.getInstance();
                    if (!suManager.hasPermission()) {
                        ((CheckBoxPreference) findPreference(key)).setChecked(false);

                        suManager.request(new SuperuserManager.permissionListener() {
                            @Override
                            public void onGranted() {
                                new Handler(getActivity().getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        ((CheckBoxPreference) findPreference(key)).setChecked(true);
                                    }
                                });
                            }

                            @Override
                            public void onDenied() {
                                new Handler(getActivity().getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        new AlertDialog.Builder(getActivity())
                                                .setMessage(R.string.warning_set_time_needs_su)
                                                .setPositiveButton(android.R.string.ok, null)
                                                .show();
                                    }
                                });
                            }
                        });
                    }
                }
                break;
        }
    }

    @Override
    public void onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    public static class ProviderPreferences extends PreferenceFragmentCompat {
        SharedPreferences sharedPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            sharedPreferences = getPreferenceManager().getSharedPreferences();

            addPreferencesFromResource(R.xml.provider_prefs);
            updatePreferenceDetails();
        }

        private void updatePreferenceDetails() {

            String mockProvider = sharedPreferences.getString(USBGpsProviderService.PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));

            Preference pref = findPreference(USBGpsProviderService.PREF_MOCK_GPS_NAME);
            pref.setSummary(getString(R.string.pref_mock_gps_name_summary, mockProvider));

            pref = findPreference(USBGpsProviderService.PREF_CONNECTION_RETRIES);

            String maxConnRetries = sharedPreferences.getString(USBGpsProviderService.PREF_CONNECTION_RETRIES, getString(R.string.defaultConnectionRetries));
            pref.setSummary(getString(R.string.pref_connection_retries_summary, maxConnRetries));
        }
    }

    public static class SirfPreferences extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener {
        SharedPreferences sharedPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            sharedPreferences = getPreferenceManager().getSharedPreferences();
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            addPreferencesFromResource(R.xml.sirf_prefs);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (USBGpsProviderService.PREF_SIRF_ENABLE_GLL.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_GGA.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_RMC.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_VTG.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_GSA.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_GSV.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_ZDA.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_SBAS.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_NMEA.equals(key)
                    || USBGpsProviderService.PREF_SIRF_ENABLE_STATIC_NAVIGATION.equals(key)
                    ) {
                enableSirfFeature(key);
            }
        }

        private void enableSirfFeature(String key) {
            Intent configIntent = new Intent(getActivity(), USBGpsProviderService.class);
            configIntent.setAction(USBGpsProviderService.ACTION_CONFIGURE_SIRF_GPS);
            configIntent.putExtra(key, sharedPreferences.getBoolean(key, false));
            getActivity().startService(configIntent);
        }

        @Override
        public void onDestroy() {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }
    }

    public static class RecordingPreferences extends PreferenceFragmentCompat {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.recording_prefs);
        }
    }

    private void log(String message) {
        if (BuildConfig.DEBUG) Log.d(TAG, message);
    }
}
