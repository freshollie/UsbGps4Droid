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
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.SwitchPreference;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

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
public class USBGpsSettingsFragment extends PreferenceFragment implements
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
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }

            Log.v(TAG, "USB Device Check thread ending");
        }
    };

    private Thread usbCheckThread;

    /**
     * Tag used for log messages
     */
    private static final String TAG = USBGpsSettingsFragment.class.getSimpleName();

    public static int DEFAULT_GPS_PRODUCT_ID = 8963;
    public static int DEFAULT_GPS_VENDOR_ID = 1659;

    private SharedPreferences sharedPref;

    private UsbManager usbManager = null;
    private String deviceName = "";
    private Handler mainHandler;

    private PreferenceScreenListener callback;

    public interface PreferenceScreenListener {
        void onNestedScreenClicked(PreferenceFragment preferenceFragment);
    }

    /**
     * Called when the fragment is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());

        usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);

        mainHandler = new Handler(getActivity().getMainLooper());

        addPreferencesFromResource(R.xml.main_prefs);

        findPreference(USBGpsProviderService.PREF_GPS_DEVICE).setOnPreferenceChangeListener(this);

        setupNestedPreferences();

        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    private void setupNestedPreferences() {
        findPreference(USBGpsProviderService.PREF_ABOUT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                USBGpsSettingsFragment.this.displayAboutDialog();
                return true;
            }
        });

        findPreference(getString(R.string.pref_gps_location_provider_key))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (callback != null) {
                            callback.onNestedScreenClicked(new ProviderPreferences());
                        }
                        return false;
                    }
                });

        findPreference(getString(R.string.pref_sirf_screen_key))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (callback != null) {
                            callback.onNestedScreenClicked(new SirfPreferences());
                        }
                        return false;
                    }
                });

        findPreference(getString(R.string.pref_recording_screen_key))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (callback != null) {
                            callback.onNestedScreenClicked(new RecordingPreferences());
                        }
                        return false;
                    }
                });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof PreferenceScreenListener) {
            callback = (PreferenceScreenListener) context;
        } else {
            throw new IllegalStateException("Owner must implement PreferenceScreenListener interface");
        }
    }

    @Override
    public void onAttach(Activity context) {
        super.onAttach(context);

        if (context instanceof PreferenceScreenListener) {
            callback = (PreferenceScreenListener) context;
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
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
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
        ListPreference prefDevices = (ListPreference) findPreference(USBGpsProviderService.PREF_GPS_DEVICE);

        prefDevices.setValue("current");

        prefDevices.setSummary(getString(R.string.pref_gps_device_summary, getSelectedDeviceSummary()));

        ListPreference prefDeviceSpeed = (ListPreference) findPreference(USBGpsProviderService.PREF_GPS_DEVICE_SPEED);
        prefDeviceSpeed.setSummary(getString(R.string.pref_gps_device_speed_summary, sharedPref.getString(USBGpsProviderService.PREF_GPS_DEVICE_SPEED, getString(R.string.defaultGpsDeviceSpeed))));
    }

    /**
     * Gets a summary of the current select product and vendor ids
     */
    private String getSelectedDeviceSummary() {
        int productId = sharedPref.getInt(
                USBGpsProviderService.PREF_GPS_DEVICE_PRODUCT_ID, DEFAULT_GPS_PRODUCT_ID);
        int vendorId = sharedPref.getInt(
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
        ListPreference prefDevices = (ListPreference) findPreference(USBGpsProviderService.PREF_GPS_DEVICE);

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

        prefDevices.setEntryValues(entryValues);
        prefDevices.setEntries(entries);
    }

    /**
     * Refreshes all of the preferences on the screen
     */
    private void updateDevicePreferenceList() {
        Preference pref;

        String mockProvider = sharedPref.getString(USBGpsProviderService.PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));

        // update usb device summary
        updateDevicePreferenceSummary();

        // update usb device list
        updateDevicesList();

        pref = findPreference(USBGpsProviderService.PREF_GPS_LOCATION_PROVIDER);
        if (sharedPref.getBoolean(USBGpsProviderService.PREF_REPLACE_STD_GPS, true)) {
            String s = getString(R.string.pref_gps_location_provider_summary);
            pref.setSummary(s);
            Log.v(TAG, "loc. provider: " + s);
        } else {
            String s = getString(R.string.pref_mock_gps_name_summary, mockProvider);
            pref.setSummary(s);
            Log.v(TAG, "loc. provider: " + s);
        }

        BaseAdapter adapter = (BaseAdapter) getPreferenceScreen().getRootAdapter();
        adapter.notifyDataSetChanged();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.about_title);
        builder.setIcon(R.drawable.gplv3_icon);
        builder.setView(messageView);
        builder.show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference.getKey().equals(USBGpsProviderService.PREF_GPS_DEVICE)) {
            String deviceName = (String) newValue;

            Log.v(TAG, "Device clicked: " + newValue);

            if (!deviceName.isEmpty() && usbManager.getDeviceList().keySet().contains(deviceName)) {
                UsbDevice device = usbManager.getDeviceList().get(deviceName);

                SharedPreferences.Editor editor = sharedPref.edit();

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
        Log.v(TAG, "Shared preferences changed: " + key);

        switch (key) {
            case USBGpsProviderService.PREF_START_GPS_PROVIDER: {
                boolean val = sharedPreferences.getBoolean(key, false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    SwitchPreference pref = (SwitchPreference)
                            findPreference(USBGpsProviderService.PREF_START_GPS_PROVIDER);

                    if (pref.isChecked() != val) {
                        pref.setChecked(val);
                        return;
                    }

                } else {
                    CheckBoxPreference pref = (CheckBoxPreference)
                            findPreference(USBGpsProviderService.PREF_START_GPS_PROVIDER);

                    if (pref.isChecked() != val) {
                        pref.setChecked(val);
                        return;
                    }
                }
                break;
            }

            case USBGpsProviderService.PREF_TRACK_RECORDING: {
                boolean val = sharedPreferences.getBoolean(key, false);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    SwitchPreference pref = (SwitchPreference)
                            findPreference(USBGpsProviderService.PREF_TRACK_RECORDING);

                    if (pref.isChecked() != val) {
                        pref.setChecked(val);
                        return;
                    }

                } else {
                    CheckBoxPreference pref = (CheckBoxPreference)
                            findPreference(USBGpsProviderService.PREF_TRACK_RECORDING);

                    if (pref.isChecked() != val) {
                        pref.setChecked(val);
                        return;
                    }
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
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    public static class ProviderPreferences extends PreferenceFragment {
        SharedPreferences sharedPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sharedPreferences = getPreferenceManager().getSharedPreferences();

            addPreferencesFromResource(R.xml.provider_prefs);
            updatePreferenceDetails();
        }

        public void updatePreferenceDetails() {

            String mockProvider = sharedPreferences.getString(USBGpsProviderService.PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));

            Preference pref = findPreference(USBGpsProviderService.PREF_MOCK_GPS_NAME);
            pref.setSummary(getString(R.string.pref_mock_gps_name_summary, mockProvider));

            pref = findPreference(USBGpsProviderService.PREF_CONNECTION_RETRIES);

            String maxConnRetries = sharedPreferences.getString(USBGpsProviderService.PREF_CONNECTION_RETRIES, getString(R.string.defaultConnectionRetries));
            pref.setSummary(getString(R.string.pref_connection_retries_summary, maxConnRetries));
        }
    }

    public static class SirfPreferences extends PreferenceFragment implements OnSharedPreferenceChangeListener {
        SharedPreferences sharedPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
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

    public static class RecordingPreferences extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.recording_prefs);
        }
    }
}
