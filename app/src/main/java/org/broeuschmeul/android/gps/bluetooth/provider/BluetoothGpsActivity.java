/*
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

package org.broeuschmeul.android.gps.bluetooth.provider;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.broeuschmeul.android.gps.usb.provider.R;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * A PreferenceActivity Class used to configure, start and stop the NMEA tracker service.
 *
 * @author Herbert von Broeuschmeul
 */
public class BluetoothGpsActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnSharedPreferenceChangeListener {

    /**
     * Tag used for log messages
     */
    private static final String LOG_TAG = "UsbGPS";

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
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        Preference pref = findPreference(BluetoothGpsProviderService.PREF_ABOUT);
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                BluetoothGpsActivity.this.displayAboutDialog();
                return true;
            }
        });
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onResume()
	 */
    @Override
    protected void onResume() {
        this.updateDevicePreferenceList();
        super.onResume();
    }

    private void updateDevicePreferenceSummary() {
        // update bluetooth device summary
        String defaultDeviceName = "";
        ListPreference prefDevices = (ListPreference) findPreference(BluetoothGpsProviderService.PREF_GPS_DEVICE);
        if (!usbManager.getDeviceList().isEmpty()) {
            defaultDeviceName = usbManager.getDeviceList().keySet().iterator().next();
        }
        deviceName = sharedPref.getString(BluetoothGpsProviderService.PREF_GPS_DEVICE, defaultDeviceName);
        String deviceDisplayedName = "";
        if (!usbManager.getDeviceList().isEmpty() && usbManager.getDeviceList().get(deviceName) != null) {
            deviceDisplayedName = usbManager.getDeviceList().get(deviceName).getDeviceName();
        } else if ((usbManager.getDeviceList().size() == 1) && (usbManager.getDeviceList().get(defaultDeviceName) != null)) {
            deviceDisplayedName = usbManager.getDeviceList().get(defaultDeviceName).getDeviceName();
            deviceName = defaultDeviceName;
            prefDevices.setValue(defaultDeviceName);
        }
        prefDevices.setSummary(getString(R.string.pref_gps_device_summary, deviceDisplayedName));
        ListPreference prefDeviceSpeed = (ListPreference) findPreference(BluetoothGpsProviderService.PREF_GPS_DEVICE_SPEED);
        prefDeviceSpeed.setSummary(getString(R.string.pref_gps_device_speed_summary, sharedPref.getString(BluetoothGpsProviderService.PREF_GPS_DEVICE_SPEED, getString(R.string.defaultGpsDeviceSpeed))));
    }

    private void updateDevicePreferenceList() {
        // update bluetooth device summary
        updateDevicePreferenceSummary();
        // update bluetooth device list
        ListPreference prefDevices = (ListPreference) findPreference(BluetoothGpsProviderService.PREF_GPS_DEVICE);
        HashMap<String, UsbDevice> connectedUsbDevices = usbManager.getDeviceList();
        String[] entryValues = new String[connectedUsbDevices.size()];
        String[] entries = new String[connectedUsbDevices.size()];
        int i = 0;
        // Loop through usb devices
        for (String name : connectedUsbDevices.keySet()) {
            // Add the name and address to the ListPreference enties and entyValues
            UsbDevice device = connectedUsbDevices.get(name);
            Log.v(LOG_TAG, "device: " + name + " -- " + device.getDeviceName() + " -- " + device);
            Log.v(LOG_TAG, "device prot: " + device.getDeviceProtocol() + " class: " + device.getDeviceClass() + " sub class: " + device.getDeviceSubclass());
            Log.v(LOG_TAG, "device dev id: " + device.getDeviceId() + " prod id: " + device.getProductId() + " sub vend id: " + device.getVendorId());
            Log.v(LOG_TAG, "device int nb: " + device.getInterfaceCount());
            for (int k = 0; k < device.getInterfaceCount(); k++) {
                UsbInterface usbIntf = device.getInterface(k);
                Log.v(LOG_TAG, "intf id: : " + usbIntf.getId() + " -- " + usbIntf);
                Log.v(LOG_TAG, "intf prot: " + usbIntf.getInterfaceProtocol() + " class: " + usbIntf.getInterfaceClass() + " sub class: " + usbIntf.getInterfaceSubclass());
                Log.v(LOG_TAG, "intf int nb: " + usbIntf.getEndpointCount());
                for (int j = 0; j < usbIntf.getEndpointCount(); j++) {
                    UsbEndpoint endPt = usbIntf.getEndpoint(j);
                    Log.v(LOG_TAG, "endPt: : " + endPt + " type: " + endPt.getType() + " dir: " + endPt.getDirection());
                }
            }
            entryValues[i] = device.getDeviceName();
            entries[i] = name;
            i++;
        }
        prefDevices.setEntryValues(entryValues);
        prefDevices.setEntries(entries);
        Preference pref = (Preference) findPreference(BluetoothGpsProviderService.PREF_TRACK_RECORDING);
        pref.setEnabled(sharedPref.getBoolean(BluetoothGpsProviderService.PREF_START_GPS_PROVIDER, false));
        pref = (Preference) findPreference(BluetoothGpsProviderService.PREF_MOCK_GPS_NAME);
        String mockProvider = sharedPref.getString(BluetoothGpsProviderService.PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));
        pref.setSummary(getString(R.string.pref_mock_gps_name_summary, mockProvider));
        pref = (Preference) findPreference(BluetoothGpsProviderService.PREF_CONNECTION_RETRIES);
        String maxConnRetries = sharedPref.getString(BluetoothGpsProviderService.PREF_CONNECTION_RETRIES, getString(R.string.defaultConnectionRetries));
        pref.setSummary(getString(R.string.pref_connection_retries_summary, maxConnRetries));
        pref = (Preference) findPreference(BluetoothGpsProviderService.PREF_GPS_LOCATION_PROVIDER);
        if (sharedPref.getBoolean(BluetoothGpsProviderService.PREF_REPLACE_STD_GPS, true)) {
            String s = getString(R.string.pref_gps_location_provider_summary);
            pref.setSummary(s);
            Log.v(LOG_TAG, "loc. provider: " + s);
            Log.v(LOG_TAG, "loc. provider: " + pref.getSummary());
        } else {
            String s = getString(R.string.pref_mock_gps_name_summary, mockProvider);
            pref.setSummary(s);
            Log.v(LOG_TAG, "loc. provider: " + s);
            Log.v(LOG_TAG, "loc. provider: " + pref.getSummary());
        }
        this.onContentChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void displayAboutDialog() {
        View messageView = getLayoutInflater().inflate(R.layout.about, null, false);
        // we need this to enable html links
        TextView textView = (TextView) messageView.findViewById(R.id.about_license);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        // When linking text, force to always use default color. This works
        // around a pressed color state bug.
        int defaultColor = textView.getTextColors().getDefaultColor();
        textView.setTextColor(defaultColor);
        textView = (TextView) messageView.findViewById(R.id.about_sources);
        textView.setTextColor(defaultColor);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.about_title);
        builder.setIcon(R.drawable.gplv3_icon);
        builder.setView(messageView);
        builder.show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (BluetoothGpsProviderService.PREF_START_GPS_PROVIDER.equals(key)) {
            boolean val = sharedPreferences.getBoolean(key, false);
            CheckBoxPreference pref = (CheckBoxPreference) findPreference(key);
            if (pref.isChecked() != val) {
                pref.setChecked(val);
            } else if (val) {
                String device = sharedPreferences.getString(BluetoothGpsProviderService.PREF_GPS_DEVICE, "");
                if (!device.equals(deviceName) && deviceName != null && deviceName.length() > 0) {
                    sharedPreferences.edit().putString(BluetoothGpsProviderService.PREF_GPS_DEVICE, deviceName).commit();
                }
                Intent serviceIntent = new Intent(getBaseContext(), BluetoothGpsProviderService.class);
                serviceIntent.setAction(BluetoothGpsProviderService.ACTION_START_GPS_PROVIDER);
                startService(serviceIntent);
            } else {
                Intent serviceIntent = new Intent(getBaseContext(), BluetoothGpsProviderService.class);
                serviceIntent.setAction(BluetoothGpsProviderService.ACTION_STOP_GPS_PROVIDER);
                startService(serviceIntent);
            }
        } else if (BluetoothGpsProviderService.PREF_TRACK_RECORDING.equals(key)) {
            boolean val = sharedPreferences.getBoolean(key, false);
            CheckBoxPreference pref = (CheckBoxPreference) findPreference(key);
            if (pref.isChecked() != val) {
                pref.setChecked(val);
            } else if (val) {
                Intent serviceIntent = new Intent(getBaseContext(), BluetoothGpsProviderService.class);
                serviceIntent.setAction(BluetoothGpsProviderService.ACTION_START_TRACK_RECORDING);
                startService(serviceIntent);
            } else {
                Intent serviceIntent = new Intent(getBaseContext(), BluetoothGpsProviderService.class);
                serviceIntent.setAction(BluetoothGpsProviderService.ACTION_STOP_TRACK_RECORDING);
                startService(serviceIntent);
            }
        } else if (BluetoothGpsProviderService.PREF_GPS_DEVICE.equals(key)) {
            updateDevicePreferenceSummary();
        } else if (BluetoothGpsProviderService.PREF_GPS_DEVICE_SPEED.equals(key)) {
            updateDevicePreferenceSummary();
        } else if (BluetoothGpsProviderService.PREF_SIRF_ENABLE_GLL.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_GGA.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_RMC.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_VTG.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_GSA.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_GSV.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_ZDA.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_SBAS.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_NMEA.equals(key)
                || BluetoothGpsProviderService.PREF_SIRF_ENABLE_STATIC_NAVIGATION.equals(key)
                ) {
            enableSirfFeature(key);
        }
        this.updateDevicePreferenceList();
    }

    private void enableSirfFeature(String key) {
        CheckBoxPreference pref = (CheckBoxPreference) (findPreference(key));
        if (pref.isChecked() != sharedPref.getBoolean(key, false)) {
            pref.setChecked(sharedPref.getBoolean(key, false));
        } else {
            Intent configIntent = new Intent(getBaseContext(), BluetoothGpsProviderService.class);
            configIntent.setAction(BluetoothGpsProviderService.ACTION_CONFIGURE_SIRF_GPS);
            configIntent.putExtra(key, pref.isChecked());
            startService(configIntent);
        }
    }
}
