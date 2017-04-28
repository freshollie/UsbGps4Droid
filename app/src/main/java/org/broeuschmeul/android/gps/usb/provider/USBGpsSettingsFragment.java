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

package org.broeuschmeul.android.gps.usb.provider;

import java.util.HashMap;

import android.Manifest;
import android.app.AlertDialog;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.support.v4.content.ContextCompat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * A PreferenceActivity Class used to configure, start and stop the NMEA tracker service.
 *
 * @author Herbert von Broeuschmeul
 */
public class USBGpsSettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener, OnSharedPreferenceChangeListener {

    private Runnable usbCheckRunnable = new Runnable() {
        @Override
        public void run() {
            int lastNum = usbManager.getDeviceList().values().size();

            while (!Thread.interrupted()) {
                int newNum = usbManager.getDeviceList().values().size();

                if (lastNum != newNum) {
                    updateDevicesList();
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

    private static final int LOCATION_REQUEST = 238472383;

    public static int DEFAULT_GPS_PRODUCT_ID = 8963;
    public static int DEFAULT_GPS_VENDOR_ID = 1659;

    private boolean tryingToStart = false;

    private SharedPreferences sharedPref;
    //	private BluetoothAdapter bluetoothAdapter = null;
    private UsbManager usbManager = null;
    private String deviceName = "";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);

        Preference pref = findPreference(USBGpsProviderService.PREF_ABOUT);
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                USBGpsSettingsFragment.this.displayAboutDialog();
                return true;
            }
        });

