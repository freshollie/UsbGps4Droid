/*
 * Copyright (C) 2010 Herbert von Broeuschmeul
 * Copyright (C) 2010 BluetoothGPS4Droid Project
 * 
 * This file is part of BluetoothGPS4Droid.
 *
 * BluetoothGPS4Droid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * BluetoothGPS4Droid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with BluetoothGPS4Droid. If not, see <http://www.gnu.org/licenses/>.
 */

package org.broeuschmeul.android.gps.bluetooth.provider;

import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

/**
 * A PreferenceActivity Class used to configure, start and stop the NMEA tracker service.
 * 
 * @author Herbert von Broeuschmeul
 *
 */
public class BluetoothGpsActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnSharedPreferenceChangeListener {
	private SharedPreferences sharedPref ;
	private BluetoothAdapter bluetoothAdapter = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref);      
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
   }

    /* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		this.updateDevicePreferenceList();
		super.onResume();
	}

	private void updateDevicePreferenceSummary(){
        // update bluetooth device summary
		String deviceName = "";
        ListPreference prefDevices = (ListPreference)findPreference(BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE);
        String deviceAddress = sharedPref.getString(BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE, null);
        if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)){
        	deviceName = bluetoothAdapter.getRemoteDevice(deviceAddress).getName();
        }
        prefDevices.setSummary(getString(R.string.pref_bluetooth_device_summary, deviceName));
    }   

	private void updateDevicePreferenceList(){
        // update bluetooth device summary
		updateDevicePreferenceSummary();
		// update bluetooth device list
        ListPreference prefDevices = (ListPreference)findPreference(BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE);
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();        
        String[] entryValues = new String[pairedDevices.size()];
        String[] entries = new String[pairedDevices.size()];
        int i = 0;
    	    // Loop through paired devices
        for (BluetoothDevice device : pairedDevices) {
        	// Add the name and address to the ListPreference enties and entyValues
        	Log.e("BT test", "device: "+device.getName() + " -- " + device.getAddress());
        	entryValues[i] = device.getAddress();
            entries[i] = device.getName();
            i++;
        }
        prefDevices.setEntryValues(entryValues);
        prefDevices.setEntries(entries);
    }
    
	@Override
	protected void onDestroy() {
		super.onDestroy();
		sharedPref.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (BluetoothGpsProviderService.PREF_START_GPS_PROVIDER.equals(key)){
			boolean val = false;
			if (val = sharedPreferences.getBoolean(key, false)){
		        startService(new Intent(BluetoothGpsProviderService.ACTION_START_GPS_PROVIDER));
			} else {
				startService(new Intent(BluetoothGpsProviderService.ACTION_STOP_GPS_PROVIDER));
			}
			CheckBoxPreference pref = (CheckBoxPreference)findPreference(key);
			if (pref.isChecked() != val){
				pref.setChecked(val);
			}
		} else if (BluetoothGpsProviderService.PREF_TRACK_RECORDING.equals(key)){
			boolean val = false;
			if (val = sharedPreferences.getBoolean(key, false)){
		        startService(new Intent(BluetoothGpsProviderService.ACTION_START_TRACK_RECORDING));
			} else {
				startService(new Intent(BluetoothGpsProviderService.ACTION_STOP_TRACK_RECORDING));
			}
			CheckBoxPreference pref = (CheckBoxPreference)findPreference(key);
			if (pref.isChecked() != val){
				pref.setChecked(val);
			}
		} else if (BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE.equals(key)){
			updateDevicePreferenceSummary();
		}	
	}	
}