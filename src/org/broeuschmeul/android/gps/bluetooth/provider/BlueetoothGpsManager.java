/*
 * Copyright (C) 2010, 2011 Herbert von Broeuschmeul
 * Copyright (C) 2010, 2011 BluetoothGPS4Droid Project
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.broeuschmeul.android.gps.nmea.util.NmeaParser;
import org.broeuschmeul.android.gps.sirf.util.SirfUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.location.LocationManager;
import android.location.GpsStatus.NmeaListener;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.os.SystemClock;
import android.util.Log;

public class BlueetoothGpsManager {

	private static final String LOG_TAG = "BlueGPS";
//	private static final String LOG_TAG = BlueetoothGpsManager.class.getSimpleName();

	private class ConnectedGps extends Thread {
		private final InputStream in;
		private final OutputStream out;
		private final PrintStream out2;
		private boolean ready = false;

		public ConnectedGps(BluetoothSocket socket) {
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
			PrintStream tmpOut2 = null;
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
				if (tmpOut != null){
					tmpOut2 = new PrintStream(tmpOut, false, "US-ASCII");
				}
			} catch (IOException e) {
				Log.e(LOG_TAG, "error while getting socket streams", e);
			}	
			in = tmpIn;
			out = tmpOut;
			out2 = tmpOut2;
		}
	
		public boolean isReady(){
			return ready;
		}
		
		public void run() {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(in,"US-ASCII"));
				String s;
				long now = SystemClock.uptimeMillis();
				long lastRead = now;
				while((enabled) && (now < lastRead+5000 )){
					if (reader.ready()){
						s = reader.readLine();
						Log.v(LOG_TAG, "data: "+System.currentTimeMillis()+" "+s);
						notifyNmeaSentence(s+"\r\n");
						ready = true;
						lastRead = SystemClock.uptimeMillis();
					} else {
						Log.d(LOG_TAG, "data: not ready "+System.currentTimeMillis());
						SystemClock.sleep(500);
					}
					now = SystemClock.uptimeMillis();
				}
			} catch (IOException e) {
				Log.e(LOG_TAG, "error while getting data", e);
				setMockLocationProviderOutOfService();
			} finally {
				ready = false;
				disableIfNeeded();
			}
		}

		/**
		 * Write to the connected OutStream.
		 * @param buffer  The bytes to write
		 */
		public void write(byte[] buffer) {
			try {
				do {
					Thread.sleep(100);
				} while (! ready);
				out.write(buffer);
				out.flush();
			} catch (IOException e) {
				Log.e(LOG_TAG, "Exception during write", e);
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, "Exception during write", e);
			}
		}
		/**
		 * Write to the connected OutStream.
		 * @param buffer  The data to write
		 */
		public void write(String buffer) {
			try {
				do {
					Thread.sleep(100);
				} while (! ready);
				out2.print(buffer);
				out2.flush();
				// } catch (IOException e) {
					//	Log.e("BT test", "Exception during write", e);
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, "Exception during write", e);
	}
		}
	}

	private Service callingService;
	private BluetoothSocket gpsSocket;
	private String gpsDeviceAddress;
	private NmeaParser parser = new NmeaParser(10f);
	private boolean enabled = false;
	private ExecutorService notificationPool;
	private ScheduledExecutorService connectionAndReadingPool;
	private List<NmeaListener> nmeaListeners = Collections.synchronizedList(new LinkedList<NmeaListener>()); 
	private LocationManager locationManager;
	private SharedPreferences sharedPreferences;
	private ConnectedGps connectedGps;
	private int disableReason = 0;
	private Notification connectionProblemNotification;
	private Notification serviceStoppedNotification;
	private Context appContext;
	private NotificationManager notificationManager;
	private int maxConnectionRetries;
	private int nbRetriesRemaining;
	private boolean connected = false;

	public BlueetoothGpsManager(Service callingService, String deviceAddress, int maxRetries) {
		this.gpsDeviceAddress = deviceAddress;
		this.callingService = callingService;
		this.maxConnectionRetries = maxRetries;
		this.nbRetriesRemaining = 1+maxRetries;
		this.appContext = callingService.getApplicationContext();
		locationManager = (LocationManager)callingService.getSystemService(Context.LOCATION_SERVICE);
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(callingService);
		notificationManager = (NotificationManager)callingService.getSystemService(Context.NOTIFICATION_SERVICE);
		parser.setLocationManager(locationManager);	
		
		connectionProblemNotification = new Notification();
		connectionProblemNotification.icon = R.drawable.ic_stat_notify;
		Intent stopIntent = new Intent(BluetoothGpsProviderService.ACTION_STOP_GPS_PROVIDER);
		// PendingIntent stopPendingIntent = PendingIntent.getService(appContext, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		PendingIntent stopPendingIntent = PendingIntent.getService(appContext, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		connectionProblemNotification.contentIntent = stopPendingIntent;

		serviceStoppedNotification = new Notification();
		serviceStoppedNotification.icon=R.drawable.ic_stat_notify;
		Intent restartIntent = new Intent(BluetoothGpsProviderService.ACTION_START_GPS_PROVIDER);
		PendingIntent restartPendingIntent = PendingIntent.getService(appContext, 0, restartIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		serviceStoppedNotification.setLatestEventInfo(appContext, 
				appContext.getString(R.string.service_closed_because_connection_problem_notification_title), 
				appContext.getString(R.string.service_closed_because_connection_problem_notification), 
				restartPendingIntent);
	}

	private void setDisableReason(int reasonId){
		disableReason = reasonId;
	}
	
	public int getDisableReason(){
		return disableReason;
	}
	
	/**
	 * @return true if the bluetooth GPS is enabled
	 */
	public synchronized boolean isEnabled() {
		return enabled;
	}

	public synchronized boolean enable() {
		notificationManager.cancel(R.string.service_closed_because_connection_problem_notification_title);
		if (! enabled){
        	Log.d(LOG_TAG, "enabling Bluetooth GPS manager");
			final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	        if (bluetoothAdapter == null) {
	            // Device does not support Bluetooth
	        	Log.e(LOG_TAG, "Device does not support Bluetooth");
	        	disable(R.string.msg_bluetooth_unsupported);
	        } else if (!bluetoothAdapter.isEnabled()) {
	        	// Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	        	// startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
	        	Log.e(LOG_TAG, "Bluetooth is not enabled");
	        	disable(R.string.msg_bluetooth_disabled);
	        } else if (Settings.Secure.getInt(callingService.getContentResolver(),Settings.Secure.ALLOW_MOCK_LOCATION, 0)==0){
	        	Log.e(LOG_TAG, "Mock location provider OFF");
	        	disable(R.string.msg_mock_location_disabled);
//	        } else if ( (! locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
//	        		 && (sharedPreferences.getBoolean(BluetoothGpsProviderService.PREF_REPLACE_STD_GPS, true))
//	        			) {
//	        	Log.e(LOG_TAG, "GPS location provider OFF");
//	        	disable(R.string.msg_gps_provider_disabled);
	        } else {
				final BluetoothDevice gpsDevice = bluetoothAdapter.getRemoteDevice(gpsDeviceAddress);
				if (gpsDevice == null){
					Log.e(LOG_TAG, "GPS device not found");       	    	
		        	disable(R.string.msg_bluetooth_gps_unavaible);
				} else {
	    			Log.e(LOG_TAG, "current device: "+gpsDevice.getName() + " -- " + gpsDevice.getAddress());
					try {
						gpsSocket = gpsDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
					} catch (IOException e) {
	    				Log.e(LOG_TAG, "Error during connection", e);
	    				gpsSocket = null;
					}
					if (gpsSocket == null){
	    				Log.e(LOG_TAG, "Error while establishing connection: no socket");
			        	disable(R.string.msg_bluetooth_gps_unavaible);
					} else {
						Runnable connectThread = new Runnable() {							
							@Override
							public void run() {
								try {
									connected = false;
									Log.v(LOG_TAG, "current device: "+gpsDevice.getName() + " -- " + gpsDevice.getAddress());
									if ((bluetoothAdapter.isEnabled()) && (nbRetriesRemaining > 0 )){										
										try {
											if (gpsSocket != null){
												Log.d(LOG_TAG, "trying to close old socket");
												gpsSocket.close();
											}
										} catch (IOException e) {
											Log.e(LOG_TAG, "Error during disconnection", e);
										}
										try {
											gpsSocket = gpsDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
										} catch (IOException e) {
											Log.e(LOG_TAG, "Error during connection", e);
						    				gpsSocket = null;
										}
										if (gpsSocket == null){
											Log.e(LOG_TAG, "Error while establishing connection: no socket");
								        	disable(R.string.msg_bluetooth_gps_unavaible);
										} else {
											// Cancel discovery because it will slow down the connection
											bluetoothAdapter.cancelDiscovery();
											// we increment the number of connection try
											// Connect the device through the socket. This will block
											// until it succeeds or throws an exception
											Log.v(LOG_TAG, "connecting to socket");
											gpsSocket.connect();
						        			Log.d(LOG_TAG, "connected to socket");
											connected = true;
											// reset eventual disabling cause
//											setDisableReason(0);
											// connection obtained so reset the number of connection try
											nbRetriesRemaining = 1+maxConnectionRetries ;
											notificationManager.cancel(R.string.connection_problem_notification_title);
						        			Log.v(LOG_TAG, "starting socket reading task");
											connectedGps = new ConnectedGps(gpsSocket);
											connectionAndReadingPool.execute(connectedGps);
								        	Log.v(LOG_TAG, "socket reading thread started");
										}
//									} else if (! bluetoothAdapter.isEnabled()) {
//										setDisableReason(R.string.msg_bluetooth_disabled);
									}
								} catch (IOException connectException) {
									// Unable to connect
									Log.e(LOG_TAG, "error while connecting to socket", connectException);									
									// disable(R.string.msg_bluetooth_gps_unavaible);
								} finally {
									nbRetriesRemaining--;
									if (! connected) {
										disableIfNeeded();
									}
								}
							}
						};
						this.enabled = true;
			        	Log.d(LOG_TAG, "Bluetooth GPS manager enabled");
			        	Log.v(LOG_TAG, "starting notification thread");
						notificationPool = Executors.newSingleThreadExecutor();
			        	Log.v(LOG_TAG, "starting connection and reading thread");
						connectionAndReadingPool = Executors.newSingleThreadScheduledExecutor();
			        	Log.v(LOG_TAG, "starting connection to socket task");
						connectionAndReadingPool.scheduleWithFixedDelay(connectThread, 5000, 60000, TimeUnit.MILLISECONDS);
					}
				}
			}
		}
		return this.enabled;
	}
	private synchronized void disableIfNeeded(){
		if (enabled){
			if (nbRetriesRemaining > 0){
				// Unable to connect
				Log.e(LOG_TAG, "Unable to establish connection");
				connectionProblemNotification.when = System.currentTimeMillis();
				String pbMessage = appContext.getResources().getQuantityString(R.plurals.connection_problem_notification, nbRetriesRemaining, nbRetriesRemaining);
				connectionProblemNotification.setLatestEventInfo(appContext, 
						appContext.getString(R.string.connection_problem_notification_title), 
						pbMessage, 
						connectionProblemNotification.contentIntent);
				connectionProblemNotification.number = 1 + maxConnectionRetries - nbRetriesRemaining;
				notificationManager.notify(R.string.connection_problem_notification_title, connectionProblemNotification);
			} else {
//				notificationManager.cancel(R.string.connection_problem_notification_title);
//				serviceStoppedNotification.when = System.currentTimeMillis();
//				notificationManager.notify(R.string.service_closed_because_connection_problem_notification_title, serviceStoppedNotification);
				disable(R.string.msg_two_many_connection_problems);
			}
		}
	}
	
	public synchronized void disable(int reasonId) {
    	Log.d(LOG_TAG, "disabling Bluetooth GPS manager reason: "+callingService.getString(reasonId));
		setDisableReason(reasonId);
    	disable();
	}
		
	public synchronized void disable() {
		notificationManager.cancel(R.string.connection_problem_notification_title);
		if (getDisableReason() != 0){
			serviceStoppedNotification.when = System.currentTimeMillis();
			serviceStoppedNotification.setLatestEventInfo(appContext, 
					appContext.getString(R.string.service_closed_because_connection_problem_notification_title), 
					appContext.getString(R.string.service_closed_because_connection_problem_notification, appContext.getString(getDisableReason())),
					serviceStoppedNotification.contentIntent);
			notificationManager.notify(R.string.service_closed_because_connection_problem_notification_title, serviceStoppedNotification);
		}
		if (enabled){
        	Log.d(LOG_TAG, "disabling Bluetooth GPS manager");
			enabled = false;
			if (gpsSocket != null){
				try {
					gpsSocket.close();
				} catch (IOException closeException) {
		    		Log.e(LOG_TAG, "error while closing socket", closeException);
				}
			}
			nmeaListeners.clear();
			disableMockLocationProvider();
			notificationPool.shutdown();
			connectionAndReadingPool.shutdown();
			callingService.stopSelf();
        	Log.d(LOG_TAG, "Bluetooth GPS manager disabled");
		}
	}

	public void enableMockLocationProvider(String gpsName){
		if (parser != null){
	       	Log.d(LOG_TAG, "enabling mock locations provider: "+gpsName);
			parser.enableMockLocationProvider(gpsName);
		}
	}

	public void disableMockLocationProvider(){
		if (parser != null){
	       	Log.d(LOG_TAG, "disabling mock locations provider");
			parser.disableMockLocationProvider();
		}
	}

	/**
	 * @return the mockGpsEnabled
	 */
	public boolean isMockGpsEnabled() {
		boolean mockGpsEnabled = false;
		if (parser != null){
			mockGpsEnabled = parser.isMockGpsEnabled();
		}
		return mockGpsEnabled;
	}
	/**
	 * @return the mockLocationProvider
	 */
	public String getMockLocationProvider() {
		String  mockLocationProvider = null;
		if (parser != null){
			mockLocationProvider = parser.getMockLocationProvider();
		}
		return mockLocationProvider;
	}

	private void setMockLocationProviderOutOfService(){
		if (parser != null){
			parser.setMockLocationProviderOutOfService();
		}
	}

	public boolean addNmeaListener(NmeaListener listener){
		if (!nmeaListeners.contains(listener)){
	       	Log.d(LOG_TAG, "adding new NMEA listener");
			nmeaListeners.add(listener);
		}
		return true;
	}

	public void removeNmeaListener(NmeaListener listener){
       	Log.d(LOG_TAG, "removing NMEA listener");
		nmeaListeners.remove(listener);
	}

	private void notifyNmeaSentence(final String nmeaSentence){
		if (enabled){
	       	Log.v(LOG_TAG, "parsing and notifying NMEA sentence: "+nmeaSentence);
			String sentence = null;
			try {
				sentence = parser.parseNmeaSentence(nmeaSentence);
			} catch (SecurityException e){
		       	Log.e(LOG_TAG, "error while parsing NMEA sentence: "+nmeaSentence, e);
				// a priori Mock Location is disabled
				sentence = null;
				disable(R.string.msg_mock_location_disabled);
			}
			final String recognizedSentence = sentence;
			final long timestamp = System.currentTimeMillis();
			if (recognizedSentence != null){
				Log.v(LOG_TAG, "notifying NMEA sentence: "+recognizedSentence);
				synchronized(nmeaListeners) {
					for(final NmeaListener listener : nmeaListeners){
						notificationPool.execute(new Runnable(){
							@Override
							public void run() {
								listener.onNmeaReceived(timestamp, recognizedSentence);
							}					 
						});
					}
				}
			}
		}
	}	

	public void sendPackagedNmeaCommand(final String command){
		Log.d(LOG_TAG, "sending NMEA sentence: "+command);
		if (isEnabled()){
			notificationPool.execute( new Runnable() {			
				@Override
				public void run() {
					while ((!enabled) || (!connected) || (connectedGps == null) || (!connectedGps.isReady())){
						Log.v(LOG_TAG, "writing thread is not ready");
						SystemClock.sleep(500);
					}
					if (isEnabled() && (connectedGps != null)){
						connectedGps.write(command);
						Log.d(LOG_TAG, "sent NMEA sentence: "+command);
					}
				}
			});
		}
	}

	public void sendPackagedSirfCommand(final String commandHexa){
		Log.d(LOG_TAG, "sending SIRF sentence: "+commandHexa);
		if (isEnabled()){
			final byte[] command = SirfUtils.genSirfCommand(commandHexa);
			notificationPool.execute( new Runnable() {			
				@Override
				public void run() {
					while ((!enabled) || (!connected) || (connectedGps == null) || (!connectedGps.isReady())){
						Log.v(LOG_TAG, "writing thread is not ready");
						SystemClock.sleep(500);
					}
					if (isEnabled() && (connectedGps != null)){
						connectedGps.write(command);
						Log.d(LOG_TAG, "sent SIRF sentence: "+commandHexa);
					}
				}
			});
		}
	}

	public void sendNmeaCommand(String sentence){
		String command = String.format((Locale)null,"$%s*%X\r\n", sentence, parser.computeChecksum(sentence));
		sendPackagedNmeaCommand(command);
	}

	public void sendSirfCommand(String payload){
		String command = SirfUtils.createSirfCommandFromPayload(payload);
		sendPackagedSirfCommand(command);
	}
}