        findPreference(USBGpsProviderService.PREF_GPS_DEVICE).setOnPreferenceChangeListener(this);

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_REQUEST);
            }
        }
    }

    private boolean hasPermission(String perm) {
        return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(getActivity(), perm));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == LOCATION_REQUEST) {
            Log.v(TAG, "Test");
            if (hasPermission(permissions[0])) {
                if (tryingToStart) {
                    tryingToStart = false;

                    Intent serviceIntent = new Intent(getActivity().getBaseContext(), USBGpsProviderService.class);
                    serviceIntent.setAction(USBGpsProviderService.ACTION_START_GPS_PROVIDER);
                    getActivity().startService(serviceIntent);
                }
            } else {
                tryingToStart = false;
                sharedPref.edit().putBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false)
                        .apply();
                new AlertDialog.Builder(getActivity()).setMessage(
                        "Mock location needs to be enabled for this app to function")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onResume()
	 */
    @Override
    public void onResume() {
        usbCheckThread = new Thread(usbCheckRunnable);
        usbCheckThread.start();

        updateDevicePreferenceList();
        super.onResume();
    }

    @Override
    public void onPause() {
        usbCheckThread.interrupt();

        super.onPause();
    }

    private void updateDevicePreferenceSummary() {
        // update usb device summary

        ListPreference prefDevices = (ListPreference) findPreference(USBGpsProviderService.PREF_GPS_DEVICE);

        prefDevices.setValue("current");

        prefDevices.setSummary(getString(R.string.pref_gps_device_summary, getSelectedDeviceSummary()));

        ListPreference prefDeviceSpeed = (ListPreference) findPreference(USBGpsProviderService.PREF_GPS_DEVICE_SPEED);
        prefDeviceSpeed.setSummary(getString(R.string.pref_gps_device_speed_summary, sharedPref.getString(USBGpsProviderService.PREF_GPS_DEVICE_SPEED, getString(R.string.defaultGpsDeviceSpeed))));
    }

    /**
     * Gets a summary of the current select product and vendor ids
     * @return
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

    private void updateDevicePreferenceList() {
        // update usb device summary
        updateDevicePreferenceSummary();

        // update usb device list
        updateDevicesList();

        Preference pref = findPreference(USBGpsProviderService.PREF_TRACK_RECORDING);
        pref.setEnabled(sharedPref.getBoolean(USBGpsProviderService.PREF_START_GPS_PROVIDER, false));

        pref = findPreference(USBGpsProviderService.PREF_MOCK_GPS_NAME);
        String mockProvider = sharedPref.getString(USBGpsProviderService.PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));

        pref.setSummary(getString(R.string.pref_mock_gps_name_summary, mockProvider));
        pref = findPreference(USBGpsProviderService.PREF_CONNECTION_RETRIES);

        String maxConnRetries = sharedPref.getString(USBGpsProviderService.PREF_CONNECTION_RETRIES, getString(R.string.defaultConnectionRetries));
        pref.setSummary(getString(R.string.pref_connection_retries_summary, maxConnRetries));

        pref = findPreference(USBGpsProviderService.PREF_GPS_LOCATION_PROVIDER);
        if (sharedPref.getBoolean(USBGpsProviderService.PREF_REPLACE_STD_GPS, true)) {
            String s = getString(R.string.pref_gps_location_provider_summary);
            pref.setSummary(s);
            Log.v(TAG, "loc. provider: " + s);
            Log.v(TAG, "loc. provider: " + pref.getSummary());
        } else {
            String s = getString(R.string.pref_mock_gps_name_summary, mockProvider);
            pref.setSummary(s);
            Log.v(TAG, "loc. provider: " + s);
            Log.v(TAG, "loc. provider: " + pref.getSummary());
        }

        BaseAdapter adapter = (BaseAdapter) getPreferenceScreen().getRootAdapter();
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (usbCheckThread != null) {
            usbCheckThread.interrupt();
        }
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v(TAG, "Shared preferences changed: " + key);

        if (USBGpsProviderService.PREF_START_GPS_PROVIDER.equals(key)) {
            boolean val = sharedPreferences.getBoolean(key, false);
            CheckBoxPreference pref = (CheckBoxPreference) findPreference(key);

            if (pref.isChecked() != val) {
                pref.setChecked(val);

            } else if (val) {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Intent serviceIntent = new Intent(getActivity().getBaseContext(), USBGpsProviderService.class);
                    serviceIntent.setAction(USBGpsProviderService.ACTION_START_GPS_PROVIDER);
                    getActivity().startService(serviceIntent);
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        tryingToStart = true;
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                LOCATION_REQUEST);
                    }
                }

            } else {
                Intent serviceIntent = new Intent(getActivity().getBaseContext(), USBGpsProviderService.class);
                serviceIntent.setAction(USBGpsProviderService.ACTION_STOP_GPS_PROVIDER);
                getActivity().startService(serviceIntent);
            }

        } else if (USBGpsProviderService.PREF_TRACK_RECORDING.equals(key)) {
            boolean val = sharedPreferences.getBoolean(key, false);
            CheckBoxPreference pref = (CheckBoxPreference) findPreference(key);
            if (pref.isChecked() != val) {
                pref.setChecked(val);
            } else if (val) {
                Intent serviceIntent = new Intent(getActivity().getBaseContext(), USBGpsProviderService.class);
                serviceIntent.setAction(USBGpsProviderService.ACTION_START_TRACK_RECORDING);
                getActivity().startService(serviceIntent);
            } else {
                Intent serviceIntent = new Intent(getActivity().getBaseContext(), USBGpsProviderService.class);
                serviceIntent.setAction(USBGpsProviderService.ACTION_STOP_TRACK_RECORDING);
                getActivity().startService(serviceIntent);
            }
        } else if (USBGpsProviderService.PREF_GPS_DEVICE.equals(key)) {
            updateDevicePreferenceSummary();

        } else if (USBGpsProviderService.PREF_GPS_DEVICE_SPEED.equals(key)) {
            updateDevicePreferenceSummary();
        } else if (USBGpsProviderService.PREF_SIRF_ENABLE_GLL.equals(key)
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
        CheckBoxPreference pref = (CheckBoxPreference) (findPreference(key));
        if (pref.isChecked() != sharedPref.getBoolean(key, false)) {
            pref.setChecked(sharedPref.getBoolean(key, false));
        } else {

            Intent configIntent = new Intent(getActivity().getBaseContext(), USBGpsProviderService.class);
            configIntent.setAction(USBGpsProviderService.ACTION_CONFIGURE_SIRF_GPS);
            configIntent.putExtra(key, pref.isChecked());
            getActivity().startService(configIntent);

        }
    }
}
