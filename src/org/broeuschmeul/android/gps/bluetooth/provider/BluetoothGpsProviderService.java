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

/**
 * 
 */
package org.broeuschmeul.android.gps.bluetooth.provider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GpsStatus.NmeaListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Config;
import android.util.Log;
import android.widget.Toast;

/**
 * A Service used to replace Android internal GPS with a bluetooth GPS and/or write GPS NMEA data in a File.
 * 
 * @author Herbert von Broeuschmeul
 *
 */
public class BluetoothGpsProviderService extends Service implements NmeaListener, LocationListener {

	public static final String ACTION_START_TRACK_RECORDING = "org.broeuschmeul.android.gps.bluetooth.tracker.nmea.intent.action.START_TRACK_RECORDING";
	public static final String ACTION_STOP_TRACK_RECORDING = "org.broeuschmeul.android.gps.bluetooth.tracker.nmea.intent.action.STOP_TRACK_RECORDING";
	public static final String ACTION_START_GPS_PROVIDER = "org.broeuschmeul.android.gps.bluetooth.provider.nmea.intent.action.START_GPS_PROVIDER";
	public static final String ACTION_STOP_GPS_PROVIDER = "org.broeuschmeul.android.gps.bluetooth.provider.nmea.intent.action.STOP_GPS_PROVIDER";
	public static final String PREF_START_GPS_PROVIDER = "startGps";
	public static final String PREF_GPS_LOCATION_PROVIDER = "gpsLocationProviderKey";
	public static final String PREF_REPLACE_STD_GPS = "replaceStdtGps";
	public static final String PREF_MOCK_GPS_NAME = "mockGpsName";
	public static final String PREF_TRACK_RECORDING = "trackRecording";
	public static final String PREF_TRACK_MIN_DISTANCE = "trackMinDistance";
	public static final String PREF_TRACK_MIN_TIME = "trackMinTime";
	public static final String PREF_TRACK_FILE_DIR = "trackFileDirectory";
	public static final String PREF_TRACK_FILE_PREFIX = "trackFilePrefix";
	public static final String PREF_BLUETOOTH_DEVICE = "bluetoothDevice";

	private BlueetoothGpsManager gpsManager = null;
	private PrintWriter writer;
	private File trackFile;
	private boolean preludeWritten = false;
	private Toast toast ;

	@Override
	public void onCreate() {
		super.onCreate();
		toast = Toast.makeText(getApplicationContext(), "NMEA track recording... on", Toast.LENGTH_SHORT);		
	}

	@Override
	public void onStart(Intent intent, int startId) {
//		super.onStart(intent, startId);
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor edit = sharedPreferences.edit();
		String deviceAddress = sharedPreferences.getString(PREF_BLUETOOTH_DEVICE, null);
		if (Config.LOGD){
			Log.d(BluetoothGpsProviderService.class.getName(), "prefs device addr: "+deviceAddress);
		}
		if (ACTION_START_GPS_PROVIDER.equals(intent.getAction())){
			if (gpsManager == null){
				Notification notification = new Notification(R.drawable.icon, this.getString(R.string.foreground_gps_provider_started_notification),  System.currentTimeMillis());
				Intent myIntent = new Intent(this, BluetoothGpsActivity.class);
				PendingIntent myPendingIntent = PendingIntent.getActivity(this, 0, myIntent, PendingIntent.FLAG_CANCEL_CURRENT);
				notification.setLatestEventInfo(getApplicationContext(), this.getString(R.string.foreground_service_started_notification_title), this.getString(R.string.foreground_gps_provider_started_notification), myPendingIntent);
				if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)){
					String mockProvider = LocationManager.GPS_PROVIDER;
					if (! sharedPreferences.getBoolean(PREF_REPLACE_STD_GPS, true)){
						mockProvider = sharedPreferences.getString(PREF_MOCK_GPS_NAME, getString(R.string.defaultMockGpsName));
					}
					gpsManager = new BlueetoothGpsManager(this, deviceAddress);
					gpsManager.enableMockLocationProvider(mockProvider);
					boolean enabled = gpsManager.enable();
					if (sharedPreferences.getBoolean(PREF_START_GPS_PROVIDER, false) != enabled){
						edit.putBoolean(PREF_START_GPS_PROVIDER,enabled);
						edit.commit();
					}
					if (enabled) {
						startForeground(R.string.foreground_gps_provider_started_notification, notification);
						toast.setText(this.getString(R.string.msg_gps_provider_started));
						toast.show();				
					}
				} else {
					stopSelf();
				}
			} else {
				toast.setText(this.getString(R.string.msg_gps_provider_already_started));
				toast.show();
			}
		} else if (ACTION_START_TRACK_RECORDING.equals(intent.getAction())){
			if (trackFile == null){
				if (gpsManager != null){
					beginTrack();
					gpsManager.addNmeaListener(this);
					if (! sharedPreferences.getBoolean(PREF_TRACK_RECORDING, false)){
						edit.putBoolean(PREF_TRACK_RECORDING,true);
						edit.commit();
					}
					toast.setText(this.getString(R.string.msg_nmea_recording_started));
					toast.show();				
				} else {
					endTrack();
					if ( sharedPreferences.getBoolean(PREF_TRACK_RECORDING, true)){
						edit.putBoolean(PREF_TRACK_RECORDING,false);
						edit.commit();
					}
				}
			} else {
				toast.setText(this.getString(R.string.msg_nmea_recording_already_started));
				toast.show();
			}
		} else if (ACTION_STOP_TRACK_RECORDING.equals(intent.getAction())){
			if (gpsManager != null){
				gpsManager.removeNmeaListener(this);
				endTrack();
				toast.setText(this.getString(R.string.msg_nmea_recording_stopped));
				toast.show();
			}
			if (sharedPreferences.getBoolean(PREF_TRACK_RECORDING, true)){
				edit.putBoolean(PREF_TRACK_RECORDING,false);
				edit.commit();
			}
		} else if (ACTION_STOP_GPS_PROVIDER.equals(intent.getAction())){
			if (sharedPreferences.getBoolean(PREF_START_GPS_PROVIDER, true)){
				edit.putBoolean(PREF_START_GPS_PROVIDER,false);
				edit.commit();
			}
			stopSelf();
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		onStart(intent, startId);
		return Service.START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		BlueetoothGpsManager manager = gpsManager;
		gpsManager  = null;
		if (manager != null){
			manager.removeNmeaListener(this);
			manager.disableMockLocationProvider();
			manager.disable();
		}
		endTrack();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor edit = sharedPreferences.edit();
		if (sharedPreferences.getBoolean(PREF_TRACK_RECORDING, true)){
			edit.putBoolean(PREF_TRACK_RECORDING,false);
			edit.commit();
		}
		if (sharedPreferences.getBoolean(PREF_START_GPS_PROVIDER, true)){
			edit.putBoolean(PREF_START_GPS_PROVIDER,false);
			edit.commit();
		}
//		toast.setText(this.getString(R.string.msg_nmea_recording_stopped));
//		toast.show();
		toast.setText(this.getString(R.string.msg_gps_provider_stopped));
		toast.show();
		super.onDestroy();
	}

	private void beginTrack(){
		SimpleDateFormat fmt = new SimpleDateFormat("_yyyy-MM-dd_HH-mm-ss'.nmea'");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String trackDirName = sharedPreferences.getString(PREF_TRACK_FILE_DIR, this.getString(R.string.defaultTrackFileDirectory));
		String trackFilePrefix = sharedPreferences.getString(PREF_TRACK_FILE_PREFIX, this.getString(R.string.defaultTrackFilePrefix));
		trackFile = new File(trackDirName,trackFilePrefix+fmt.format(new Date()));
		Log.i(BluetoothGpsProviderService.class.getName(), "Writing the prelude of the NMEA file: "+trackFile.getAbsolutePath());
		File trackDir = trackFile.getParentFile();
		try {
			if ((! trackDir.mkdirs()) && (! trackDir.isDirectory())){
				Log.e(BluetoothGpsProviderService.class.getName(), "Error while creating parent dir of NMEA file: "+trackDir.getAbsolutePath());
			}
			writer = new PrintWriter(new BufferedWriter(new FileWriter(trackFile)));
			preludeWritten = true;
		} catch (IOException e) {
			Log.e(BluetoothGpsProviderService.class.getName(), "Error while writing the prelude of the NMEA file: "+trackFile.getAbsolutePath(), e);
			// there was an error while writing the prelude of the NMEA file, stopping the service...
			stopSelf();
		}
	}
	private void endTrack(){
		if (trackFile != null && writer != null){
			Log.i(BluetoothGpsProviderService.class.getName(), "Ending the NMEA file: "+trackFile.getAbsolutePath());
			preludeWritten = false;
			writer.close();
			trackFile = null;
		}
	}
	private void addNMEAString(String data){
		if (! preludeWritten){
			beginTrack();
		}
		Log.d(BluetoothGpsProviderService.class.getName(), "Adding data in the NMEA file: "+ data);
		if (trackFile != null && writer != null){
			writer.print(data);
		}
	}
	/* (non-Javadoc)
	 * @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind(Intent intent) {
		if (Config.LOGD){
			Log.d(BluetoothGpsProviderService.class.getName(), "trying access IBinder");
		}				
		return null;
	}

	@Override
	public void onLocationChanged(Location location) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onProviderDisabled(String provider) {
		Log.e(BluetoothGpsProviderService.class.getName(), "The GPS has been disabled.....stopping the NMEA tracker service.");
		stopSelf();
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub		
	}

	@Override
	public void onNmeaReceived(long timestamp, String data) {
		addNMEAString(data);
	}
}
